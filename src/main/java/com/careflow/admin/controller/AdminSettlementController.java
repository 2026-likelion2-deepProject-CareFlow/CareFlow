package com.careflow.admin.controller;

import com.careflow.admin.dto.response.AdminSettlementDetailResponse;
import com.careflow.admin.dto.response.AdminSettlementSummaryResponse;
import com.careflow.admin.service.AdminSettlementService;
import com.careflow.auth.security.CustomUserDetails;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 관리자용 대행사 정산 관리 API 컨트롤러
 * 모든 엔드포인트는 ROLE_ADMIN 로그인 상태 전제 (SecurityConfig에서 /api/admin/** hasRole(ADMIN) 필터링)
 */
@RestController
@RequestMapping("/api/admin/settlements")
@RequiredArgsConstructor
public class AdminSettlementController {

    private final AdminSettlementService adminSettlementService;

    /**
     * [GET] /api/admin/settlements?year=&month=
     * 전체 대행사 대상 월별 정산 현황(요약 + 대행사별 목록)을 조회한다.
     */
    @GetMapping
    public ResponseEntity<AdminSettlementSummaryResponse> getMonthlySummary(
            @RequestParam int year,
            @RequestParam int month,
            @AuthenticationPrincipal CustomUserDetails userDetails) throws IllegalAccessException {

        return ResponseEntity.ok(adminSettlementService.getMonthlySummary(userDetails, year, month));
    }

    /**
     * [GET] /api/admin/settlements/{agencyId}/details?year=&month=
     * 특정 대행사의 해당 월 건별 정산 내역을 조회한다.
     */
    @GetMapping("/{agencyId}/details")
    public ResponseEntity<List<AdminSettlementDetailResponse>> getAgencyDetails(
            @PathVariable Long agencyId,
            @RequestParam int year,
            @RequestParam int month,
            @AuthenticationPrincipal CustomUserDetails userDetails) throws IllegalAccessException {

        return ResponseEntity.ok(adminSettlementService.getAgencyDetails(userDetails, agencyId, year, month));
    }

    /**
     * [PATCH] /api/admin/settlements/{agencyId}/approve?year=&month=
     * 특정 대행사의 해당 월 미지급 정산 전체를 지급 완료 처리한다.
     */
    @PatchMapping("/{agencyId}/approve")
    public ResponseEntity<Void> approveAgency(
            @PathVariable Long agencyId,
            @RequestParam int year,
            @RequestParam int month,
            @AuthenticationPrincipal CustomUserDetails userDetails) throws IllegalAccessException {

        adminSettlementService.approveAgency(userDetails, agencyId, year, month);
        return ResponseEntity.ok().build();
    }

    /**
     * [PATCH] /api/admin/settlements/approve-all?year=&month=
     * 해당 월 전체 대행사의 미지급 정산을 일괄 지급 완료 처리한다.
     */
    @PatchMapping("/approve-all")
    public ResponseEntity<Void> approveAll(
            @RequestParam int year,
            @RequestParam int month,
            @AuthenticationPrincipal CustomUserDetails userDetails) throws IllegalAccessException {

        adminSettlementService.approveAll(userDetails, year, month);
        return ResponseEntity.ok().build();
    }
}
