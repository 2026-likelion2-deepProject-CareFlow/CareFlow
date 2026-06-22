package com.careflow.account_requests.controller;

import com.careflow.account_requests.dto.AccountRequestReject;
import com.careflow.account_requests.entity.AccountRequests;
import com.careflow.account_requests.service.AccountRequestsService;
import com.careflow.agency.entity.Agencies;
import com.careflow.agency.service.AgenciesService;
import com.careflow.auth.security.CustomUserDetails;
import com.careflow.user.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/account-requests")
@RequiredArgsConstructor
public class AccountRequestsController {

    private final AgenciesService agenciesService;
    private final UserService userService;
    private final AccountRequestsService accountRequestsService;

    // 대행사 계정 생성 요청 목록 조회
    @GetMapping("/agencylist")
    public ResponseEntity<List<AccountRequests>> requestlist(
            @AuthenticationPrincipal CustomUserDetails userDetails
            ) {

        // CareFlow 관리자 계정으로 로그인 시
        if(userDetails.getRole().equals("ADMIN")) {
            // account_requests 테이블에서 agency_id 컬럼을 기준으로 대행사 테이블과 조인 후 approval_status 컬럼의 상태가 PENDING 인 경우만 account_requests 테이블에서 조회
            List<AccountRequests> superAccountRequest = accountRequestsService.findRequestByPendingAgencies();
            return ResponseEntity.ok(superAccountRequest);

        } else {

            Long userId = userDetails.getUserId();
            Agencies agencies = agenciesService.findRepresentativeIdById(userId);

            // 슈퍼 계정인 경우
            if(agencies != null) {
                List<AccountRequests> mangerAccountRequest = accountRequestsService.findRequestByAgencyIdAndApproved(agencies.getId());
                return ResponseEntity.ok(mangerAccountRequest);
            } else {
                // 일반 관리자 계정인 경우 UnAuthorized 에러 반환(요청 권한없음)
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
            }

        }
    }

    //
    @GetMapping("/engineerlist")
    public ResponseEntity<List<AccountRequests>> engineerlist(
            @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        // 슈퍼 계정으로 로그인 했을 경우
        if(userDetails.getRole().equals("AGENCY")){
            Agencies agencies = agenciesService.findRepresentativeIdById(userDetails.getUserId());
            List<AccountRequests> engineerAccountRequest = accountRequestsService.findRequestByRoleAndStatus(agencies.getId());
            return ResponseEntity.ok(engineerAccountRequest);
        } else {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

    }

    // 대행사 슈퍼 계정, 또는 일반 관리자 계정 요청 승인
    @PostMapping("/agency/approval")
    public ResponseEntity<Void> approveAccountRequest(@AuthenticationPrincipal CustomUserDetails userDetails, @RequestParam Long accountId) throws IllegalAccessException {

        // CareFlow 관리자, 또는 대행사 슈퍼 계정의 요청인 경우
        if (userDetails.getRole().equals("ADMIN") || userDetails.getRole().equals("AGENCY")){
            // 슈퍼 계정 생성 및 대행사 등록 허가
            accountRequestsService.approveAgencyAccount(userDetails, accountId);
        }
         else {
            throw new IllegalAccessException("기능에 대한 접근 권한이 없습니다.");
        }

        return ResponseEntity.noContent().build();
    }

    // 대행사 슈퍼 계정, 또는 일반 관리자 요청 거절
    @PostMapping("/agency/rejection")
    public ResponseEntity<Void> rejectAccountRequest(@AuthenticationPrincipal CustomUserDetails userDetails, @RequestParam Long accountId,
                                                     @RequestBody AccountRequestReject accountRequestReject)
            throws IllegalAccessException {

        if (userDetails.getRole().equals("ADMIN") || userDetails.getRole().equals("AGENCY")){
            accountRequestsService.rejectAgencyAccount(userDetails, accountId, accountRequestReject);
        } else {
            throw new IllegalAccessException("기능에 대한 접근 권한이 없습니다.");
        }
        return ResponseEntity.noContent().build();
    }

    // 수리기사 계정 생성요청 승인
    @PostMapping("/engineer/approval")
    public ResponseEntity<Void> approveEngineerAccount(@AuthenticationPrincipal CustomUserDetails userDetails, @RequestParam Long accountId) throws IllegalAccessException {

        // 슈퍼 계정만 접근
        if (userDetails.getRole().equals("AGENCY")){
            // 수리기사 계정 생성 및 프로필 생성
            accountRequestsService.approveEngineerAccount(userDetails, accountId);
        }
        else {
            throw new IllegalAccessException("기능에 대한 접근 권한이 없습니다.");
        }
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/engineer/rejection")
    public ResponseEntity<Void> rejectEngineerAccount(@AuthenticationPrincipal CustomUserDetails userDetails, @RequestParam Long accountId,
                                                     @RequestBody AccountRequestReject accountRequestReject)
            throws IllegalAccessException {
        if (userDetails.getRole().equals("AGENCY")){
            // 슈퍼 계정만 접근
            accountRequestsService.rejectEngineerAccount(userDetails, accountId, accountRequestReject);
        } else {
            throw new IllegalAccessException("기능에 대한 접근 권한이 없습니다.");
        }
        return ResponseEntity.noContent().build();
    }
}
