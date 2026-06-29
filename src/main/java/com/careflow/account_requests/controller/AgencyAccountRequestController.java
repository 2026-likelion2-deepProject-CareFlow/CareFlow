package com.careflow.account_requests.controller;

import com.careflow.account_requests.dto.AccountRequestListResponse;
import com.careflow.account_requests.dto.AccountRequestReject;
import com.careflow.account_requests.service.AccountRequestsService;
import com.careflow.account_requests.service.AgencyAccountRequestService;
import com.careflow.agency.entity.Agencies;
import com.careflow.agency.service.AgenciesService;
import com.careflow.auth.security.CustomUserDetails;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;


@RestController
@RequestMapping("/api/account-requests")
@RequiredArgsConstructor
public class AgencyAccountRequestController {

    private final AgenciesService agenciesService;
    private final AccountRequestsService accountRequestsService;
    private final AgencyAccountRequestService agencyAccountRequestService;

    // 대행사 계정 생성 요청 목록 조회
    @GetMapping("/agencylist")
    public ResponseEntity<AccountRequestListResponse> requestlist(
            @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        // CareFlow 관리자 계정으로 로그인 시
        if (userDetails.getRole().equals("ADMIN")) {
            // account_requests 테이블에서 agency_id 컬럼을 기준으로 대행사 테이블과 조인 후 approval_status 컬럼의 상태가 PENDING 인 경우만 account_requests 테이블에서 조회
            AccountRequestListResponse response = AccountRequestListResponse.of(
                    accountRequestsService.findRequestByPendingAgencies());
            return ResponseEntity.ok(response);
        } else {
            Long userId = userDetails.getUserId();
            Agencies agencies = agenciesService.findRepresentativeIdById(userId);

            // 슈퍼 계정인 경우
            if (agencies != null) {
                AccountRequestListResponse response = AccountRequestListResponse.of(
                        accountRequestsService.findRequestByAgencyIdAndApproved(agencies.getId()));
                return ResponseEntity.ok(response);
            } else {
                // 일반 관리자 계정인 경우 UnAuthorized 에러 반환(요청 권한없음)
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
            }
        }
    }

    // 대행사 슈퍼 계정, 또는 일반 관리자 계정 요청 승인
    // 세부 권한 검증(슈퍼 계정 요청은 ADMIN만, 일반 관리자 요청은 해당 대행사 슈퍼 계정만)은 서비스 레이어에서 처리
    @PostMapping("/agency/approval")
    public ResponseEntity<Void> approveAccountRequest(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @RequestParam Long accountId) throws IllegalAccessException {

        if (userDetails.getRole().equals("ADMIN") || userDetails.getRole().equals("AGENCY")) {
            agencyAccountRequestService.approveAgencyAccount(userDetails, accountId);
        } else {
            throw new IllegalAccessException("기능에 대한 접근 권한이 없습니다.");
        }

        return ResponseEntity.noContent().build();
    }

    // 대행사 슈퍼 계정, 또는 일반 관리자 요청 거절
    @PostMapping("/agency/rejection")
    public ResponseEntity<Void> rejectAccountRequest(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @RequestParam Long accountId,
            @Valid @RequestBody AccountRequestReject accountRequestReject) throws IllegalAccessException {

        if (userDetails.getRole().equals("ADMIN") || userDetails.getRole().equals("AGENCY")) {
            agencyAccountRequestService.rejectAgencyAccount(userDetails, accountId, accountRequestReject);
        } else {
            throw new IllegalAccessException("기능에 대한 접근 권한이 없습니다.");
        }
        return ResponseEntity.noContent().build();
    }
}
