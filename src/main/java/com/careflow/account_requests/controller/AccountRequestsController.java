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
    @GetMapping("/list")
    public ResponseEntity<List<AccountRequests>> requestlist(
//            @AuthenticationPrincipal CustomUserDetails userDetails
            ) {

        // CareFlow 관리자 계정으로 로그인 시
        if(userDetails.getRole().equals("ADMIN")) {
            // account_requests 테이블에서 agency_id 컬럼을 기준으로 대행사 테이블과 조인 후 approval_status 컬럼의 상태가 PENDING 인 경우만 account_requests 테이블에서 조회
            List<AccountRequests> superAccountRequest = accountRequestsService.findRequestByPendingAgencies();
            return ResponseEntity.ok(superAccountRequest);

        } else {
            // 대행사 슈퍼 계정인지 확인
            Long agencyId = userDetails.getAgencyId();
            Long representativeIdById = agenciesService.findRepresentativeIdById(agencyId);

            // 슈퍼 계정인 경우
            if(representativeIdById != null && representativeIdById == userDetails.getUserId() && userDetails.getRole().equals("AGENCY")) {
                List<AccountRequests> mangerAccountRequest = accountRequestsService.findRequestByAgencyIdAndApproved(agencyId);
                return ResponseEntity.ok(mangerAccountRequest);
            } else {
                // UnAuthorized 에러 반환(요청 권한없음)
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
            }

        }
    }

    // 요청 승인
    @PatchMapping("/approve")
    public ResponseEntity<Void> approveAccountRequest(@AuthenticationPrincipal CustomUserDetails userDetails, Long accountId) {
        // 요청 승인 시 플로우
        /*
            // -> CareFlow 관리자 승인 시 해당 요청의 agency_id 값을 기준으로 대행사 정보 검색
            // -> 대행사 정보 검색 결과 미승인 상태일 시 현재 요청이 슈퍼 계정 생성 요청인것으로 판단
            // -> 회원 정보 users 테이블에 저장 및 생성된 user_id 값 가져와서 created_user_id 컬럼에 적재
            // -> 이후 해당 값을 다시 대행사 테이블에서 agency_id 값을 통해 찾은 데이터에 대해 representative_user_id 컬럼값 갱신 및 대행사 데이터 승인 상태 변경(PENDING -> APPROVED)
         */

        AccountRequests accountRequests =
        return null;
    }

    // 요청 거절
    @PatchMapping("/reject")
    public ResponseEntity<Void> rejectAccountRequest(@AuthenticationPrincipal CustomUserDetails userDetails, Long accountId) {
        return null;
    }
}
