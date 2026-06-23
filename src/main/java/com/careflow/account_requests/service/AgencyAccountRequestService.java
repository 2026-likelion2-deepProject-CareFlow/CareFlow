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

import java.util.NoSuchElementException;

/**
 * 대행사 계정 생성 요청(슈퍼 계정 / 일반 관리자 계정) 승인·거부 서비스.
 * 공통 User 생성 로직은 AccountRequestsService.createUserFromRequest 에 위임한다.
 */
@Service
@RequiredArgsConstructor
public class AgencyAccountRequestService {

    private final AccountRequestsRepository accountRequestsRepository;
    private final AccountRequestsService accountRequestsService;
    private final UserService userService;

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

        accountRequestsService.createUserFromRequest(userDetails, accountRequests, Role.AGENCY);
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
}
