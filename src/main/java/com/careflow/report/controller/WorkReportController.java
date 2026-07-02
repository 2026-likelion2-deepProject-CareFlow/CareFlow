package com.careflow.report.controller;

import com.careflow.auth.security.CustomUserDetails;
import com.careflow.engineer.dto.CreateWorkReportRequest;
import com.careflow.report.dto.EngineerReportListResponse;
import com.careflow.report.dto.WorkReportDetailResponse;
import com.careflow.report.service.WorkReportService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/engineer/work-reports")
@RequiredArgsConstructor
public class WorkReportController {

    private final WorkReportService workReportService;

    @PostMapping
    @PreAuthorize("hasRole('ENGINEER')")
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
    @PreAuthorize("hasAnyRole('ENGINEER', 'CUSTOMER')")
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
    @PreAuthorize("hasRole('CUSTOMER')")
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

    /**
     * [기사용 API] 기사 본인의 작업 보고서 목록 조회 (페이징)
     * GET /api/engineer/work-reports?page=1&size=20
     */
    @GetMapping
    @PreAuthorize("hasRole('ENGINEER')")
    public ResponseEntity<Page<EngineerReportListResponse>> getWorkReports(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @RequestParam(defaultValue = "1") int page, // 프론트엔드는 1페이지부터 시작
            @RequestParam(defaultValue = "20") int size) {

        // Spring Data JPA의 Pageable은 0 인덱스부터 시작하므로 (page - 1) 처리
        PageRequest pageRequest = PageRequest.of(Math.max(0, page - 1), size);
        Page<EngineerReportListResponse> response = workReportService.getEngineerWorkReports(userDetails.getUserId(), pageRequest);

        return ResponseEntity.ok(response);
    }

    // 작업 완료 보고서 승인 요청 취소
    @DeleteMapping("/{reportId}/approval-request")
    @PreAuthorize("hasRole('ENGINEER')")
    public ResponseEntity<String> cancelApprovalRequest(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable Long reportId) {

        workReportService.cancelApprovalRequest(userDetails.getUserId(), reportId);
        return ResponseEntity.ok("보고서 승인 요청이 취소되었습니다.");
    }
}