// 파일 경로: src/main/java/com/careflow/engineer/controller/EngineerDashboardController.java
package com.careflow.engineer.controller;

import com.careflow.auth.security.CustomUserDetails;
import com.careflow.engineer.dto.EngineerDashboardResponse;
import com.careflow.settlement.dto.EngineerSettlementSummaryResponse;
import com.careflow.engineer.service.EngineerDashboardService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/engineer")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ENGINEER')") // 권한 설정
public class EngineerDashboardController {

    private final EngineerDashboardService engineerDashboardService;

    // 1. 대시보드 요약 정보 (GET /api/engineer/dashboard)
    @GetMapping("/dashboard")
    public ResponseEntity<EngineerDashboardResponse> getDashboard(
            @AuthenticationPrincipal CustomUserDetails userDetails) {

        EngineerDashboardResponse response = engineerDashboardService.getDashboardData(userDetails.getUserId());
        return ResponseEntity.ok(response);
    }

    // 2. 실적 및 정산 내역 요약 (GET /api/engineer/settlements/summary)
    @GetMapping("/settlements/summary")
    public ResponseEntity<EngineerSettlementSummaryResponse> getSettlementSummary(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateTo) {

        EngineerSettlementSummaryResponse response = engineerDashboardService.getSettlementSummary(userDetails.getUserId(), dateFrom, dateTo);
        return ResponseEntity.ok(response);
    }
}