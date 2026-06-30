package com.careflow.agency.controller;

import com.careflow.agency.dto.request.AgencyCustomerSearchRequest;
import com.careflow.agency.dto.response.AgencyCustomerApplianceResponse;
import com.careflow.agency.dto.response.AgencyCustomerAsRequestResponse;
import com.careflow.agency.dto.response.AgencyCustomerListResponse;
import com.careflow.agency.dto.response.AgencyCustomerPaymentResponse;
import com.careflow.agency.service.AgencyCustomerService;
import com.careflow.auth.security.CustomUserDetails;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/agency/customers")
@RequiredArgsConstructor
public class AgencyCustomerController {

    private final AgencyCustomerService agencyCustomerService;

    /**
     * [GET] /api/agency/customers?page=0&size=10
     * 본인 대행사 소속 기사에게 COMPLETED 서비스를 1회 이상 받은 고객 목록을 검색/페이징 조회한다.
     * - 요청 바디(keyword/status/grade/joinPath/joinedFrom/joinedTo)는 선택 — 미전송 시 전체 조회
     * - role != AGENCY → Security 설정에서 403/401 처리
     */
    @GetMapping
    public ResponseEntity<AgencyCustomerListResponse> getCustomers(
            @PageableDefault(page = 0, size = 10) Pageable pageable,
            @RequestBody(required = false) AgencyCustomerSearchRequest request,
            @AuthenticationPrincipal CustomUserDetails userDetails) throws IllegalAccessException {

        AgencyCustomerListResponse response =
                agencyCustomerService.searchCustomers(userDetails, pageable, request);
        return ResponseEntity.ok(response);
    }

    /**
     * [GET] /api/agency/customers/{userId}/appliances
     * 본인 대행사로부터 COMPLETED 서비스를 받은 특정 고객의 등록 가전 목록을 조회한다.
     * - userId가 존재하지 않으면 404, 본인 대행사와 서비스 이력이 없는 고객이면 401(데이터 격리)
     */
    @GetMapping("/{userId}/appliances")
    public ResponseEntity<List<AgencyCustomerApplianceResponse>> getCustomerAppliances(
            @PathVariable Long userId,
            @AuthenticationPrincipal CustomUserDetails userDetails) throws IllegalAccessException {

        List<AgencyCustomerApplianceResponse> response =
                agencyCustomerService.getCustomerAppliances(userDetails, userId);
        return ResponseEntity.ok(response);
    }

    /**
     * [GET] /api/agency/customers/{userId}/as-requests
     * 본인 대행사로부터 COMPLETED 서비스를 받은 특정 고객이 본인 대행사로 접수한 A/S 이력을 조회한다(상태 무관 전체).
     * - userId가 존재하지 않으면 404, 본인 대행사와 서비스 이력이 없는 고객이면 401(데이터 격리)
     */
    @GetMapping("/{userId}/as-requests")
    public ResponseEntity<List<AgencyCustomerAsRequestResponse>> getCustomerAsRequests(
            @PathVariable Long userId,
            @AuthenticationPrincipal CustomUserDetails userDetails) throws IllegalAccessException {

        List<AgencyCustomerAsRequestResponse> response =
                agencyCustomerService.getCustomerAsRequests(userDetails, userId);
        return ResponseEntity.ok(response);
    }

    /**
     * [GET] /api/agency/customers/{userId}/payments
     * 본인 대행사로부터 COMPLETED 서비스를 받은 특정 고객이 본인 대행사로 접수한 A/S 건의 결제 내역을 조회한다(상태 무관 전체).
     * - userId가 존재하지 않으면 404, 본인 대행사와 서비스 이력이 없는 고객이면 401(데이터 격리)
     */
    @GetMapping("/{userId}/payments")
    public ResponseEntity<List<AgencyCustomerPaymentResponse>> getCustomerPayments(
            @PathVariable Long userId,
            @AuthenticationPrincipal CustomUserDetails userDetails) throws IllegalAccessException {

        List<AgencyCustomerPaymentResponse> response =
                agencyCustomerService.getCustomerPayments(userDetails, userId);
        return ResponseEntity.ok(response);
    }
}
