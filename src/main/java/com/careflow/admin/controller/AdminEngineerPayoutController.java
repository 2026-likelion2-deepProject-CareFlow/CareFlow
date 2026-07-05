package com.careflow.admin.controller;

import com.careflow.admin.dto.request.AdminEngineerPayoutStatusUpdateRequest;
import com.careflow.admin.dto.response.AdminEngineerPayoutListResponse;
import com.careflow.admin.service.AdminEngineerPayoutService;
import com.careflow.auth.security.CustomUserDetails;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/**
 * 관리자용 대행사→기사 지급 배치 조회 컨트롤러 (분쟁 조정용)
 * 모든 엔드포인트는 ROLE_ADMIN 로그인 상태 전제 (SecurityConfig에서 /api/admin/** hasRole(ADMIN) 필터링)
 */
@RestController
@RequestMapping("/api/admin/engineer-payouts")
@RequiredArgsConstructor
public class AdminEngineerPayoutController {

    private final AdminEngineerPayoutService adminEngineerPayoutService;

    /**
     * [GET] /api/admin/engineer-payouts?year=&month=&status=
     * 해당 연월의 대행사→기사 지급 배치 전체를 조회한다. status로 필터링 가능(특히 DISPUTED 조정용).
     */
    @GetMapping
    public ResponseEntity<AdminEngineerPayoutListResponse> getEngineerPayouts(
            @RequestParam int year,
            @RequestParam int month,
            @RequestParam(required = false) String status,
            @AuthenticationPrincipal CustomUserDetails userDetails) throws IllegalAccessException {

        return ResponseEntity.ok(adminEngineerPayoutService.getEngineerPayouts(userDetails, year, month, status));
    }

    /**
     * [PATCH] /api/admin/engineer-payouts/{engineerPayoutId}/status
     * 건별 지급 상태 변경 (기사 이의제기 조정용 — PENDING↔DISPUTED 간 전이만 허용)
     */
    @PatchMapping("/{engineerPayoutId}/status")
    public ResponseEntity<Void> updateStatus(
            @PathVariable Long engineerPayoutId,
            @Valid @RequestBody AdminEngineerPayoutStatusUpdateRequest request,
            @AuthenticationPrincipal CustomUserDetails userDetails) throws IllegalAccessException {

        adminEngineerPayoutService.updateStatus(userDetails, engineerPayoutId, request.status());
        return ResponseEntity.noContent().build();
    }
}
