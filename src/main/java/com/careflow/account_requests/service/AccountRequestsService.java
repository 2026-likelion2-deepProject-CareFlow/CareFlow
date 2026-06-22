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

import java.time.LocalDateTime;
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

    @Transactional
    public void approveAgencyAccount(CustomUserDetails userDetails, Long accountId) {
        AccountRequests accountRequests = accountRequestsRepository.findById(accountId).orElse(null);
        if (accountRequests == null) {
            throw new NoSuchElementException("요청 정보를 찾을 수 없습니다.");
        } else {
            // 요청 상태 확인
            if (accountRequests.getStatus() == AccountRequestsStatus.PENDING){
                // 대행사 슈퍼 계정 회원정보 저장 (트랜잭션 커밋 정상 작동확인)
                approveAndCreateUser(userDetails, accountRequests, accountId);
            } else{
                throw new IllegalArgumentException("이미 승인되었거나 거부된 요청입니다.");
            }
        }
    }

    @Transactional
    public void rejectAgencyAccount(CustomUserDetails userDetails, Long accountId, AccountRequestReject accountRequestReject) throws IllegalAccessException {
        AccountRequests accountRequests = accountRequestsRepository.findById(accountId).orElse(null);
        if (accountRequests == null) {
            throw new NoSuchElementException("요청 정보를 찾을 수 없습니다.");
        } else {
            // 요청 상태 확인
            if (accountRequests.getStatus() == AccountRequestsStatus.PENDING){
                // 정상 거부처리 (더티 체킹)
                accountRequests.builder()
                        .status(AccountRequestsStatus.REJECTED)
                        .reviewedBy(userService.findById(userDetails.getUserId()))
                        .reviewedAt(LocalDateTime.now())
                        .updatedAt(LocalDateTime.now())
                        .rejectReason(accountRequestReject.rejectReson())
                        .build();

                accountRequests.getAgencyId().builder()
                        .approvalStatus(AgencyStatus.REJECTED)
                        .updatedAt(LocalDateTime.now())
                        .build();
            } else if (accountRequests.getStatus() == AccountRequestsStatus.APPROVED){
                throw new IllegalAccessException("이미 등록 승인된 대행사 입니다.");
            } else {
                throw new IllegalAccessException("이미 등록 거부된 대행사 입니다.");
            }
        }
    }

    private void approveAndCreateUser(CustomUserDetails userDetails, AccountRequests accountRequests, Long accountId){

        User user = User.builder().agencyId(accountRequests.getAgencyId().getId())
                .name(accountRequests.getName())
                .email(accountRequests.getEmail())
                .passwordHash(accountRequests.getPassword())
                .phone(accountRequests.getPhone())
                .role(Role.AGENCY)
                .addressDetail(accountRequests.getAddressDetail())
                .build();

        // 회원 정보 저장
        Long userId = userService.saveUser(user);
        User approvedBy = userService.findById(userDetails.getUserId());
        User createdUser = userService.findById(userId);

        // 요청 정보 업데이트 (더티 체킹)
        accountRequests.builder()
                .status(AccountRequestsStatus.APPROVED)
                .reviewedBy(approvedBy)
                .updatedAt(LocalDateTime.now())
                .reviewedAt(LocalDateTime.now())
                .createdUserId(createdUser)
                .build();

        /*
            대행사 정보가 승인 대기중인 상태(PENDING) 이면 슈퍼 계정 생성 요청
            대행사 정보가 승인 되어있는 상태라면(APPROVED) 일반 관리자 계정 요청(대행사 정보 갱신과정 생략)
         */
        Agencies agencies = accountRequests.getAgencyId();
        if (agencies.getApprovalStatus().equals(AgencyStatus.PENDING)){
            // 대행사 정보 업데이트 (더티 체킹)
            agencies.builder()
                    .approvalStatus(AgencyStatus.APPROVED)
                    .approvedBy(approvedBy)
                    .approvedAt(LocalDateTime.now())
                    .updatedAt(LocalDateTime.now())
                    .representativeId(createdUser)
                    .build();
        }
    }
}
