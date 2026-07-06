package com.careflow.as_request.controller;

import com.careflow.as_request.dto.AgencyAsRequestDetailResponse;
import com.careflow.as_request.dto.AsRequestCreateDto;
import com.careflow.as_request.dto.AsRequestCreateResponseDto;
import com.careflow.as_request.dto.AsRequestResponseDto;
import com.careflow.as_request.dto.CustomerAsRequestDetailResponse;
import com.careflow.as_request.dto.ExpectedRepairCostResponse;
import com.careflow.as_request.service.AgencyAsRequestService;
import com.careflow.as_request.service.AsRequestService;
import com.careflow.auth.security.CustomUserDetails;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

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
     * 고객용: 증상별 예상 수리 비용 조회 API
     * - A/S 접수 마지막 확인 단계에서 사용
     * - Quartz 배치 집계 전(avgCost=null)이면 프론트에서 "데이터 수집 중"으로 표시
     */
    @GetMapping("/expected-cost/{symptomId}")
    public ResponseEntity<ExpectedRepairCostResponse> getExpectedRepairCost(
            @PathVariable Long symptomId) {

        ExpectedRepairCostResponse response =
                asRequestService.getExpectedRepairCost(symptomId);
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
     * PENDING, AGENCY_RECEIVED, ASSIGNED(예약 확정 전) 상태일 때만 취소 가능
     */
    @PatchMapping("/{asRequestId}/cancel")
    public ResponseEntity<Void> cancelAsRequest(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable Long asRequestId,
            @RequestParam(required = false) String cancelReason) {

        asRequestService.cancelAsRequest(userDetails.getUserId(), asRequestId, cancelReason);
        return ResponseEntity.ok().build();
    }

    // 대행사용 단건 상세 조회는 AgencyController /api/agency/work-requests/{requestId}/detail 로 이관됨
}