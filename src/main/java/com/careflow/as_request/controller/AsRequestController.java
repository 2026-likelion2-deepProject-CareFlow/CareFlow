package com.careflow.as_request.controller;

import com.careflow.as_request.dto.AgencyAsRequestDetailResponse;
import com.careflow.as_request.dto.AgencyAsRequestListResponse;
import com.careflow.as_request.dto.AgencyDashboardSummaryResponse;
import com.careflow.as_request.dto.AsRequestCreateDto;
import com.careflow.as_request.dto.AsRequestCreateResponseDto;
import com.careflow.as_request.dto.AsRequestResponseDto;
import com.careflow.as_request.dto.CustomerAsRequestDetailResponse;
import com.careflow.as_request.service.AgencyAsRequestService;
import com.careflow.as_request.service.AsRequestService;
import com.careflow.auth.security.CustomUserDetails;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/as-requests")
@RequiredArgsConstructor
public class AsRequestController {

    private final AsRequestService asRequestService;
    private final AgencyAsRequestService agencyAsRequestService;

    /**
     * 고객용: A/S 접수 및 수리 기사 배정 API
     * - assignType = AUTO  : 조건 기반 자동 배정 (4단계 fallback 포함)
     * - assignType = MANUAL: 고객이 직접 지정한 기사(preferredEngineerId)에게 배정
     */
    @PostMapping
    public ResponseEntity<AsRequestCreateResponseDto> createAsRequest(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @Valid @RequestBody AsRequestCreateDto dto) {

        AsRequestCreateResponseDto response =
                asRequestService.createAsRequest(userDetails.getUserId(), dto);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * 고객용: A/S 요청 단건 상세 조회 API
     * - 본인 요청이 아닌 경우 401 반환
     * - 존재하지 않는 requestId → 404 반환
     * - 배정 전 상태이면 engineerName, engineerPhone은 null로 반환
     */
    @GetMapping("/{requestId}")
    public ResponseEntity<CustomerAsRequestDetailResponse> getMyAsRequestDetail(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable Long requestId) throws IllegalAccessException {

        CustomerAsRequestDetailResponse response =
                asRequestService.getMyAsRequestDetail(userDetails.getUserId(), requestId);
        return ResponseEntity.ok(response);
    }

    /**
     * 고객용: 본인의 A/S 목록 조회 API
     */
    @GetMapping("/me")
    public ResponseEntity<List<AsRequestResponseDto>> getMyAsRequests(
            @AuthenticationPrincipal CustomUserDetails userDetails) {

        List<AsRequestResponseDto> myRequests =
                asRequestService.getMyAsRequests(userDetails.getUserId());
        return ResponseEntity.ok(myRequests);
    }

    /**
     * 고객용: A/S 취소 API
     * PENDING 또는 AGENCY_RECEIVED 상태일 때만 취소 가능
     */
    @PatchMapping("/{asRequestId}/cancel")
    public ResponseEntity<Void> cancelAsRequest(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable Long asRequestId,
            @RequestParam(required = false) String cancelReason) {

        asRequestService.cancelAsRequest(userDetails.getUserId(), asRequestId, cancelReason);
        return ResponseEntity.ok().build();
    }

    /**
     * 대행사용: 소속 수리 기사들에게 배정된 A/S 요청 전체 목록 조회
     * - COMPLETED 상태 요청은 제외 (수리 완료된 건은 이 목록에 노출하지 않음)
     * - role != AGENCY → 401
     * - 결과 없음 → 204 No Content
     */
    @GetMapping("/agency")
    public ResponseEntity<List<AgencyAsRequestListResponse>> getAgencyAsRequests(
            @AuthenticationPrincipal CustomUserDetails userDetails) throws IllegalAccessException {

        List<AgencyAsRequestListResponse> result =
                agencyAsRequestService.getAsRequestsByAgency(userDetails);

        if (result.isEmpty()) {
            return ResponseEntity.noContent().build();
        }

        return ResponseEntity.ok(result);
    }

    /**
     * 대행사용: A/S 요청 필터링 조회 (날짜별, 상태별)
     * - date   미입력 시 전체 날짜 조회
     * - status 미입력 시 전체 상태 조회 / COMPLETED 입력 시 빈 결과 반환
     * - role != AGENCY → 401
     * - 결과 없음 → 204 No Content
     */
    @GetMapping("/agency/search")
    public ResponseEntity<List<AgencyAsRequestListResponse>> searchAgencyAsRequests(
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(required = false) String status,
            @AuthenticationPrincipal CustomUserDetails userDetails) throws IllegalAccessException {

        List<AgencyAsRequestListResponse> result =
                agencyAsRequestService.searchAsRequests(userDetails, date, status);

        if (result.isEmpty()) {
            return ResponseEntity.noContent().build();
        }

        return ResponseEntity.ok(result);
    }

    /**
     * 대행사용: 대시보드 요약 통계 조회
     * 대행사 작업 관리 대시보드 최초 진입 시 호출하는 API.
     * - 전체 누적 A/S 접수 건수
     * - 오늘 신규 접수 건수
     * - 오늘 접수 중 ASSIGNED(기사 배정 대기) 건수
     * - 오늘 접수 중 ACCEPTED(기사 배정 승인) 건수
     * - 오늘 접수 중 CANCELLED(고객 취소) 건수
     * role != AGENCY → 401
     */
    @GetMapping("/agency/dashboard-summary")
    public ResponseEntity<AgencyDashboardSummaryResponse> getAgencyDashboardSummary(
            @AuthenticationPrincipal CustomUserDetails userDetails) throws IllegalAccessException {

        AgencyDashboardSummaryResponse response =
                agencyAsRequestService.getDashboardSummary(userDetails);

        return ResponseEntity.ok(response);
    }

    /**
     * 대행사용: A/S 요청 단건 상세 조회
     * - 본인 소속 대행사의 요청만 조회 가능
     * - role != AGENCY 또는 타 대행사 요청 접근 → 401
     * - requestId 미존재 → 404
     */
    @GetMapping("/agency/{requestId}")
    public ResponseEntity<AgencyAsRequestDetailResponse> getAgencyAsRequestDetail(
            @PathVariable Long requestId,
            @AuthenticationPrincipal CustomUserDetails userDetails) throws IllegalAccessException {

        AgencyAsRequestDetailResponse response =
                agencyAsRequestService.getAsRequestDetail(userDetails, requestId);

        return ResponseEntity.ok(response);
    }
}