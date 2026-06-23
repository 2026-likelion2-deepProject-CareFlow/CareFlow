package com.careflow.account_requests.service;

import com.careflow.account_requests.dto.AccountRequestReject;
import com.careflow.account_requests.entity.AccountRequests;
import com.careflow.account_requests.repository.AccountRequestsRepository;
import com.careflow.agency.entity.Agencies;
import com.careflow.auth.security.CustomUserDetails;
import com.careflow.common.enums.AccountRequestsStatus;
import com.careflow.common.enums.AgencyStatus;
import com.careflow.common.enums.Role;
import com.careflow.user.entity.User;
import com.careflow.user.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.NoSuchElementException;

@Service
@RequiredArgsConstructor
public class AccountRequestsService {

    private final AccountRequestsRepository accountRequestsRepository;
    private final UserService userService;

    @Transactional(readOnly = true)
    public List<AccountRequests> findRequestByPendingAgencies() {
        return accountRequestsRepository.findRequestByPendingAgencies();
    }

    @Transactional(readOnly = true)
    public List<AccountRequests> findRequestByAgencyIdAndApproved(Long agencyId) {
        return accountRequestsRepository.findRequestByAgencyIdAndApproved(agencyId);
    }

    @Transactional(readOnly = true)
    public AccountRequests findRequestById(Long accountId) {
        return accountRequestsRepository.findById(accountId).orElse(null);
    }

    @Transactional(readOnly = true)
    public List<AccountRequests> findRequestByRoleAndStatus(Long agencyId) {
        return accountRequestsRepository.findRequestByRequestsRoleAndStatus_Pending(agencyId);
    }

    @Transactional
    public void approveAgencyAccount(CustomUserDetails userDetails, Long accountId) throws IllegalAccessException {
        AccountRequests accountRequests = accountRequestsRepository.findById(accountId)
                .orElseThrow(() -> new NoSuchElementException("요청 정보를 찾을 수 없습니다."));

        if (accountRequests.getStatus() != AccountRequestsStatus.PENDING) {
            throw new IllegalArgumentException("이미 승인되었거나 거부된 요청입니다.");
        }

        Agencies targetAgency = accountRequests.getAgency();

        // 슈퍼 계정 생성 요청 — ADMIN만 승인 가능
        if (targetAgency.getApprovalStatus() == AgencyStatus.PENDING) {
            if (!userDetails.getRole().equals("ADMIN")) {
                throw new IllegalAccessException("슈퍼 계정 요청은 CareFlow 관리자만 승인할 수 있습니다.");
            }
        }
        // 일반 관리자 계정 생성 요청 — 해당 대행사의 슈퍼 계정만 승인 가능
        else if (targetAgency.getApprovalStatus() == AgencyStatus.APPROVED) {
            if (!userDetails.getRole().equals("AGENCY")) {
                throw new IllegalAccessException("일반 관리자 계정 요청은 대행사 슈퍼 계정만 승인할 수 있습니다.");
            }
            // 다른 대행사의 슈퍼 계정이 승인하지 못하도록 대행사 소속 검증
            if (!targetAgency.getRepresentativeId().getId().equals(userDetails.getUserId())) {
                throw new IllegalAccessException("자신이 소속된 대행사의 요청만 승인할 수 있습니다.");
            }
        }

        approveAndCreateUser(userDetails, accountRequests, Role.AGENCY);
    }

    @Transactional
    public void rejectAgencyAccount(CustomUserDetails userDetails, Long accountId, AccountRequestReject accountRequestReject) throws IllegalAccessException {
        AccountRequests accountRequests = accountRequestsRepository.findById(accountId)
                .orElseThrow(() -> new NoSuchElementException("요청 정보를 찾을 수 없습니다."));

        if (accountRequests.getStatus() == AccountRequestsStatus.APPROVED) {
            throw new IllegalAccessException("이미 등록 승인된 대행사 입니다.");
        } else if (accountRequests.getStatus() == AccountRequestsStatus.REJECTED) {
            throw new IllegalAccessException("이미 등록 거부된 대행사 입니다.");
        }

        User reviewer = userService.findById(userDetails.getUserId());

        // 요청 거부 처리 — 도메인 메서드로 더티 체킹
        accountRequests.reject(reviewer, accountRequestReject.rejectReson());

        // 슈퍼 계정 요청 거부의 경우 대행사 상태도 REJECTED 로 전환
        if (accountRequests.getAgency().getApprovalStatus() == AgencyStatus.PENDING) {
            accountRequests.getAgency().reject();
        }
    }

    @Transactional
    public void approveEngineerAccount(CustomUserDetails userDetails, Long accountId) {
        AccountRequests accountRequests = accountRequestsRepository.findById(accountId)
                .orElseThrow(() -> new NoSuchElementException("요청 정보를 찾을 수 없습니다."));

        if (accountRequests.getStatus() != AccountRequestsStatus.PENDING) {
            throw new IllegalArgumentException("이미 승인되었거나 거부된 요청입니다.");
        }

        approveAndCreateUser(userDetails, accountRequests, Role.ENGINEER);
    }

    @Transactional
    public void rejectEngineerAccount(CustomUserDetails userDetails, Long accountId, AccountRequestReject accountRequestReject) throws IllegalAccessException {
        AccountRequests accountRequests = accountRequestsRepository.findById(accountId)
                .orElseThrow(() -> new NoSuchElementException("요청 정보를 찾을 수 없습니다."));

        if (accountRequests.getStatus() == AccountRequestsStatus.APPROVED) {
            throw new IllegalAccessException("이미 등록 승인된 수리기사 계정입니다.");
        } else if (accountRequests.getStatus() == AccountRequestsStatus.REJECTED) {
            throw new IllegalAccessException("이미 등록 거부된 수리기사 계정입니다.");
        }

        User reviewer = userService.findById(userDetails.getUserId());
        // 거부 처리 — 도메인 메서드로 더티 체킹
        accountRequests.reject(reviewer, accountRequestReject.rejectReson());
    }

    private void approveAndCreateUser(CustomUserDetails userDetails, AccountRequests accountRequests, Role role) {

        User user = User.builder().agency(accountRequests.getAgency())
                .name(accountRequests.getName())
                .email(accountRequests.getEmail())
                .passwordHash(accountRequests.getPassword()) // 요청 저장 시점에 이미 해싱됨
                .phone(accountRequests.getPhone())
                .role(role)
                .addressDetail(accountRequests.getAddressDetail())
                .regionId(accountRequests.getRegion())
                .build();

        // 회원 정보 저장
        Long userId = userService.saveUser(user);
        User approvedBy = userService.findById(userDetails.getUserId());
        User createdUser = userService.findById(userId);

        // 요청 승인 처리 — 도메인 메서드로 더티 체킹
        accountRequests.approve(approvedBy, createdUser);

        /*
            대행사 정보가 승인 대기중인 상태(PENDING) 이면 슈퍼 계정 생성 요청
            대행사 정보가 승인 되어있는 상태라면(APPROVED) 일반 관리자 계정 요청(대행사 정보 갱신 생략)
            수리기사는 PENDING 상태인 대행사에 대해 계정 생성 요청 불가
         */
        Agencies agencies = accountRequests.getAgency();
        if (agencies.getApprovalStatus() == AgencyStatus.PENDING) {
            // 대행사 상태 업데이트 및 슈퍼 계정 연결 — 도메인 메서드로 더티 체킹
            agencies.approve(approvedBy, createdUser);
        }
    }
}
