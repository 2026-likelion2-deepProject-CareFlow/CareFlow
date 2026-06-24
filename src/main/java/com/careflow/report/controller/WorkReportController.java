package com.careflow.report.controller;

import com.careflow.auth.security.CustomUserDetails;
import com.careflow.engineer.dto.CreateWorkReportRequest;
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
}