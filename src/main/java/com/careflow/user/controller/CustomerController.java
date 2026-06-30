package com.careflow.user.controller;

import com.careflow.as_request.dto.AsRequestResponseDto;
import com.careflow.as_request.service.AsRequestService;
import com.careflow.auth.security.CustomUserDetails;
import com.careflow.engineer.dto.CustomerEngineerAvailabilityResponse;
import com.careflow.engineer.dto.CustomerEngineerSummaryResponse;
import com.careflow.engineer.service.CustomerEngineerQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

/**
 * 고객(CUSTOMER) 전용 컨트롤러 — /api/customers
 *
 * 프론트엔드 URI 규약(/api/customers/{customerId}/*)에 맞춰 고객 관련 API를 제공한다.
 * customerId 경로 파라미터는 URL 구조 일치를 위해 선언하며, 실제 인증은 JWT로 처리한다.
 */
@RestController
@RequestMapping("/api/customers/{customerId}")
@RequiredArgsConstructor
public class CustomerController {

    private final AsRequestService asRequestService;
    private final CustomerEngineerQueryService customerEngineerQueryService;

    /**
     * 고객 본인의 A/S 접수 목록 조회
     * GET /api/customers/{customerId}/as-requests
     */
    @GetMapping("/as-requests")
    public ResponseEntity<List<AsRequestResponseDto>> getMyAsRequests(
            @PathVariable Long customerId,
            @AuthenticationPrincipal CustomUserDetails userDetails) {

        List<AsRequestResponseDto> myRequests =
                asRequestService.getMyAsRequests(userDetails.getUserId());
        return ResponseEntity.ok(myRequests);
    }

    /**
     * 수동 배정 - 조건(지역/브랜드/보유기술) 필터링된 후보 기사 목록 조회
     * GET /api/customers/{customerId}/engineers/available?regionId=&brand=&skill=
     */
    @GetMapping("/engineers/available")
    public ResponseEntity<List<CustomerEngineerSummaryResponse>> getAvailableEngineers(
            @PathVariable Long customerId,
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @RequestParam Integer regionId,
            @RequestParam(required = false) String brand,
            @RequestParam(required = false) String skill) {

        List<CustomerEngineerSummaryResponse> response =
                customerEngineerQueryService.getAvailableEngineers(regionId, brand, skill);
        return ResponseEntity.ok(response);
    }

    /**
     * 수동 배정 - 선택한 기사의 가능 일정(날짜/시간대) 조회
     * GET /api/customers/{customerId}/engineers/{engineerId}/availability?from=&to=
     */
    @GetMapping("/engineers/{engineerId}/availability")
    public ResponseEntity<CustomerEngineerAvailabilityResponse> getEngineerAvailability(
            @PathVariable Long customerId,
            @PathVariable Long engineerId,
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {

        CustomerEngineerAvailabilityResponse response =
                customerEngineerQueryService.getEngineerAvailability(engineerId, from, to);
        return ResponseEntity.ok(response);
    }
}
