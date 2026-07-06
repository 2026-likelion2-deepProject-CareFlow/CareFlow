package com.careflow.account_requests.controller;

import com.careflow.account_requests.dto.AccountRequestListResponse;
import com.careflow.account_requests.dto.AccountRequestReject;
import com.careflow.account_requests.entity.AccountRequests;
import com.careflow.account_requests.service.AccountRequestsService;
import com.careflow.account_requests.service.EngineerAccountRequestService;
import com.careflow.agency.entity.Agencies;
import com.careflow.agency.service.AgenciesService;
import com.careflow.auth.security.CustomUserDetails;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/account-requests")
@RequiredArgsConstructor
public class EngineerAccountRequestController {

    private final AgenciesService agenciesService;
    private final AccountRequestsService accountRequestsService;
    private final EngineerAccountRequestService engineerAccountRequestService;

    // 수리기사 계정 요청 목록 조회
    // ※ 엔티티를 그대로 반환하면 Agencies.representativeId ↔ User.agency 양방향 연관관계로 인해
    //   Jackson 직렬화 시 무한 재귀가 발생하므로, agencylist 와 동일하게 DTO 로 변환해서 반환한다.
    @GetMapping("/engineerlist")
    public ResponseEntity<AccountRequestListResponse> engineerlist(
            @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        // 슈퍼 계정으로 로그인 했을 경우
        if (userDetails.getRole().equals("AGENCY")) {
            Agencies agencies = agenciesService.findRepresentativeIdById(userDetails.getUserId());
            List<AccountRequests> engineerAccountRequest = accountRequestsService.findRequestByRoleAndStatus(agencies.getId());
            return ResponseEntity.ok(AccountRequestListResponse.of(engineerAccountRequest));
        } else {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
    }

    // 수리기사 계정 생성 요청 승인 — 슈퍼 계정만 접근 가능
    @PostMapping("/engineer/approval")
    public ResponseEntity<Void> approveEngineerAccount(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @RequestParam Long accountId) throws IllegalAccessException {

        if (userDetails.getRole().equals("AGENCY")) {
            engineerAccountRequestService.approveEngineerAccount(userDetails, accountId);
        } else {
            throw new IllegalAccessException("기능에 대한 접근 권한이 없습니다.");
        }
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/engineer/rejection")
    public ResponseEntity<Void> rejectEngineerAccount(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @RequestParam Long accountId,
            @Valid @RequestBody AccountRequestReject accountRequestReject) throws IllegalAccessException {

        if (userDetails.getRole().equals("AGENCY")) {
            engineerAccountRequestService.rejectEngineerAccount(userDetails, accountId, accountRequestReject);
        } else {
            throw new IllegalAccessException("기능에 대한 접근 권한이 없습니다.");
        }
        return ResponseEntity.noContent().build();
    }
}
