package com.careflow.report.controller;

import com.careflow.auth.security.CustomUserDetails;
import com.careflow.engineer.dto.CreateWorkReportRequest;
import com.careflow.report.dto.WorkReportDetailResponse;
import com.careflow.report.service.WorkReportService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/engineers/me/reports")
@RequiredArgsConstructor
public class WorkReportController {

    private final WorkReportService workReportService;

    @PostMapping
    public ResponseEntity<String> submitReport(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @RequestBody @Valid CreateWorkReportRequest request) {

        Long reportId = workReportService.submitWorkReport(userDetails.getUserId(), request);

        return ResponseEntity.status(HttpStatus.CREATED)
                .body("작업 완료 보고서가 제출되고, 제품 건강 진단서가 갱신되었습니다. (Report ID: " + reportId + ")");
    }

    /**
     * 작업 완료 보고서 상세 조회 API (고객/기사 공용)
     */
    @GetMapping("/{reportId}")
    public ResponseEntity<WorkReportDetailResponse> getReportDetail(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable Long reportId) throws IllegalAccessException {

        WorkReportDetailResponse response = workReportService.getWorkReportDetail(
                userDetails.getUserId(),
                userDetails.getRole(),
                reportId
        );
        return ResponseEntity.ok(response);
    }

    /**
     * 작업 완료 보고서 고객 승인 API
     */
    @PatchMapping("/{reportId}/approve")
    public ResponseEntity<String> approveReport(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable Long reportId) throws IllegalAccessException {

        // 이 엔드포인트는 CUSTOMER만 호출하도록 권한 1차 방어
        if (!"CUSTOMER".equals(userDetails.getRole())) {
            throw new IllegalAccessException("고객만 보고서를 승인할 수 있습니다.");
        }

        workReportService.approveWorkReport(userDetails.getUserId(), reportId);

        return ResponseEntity.ok("작업 보고서가 성공적으로 승인되었습니다. 결제 단계로 이동합니다.");
    }
}