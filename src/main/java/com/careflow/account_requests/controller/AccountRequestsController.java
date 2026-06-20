package com.careflow.account_requests.controller;

import com.careflow.account_requests.entity.AccountRequests;
import com.careflow.account_requests.service.AccountRequestsService;
import com.careflow.agency.service.AgenciesService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/account-requests")
@RequiredArgsConstructor
public class AccountRequestsController {

    private final AgenciesService agenciesService;
    private final AccountRequestsService accountRequestsService;

    // 요청 목록 조회
//    @GetMapping("/list")
//    public ResponseEntity<List<AccountRequests>> requestlist(
////            @AuthenticationPrincipal CustomUserDetails userDetails
//            ) {
//
//        // CareFlow 관리자 계정으로 로그인 시
//        if(userDetails.getRole().equals("ADMIN")) {
//            // account_requests 테이블에서 agency_id 컬럼을 기준으로 대행사 테이블과 조인 후 approval_status 컬럼의 상태가 PENDING 인 경우만 account_requests 테이블에서 조회
//            List<AccountRequests> superAccountRequest = accountRequestsService.findRequestByPendingAgencies();
//            return ResponseEntity.ok(superAccountRequest);
//
//        } else {
//            // 대행사 슈퍼 계정인지 확인
//            Long agencyId = userDetails.getAgencyId();
//            Long representativeIdById = agenciesService.findRepresentativeIdById(agencyId);
//
//            // 슈퍼 계정인 경우
//            if(representativeIdById != null && representativeIdById == userDetails.getUserId() && userDetails.getRole().equals("AGENCY")) {
//                List<AccountRequests> mangerAccountRequest = accountRequestsService.findRequestByAgencyIdAndApproved(agencyId);
//                return ResponseEntity.ok(mangerAccountRequest);
//            } else {
//                // UnAuthorized 에러 반환(요청 권한없음)
//                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
//            }
//
//        }
//    }
//
//    // 요청 승인
//    @PatchMapping("/approve")
//    public ResponseEntity<Void> approveAccountRequest(@AuthenticationPrincipal CustomUserDetails userDetails, Long accountId) {
//        return null;
//    }
//
//    // 요청 거절
//    @PatchMapping("/reject")
//    public ResponseEntity<Void> rejectAccountRequest(@AuthenticationPrincipal CustomUserDetails userDetails, Long accountId) {
//        return null;
//    }
}
