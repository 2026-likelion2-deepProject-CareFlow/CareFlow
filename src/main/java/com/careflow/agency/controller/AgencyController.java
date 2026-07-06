package com.careflow.agency.controller;

import com.careflow.agency.dto.request.AgencyProfileUpdateRequest;
import com.careflow.agency.dto.request.AgencyWithdrawRequest;
import com.careflow.agency.dto.request.SecurityToggleRequest;
import com.careflow.agency.dto.response.AgencyDataImportResponse;
import com.careflow.agency.dto.response.AgencyProfileResponse;
import com.careflow.agency.dto.response.AgencySecuritySettingsResponse;
import com.careflow.agency.dto.response.TrustedDeviceResponse;
import com.careflow.agency.service.AgenciesService;
import com.careflow.agency.service.AgencyDataTransferService;
import com.careflow.as_request.dto.AgencyAsRequestDetailResponse;
import com.careflow.as_request.dto.AgencyAsRequestListResponse;
import com.careflow.as_request.dto.AgencyDashboardSummaryResponse;
import com.careflow.as_request.service.AgencyAsRequestService;
import com.careflow.as_status_log.dto.AsStatusLogSummaryResponse;
import com.careflow.as_status_log.service.AgencyAsStatusLogService;
import com.careflow.auth.security.CustomUserDetails;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * 대행사 전용 통합 컨트롤러 — /api/agency
 *
 * 프론트엔드 URI 규약(단수형 /api/agency/*)에 맞춰 대행사 설정 및 작업 관련 API를 제공한다.
 * 모든 엔드포인트는 AGENCY 역할만 접근 가능하다.
 */
@RestController
@RequestMapping("/api/agency")
@RequiredArgsConstructor
@PreAuthorize("hasRole('AGENCY')")
public class AgencyController {

    private final AgenciesService agenciesService;
    private final AgencyAsRequestService agencyAsRequestService;
    private final AgencyAsStatusLogService agencyAsStatusLogService;
    private final AgencyDataTransferService agencyDataTransferService;

    // ─────────────────────────────────────────────
    //  대행사 설정 API
    // ─────────────────────────────────────────────

    // 대행사 내 정보 조회 (설정 페이지)
    // agencyId 기준 조회 — 대표뿐 아니라 소속 staff 계정도 조회 가능 (수정 API는 대표 전용 정책 유지)
    @GetMapping("/me")
    public ResponseEntity<AgencyProfileResponse> getAgencyProfile(
            @AuthenticationPrincipal CustomUserDetails userDetails) {

        AgencyProfileResponse response = agenciesService.getProfile(userDetails.getAgencyId(), userDetails.getUserId());
        return ResponseEntity.ok(response);
    }

    // 대행사 정보 수정 (상호명, 주소)
    @PutMapping("/me")
    public ResponseEntity<AgencyProfileResponse> updateAgencyProfile(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @Valid @RequestBody AgencyProfileUpdateRequest request) {

        AgencyProfileResponse response = agenciesService.updateProfile(userDetails.getUserId(), request);
        return ResponseEntity.ok(response);
    }

    /**
     * 대행사 관리자(본인) 계정 탈퇴
     * - 대표 담당자(슈퍼 계정)는 탈퇴 불가(403), 비밀번호 불일치 시 400
     */
    @DeleteMapping("/me/withdraw")
    public ResponseEntity<Void> withdrawAccount(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @Valid @RequestBody AgencyWithdrawRequest request) {

        agenciesService.withdrawAccount(userDetails.getUserId(), request);
        return ResponseEntity.noContent().build();
    }

    // 로그인 보안 설정 조회 (2단계 인증/로그인 알림 상태 + 신뢰 기기 개수)
    @GetMapping("/me/security")
    public ResponseEntity<AgencySecuritySettingsResponse> getSecuritySettings(
            @AuthenticationPrincipal CustomUserDetails userDetails) {

        return ResponseEntity.ok(agenciesService.getSecuritySettings(userDetails.getUserId()));
    }

    // 2단계 인증 토글
    @PatchMapping("/me/security/two-factor")
    public ResponseEntity<Void> toggleTwoFactor(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @Valid @RequestBody SecurityToggleRequest request) {

        agenciesService.toggleTwoFactor(userDetails.getUserId(), request.enabled());
        return ResponseEntity.noContent().build();
    }

    // 로그인 알림 토글
    @PatchMapping("/me/security/login-alert")
    public ResponseEntity<Void> toggleLoginAlert(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @Valid @RequestBody SecurityToggleRequest request) {

        agenciesService.toggleLoginAlert(userDetails.getUserId(), request.enabled());
        return ResponseEntity.noContent().build();
    }

    // 신뢰 기기 목록 조회
    @GetMapping("/me/trusted-devices")
    public ResponseEntity<List<TrustedDeviceResponse>> getTrustedDevices(
            @AuthenticationPrincipal CustomUserDetails userDetails) {

        return ResponseEntity.ok(agenciesService.getTrustedDevices(userDetails.getUserId()));
    }

    // 신뢰 기기 삭제(신뢰 해제)
    @DeleteMapping("/me/trusted-devices/{deviceId}")
    public ResponseEntity<Void> deleteTrustedDevice(
            @PathVariable Long deviceId,
            @AuthenticationPrincipal CustomUserDetails userDetails) throws IllegalAccessException {

        agenciesService.deleteTrustedDevice(userDetails.getUserId(), deviceId);
        return ResponseEntity.noContent().build();
    }

    // 데이터 내보내기 — 소속 기사 로스터 CSV 다운로드
    @GetMapping("/me/data-export")
    public ResponseEntity<byte[]> exportData(
            @AuthenticationPrincipal CustomUserDetails userDetails) {

        byte[] csv = agencyDataTransferService.exportEngineerRoster(userDetails.getAgencyId());
        String filename = "engineers_" + userDetails.getAgencyId() + "_"
                + LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE) + ".csv";

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .contentType(MediaType.parseMediaType("text/csv; charset=UTF-8"))
                .body(csv);
    }

    // 데이터 가져오기 — CSV로 기사 가입 신청 일괄 등록(PENDING, 대표 담당자 전용)
    @PostMapping(value = "/me/data-import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<AgencyDataImportResponse> importData(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @RequestParam("file") MultipartFile file) throws IllegalAccessException {

        AgencyDataImportResponse response = agencyDataTransferService.importEngineerRoster(userDetails, file);
        return ResponseEntity.ok(response);
    }

    // ─────────────────────────────────────────────
    //  대행사 대시보드 통계 API
    // ─────────────────────────────────────────────

    /**
     * 대행사 대시보드 요약 통계
     * - 전체 누적 접수 건수 / 오늘 신규 접수 / 배정 대기 / 배정 승인 / 취소 건수
     */
    @GetMapping("/stats/summary")
    public ResponseEntity<AgencyDashboardSummaryResponse> getDashboardSummary(
            @AuthenticationPrincipal CustomUserDetails userDetails) throws IllegalAccessException {

        AgencyDashboardSummaryResponse response = agencyAsRequestService.getDashboardSummary(userDetails);
        return ResponseEntity.ok(response);
    }

    // ─────────────────────────────────────────────
    //  대행사 작업 요청 API
    // ─────────────────────────────────────────────

    /**
     * 대행사 소속 A/S 요청 목록 조회
     * - status, date 파라미터 미입력 시 COMPLETED 제외 전체 목록 반환
     * - 파라미터 입력 시 필터링 조회 수행 (프론트 /api/agency/work-requests?status= 방식 대응)
     */
    @GetMapping("/work-requests")
    public ResponseEntity<List<AgencyAsRequestListResponse>> getWorkRequests(
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(required = false) String status,
            @AuthenticationPrincipal CustomUserDetails userDetails) throws IllegalAccessException {

        List<AgencyAsRequestListResponse> result;

        if (date == null && status == null) {
            // 파라미터 없음 → 전체 목록 (COMPLETED 제외)
            result = agencyAsRequestService.getAsRequestsByAgency(userDetails);
        } else {
            // 파라미터 있음 → 필터링 조회
            result = agencyAsRequestService.searchAsRequests(userDetails, date, status);
        }

        if (result.isEmpty()) {
            return ResponseEntity.noContent().build();
        }
        return ResponseEntity.ok(result);
    }

    /**
     * 대행사 A/S 요청 단건 상세 조회
     * - 프론트 URI: GET /api/agency/work-requests/{requestId}/detail
     * - 본인 소속 대행사의 요청만 조회 가능, 타 대행사 요청 접근 시 401
     */
    @GetMapping("/work-requests/{requestId}/detail")
    public ResponseEntity<AgencyAsRequestDetailResponse> getWorkRequestDetail(
            @PathVariable Long requestId,
            @AuthenticationPrincipal CustomUserDetails userDetails) throws IllegalAccessException {

        AgencyAsRequestDetailResponse response =
                agencyAsRequestService.getAsRequestDetail(userDetails, requestId);
        return ResponseEntity.ok(response);
    }

    /**
     * 대행사 A/S 상태별 집계 (작업 현황 통계)
     * - WAITING / ENGINEER_DEPARTED / ENGINEER_ARRIVED / IN_PROGRESS / COMPLETED 건수
     */
    @GetMapping("/work-requests/stats")
    public ResponseEntity<AsStatusLogSummaryResponse> getWorkRequestStats(
            @AuthenticationPrincipal CustomUserDetails userDetails) throws IllegalAccessException {

        AsStatusLogSummaryResponse response = agencyAsStatusLogService.getStatusSummary(userDetails);
        return ResponseEntity.ok(response);
    }

    /**
     * 대행사 A/S 접수 반려
     * - 배정 대기(AGENCY_RECEIVED) 상태의 요청만 반려 가능 (그 외 상태는 400)
     * - 본인 소속 대행사의 요청이 아니면 401, 존재하지 않는 requestId는 404
     * - 반려 시 고객에게 재신청 안내 알림 자동 발송(자동/수동 배정에 따라 문구 다름)
     */
    @PatchMapping("/work-requests/{requestId}/reject")
    public ResponseEntity<Void> rejectWorkRequest(
            @PathVariable Long requestId,
            @RequestParam(required = false) String reason,
            @AuthenticationPrincipal CustomUserDetails userDetails) throws IllegalAccessException {

        agencyAsRequestService.rejectAsRequest(userDetails, requestId, reason);
        return ResponseEntity.noContent().build();
    }
}
