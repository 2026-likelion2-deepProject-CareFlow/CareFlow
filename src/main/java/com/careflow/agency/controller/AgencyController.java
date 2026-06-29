package com.careflow.agency.controller;

import com.careflow.agency.dto.request.AgencyProfileUpdateRequest;
import com.careflow.agency.dto.response.AgencyProfileResponse;
import com.careflow.agency.service.AgenciesService;
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
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

/**
 * 대행사 전용 통합 컨트롤러 — /api/agency
 *
 * 프론트엔드 URI 규약(단수형 /api/agency/*)에 맞춰 대행사 설정 및 작업 관련 API를 제공한다.
 */
@RestController
@RequestMapping("/api/agency")
@RequiredArgsConstructor
public class AgencyController {

    private final AgenciesService agenciesService;
    private final AgencyAsRequestService agencyAsRequestService;
    private final AgencyAsStatusLogService agencyAsStatusLogService;

    // ─────────────────────────────────────────────
    //  대행사 설정 API
    // ─────────────────────────────────────────────

    // 대행사 내 정보 조회 (설정 페이지)
    @GetMapping("/me")
    public ResponseEntity<AgencyProfileResponse> getAgencyProfile(
            @AuthenticationPrincipal CustomUserDetails userDetails) {

        AgencyProfileResponse response = agenciesService.getProfile(userDetails.getUserId());
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
}
