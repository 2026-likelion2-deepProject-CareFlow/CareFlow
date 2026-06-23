package com.careflow.account_requests.service;

import com.careflow.account_requests.dto.AccountRequestReject;
import com.careflow.account_requests.entity.AccountRequests;
import com.careflow.account_requests.repository.AccountRequestsRepository;
import com.careflow.auth.security.CustomUserDetails;
import com.careflow.common.enums.AccountRequestsStatus;
import com.careflow.common.enums.Role;
import com.careflow.user.entity.User;
import com.careflow.user.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.NoSuchElementException;

/**
 * 수리기사 계정 생성 요청 승인·거부 서비스.
 * 공통 User 생성 로직은 AccountRequestsService.createUserFromRequest 에 위임한다.
 */
@Service
@RequiredArgsConstructor
public class EngineerAccountRequestService {

    private final AccountRequestsRepository accountRequestsRepository;
    private final AccountRequestsService accountRequestsService;
    private final UserService userService;

    @Transactional
    public void approveEngineerAccount(CustomUserDetails userDetails, Long accountId) {
        AccountRequests accountRequests = accountRequestsRepository.findById(accountId)
                .orElseThrow(() -> new NoSuchElementException("요청 정보를 찾을 수 없습니다."));

        if (accountRequests.getStatus() != AccountRequestsStatus.PENDING) {
            throw new IllegalArgumentException("이미 승인되었거나 거부된 요청입니다.");
        }

        accountRequestsService.createUserFromRequest(userDetails, accountRequests, Role.ENGINEER);
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
}
