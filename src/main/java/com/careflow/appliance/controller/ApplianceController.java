package com.careflow.appliance.controller;

import com.careflow.appliance.dto.ApplianceCreateRequest;
import com.careflow.appliance.dto.ApplianceResponse;
import com.careflow.appliance.dto.HealthCertificateResponse;
import com.careflow.appliance.service.ApplianceService;
import com.careflow.auth.security.CustomUserDetails;
import com.careflow.report.dto.RepairHistoryResponse;
import com.careflow.report.service.WorkReportService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/appliances")
@RequiredArgsConstructor
public class ApplianceController {

    private final ApplianceService applianceService;
    private final WorkReportService workReportService;

    /**
     * 가전제품 등록 API
     * customerId는 경로에서 받지만 실제 인증은 JWT(@AuthenticationPrincipal)로 처리
     */
    @PostMapping
    public ResponseEntity<ApplianceResponse> registerAppliance(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @Valid @RequestBody ApplianceCreateRequest request) {

        ApplianceResponse response = applianceService.registerAppliance(userDetails.getUserId(), request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * 고객 가전제품 목록 조회 API (논리 삭제 제외)
     */
    @GetMapping("/me")
    public ResponseEntity<List<ApplianceResponse>> getMyAppliances(
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        return ResponseEntity.ok(applianceService.getMyAppliances(userDetails.getUserId()));
    }

    /**
     * 가전제품 상세 조회 API
     */
    @GetMapping("/{applianceId}")
    public ResponseEntity<ApplianceResponse> getApplianceDetail(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable Long applianceId) throws IllegalAccessException {

        return ResponseEntity.ok(applianceService.getApplianceDetail(userDetails.getUserId(), applianceId));
    }

    /**
     * 가전제품 논리 삭제 API
     */
    @DeleteMapping("/{applianceId}")
    public ResponseEntity<Void> deleteAppliance(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable Long applianceId) throws IllegalAccessException {

        applianceService.deleteAppliance(userDetails.getUserId(), applianceId);
        return ResponseEntity.noContent().build();
    }

    /**
     * 가전제품 이전 수리 이력 타임라인 조회 API (E-10)
     * 고객의 가전 상세 화면 및 수리기사의 작업 상세 화면에서 공통 사용
     */
    @GetMapping("/{applianceId}/repair-history")
    public ResponseEntity<List<RepairHistoryResponse>> getRepairHistory(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable Long applianceId) throws IllegalAccessException {

        List<RepairHistoryResponse> history = workReportService.getApplianceRepairHistory(
                userDetails.getUserId(),
                userDetails.getRole(),
                applianceId
        );

        return ResponseEntity.ok(history);
    }

    /**
     * 가전제품 건강 진단서 상세 조회 API (C-24)
     */
    @GetMapping("/{applianceId}/health-certificate")
    public ResponseEntity<HealthCertificateResponse> getHealthCertificate(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable Long applianceId) throws IllegalAccessException {

        HealthCertificateResponse response = applianceService.getHealthCertificate(
                userDetails.getUserId(),
                userDetails.getRole(),
                applianceId
        );

        return ResponseEntity.ok(response);
    }
}
