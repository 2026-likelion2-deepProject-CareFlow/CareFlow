package com.careflow.agency.controller;

import com.careflow.agency.dto.request.AgencySettlementSearchRequest;
import com.careflow.agency.dto.request.SettlementStatusUpdateRequest;
import com.careflow.agency.dto.response.AgencySettlementListResponse;
import com.careflow.agency.service.AgencySettlementService;
import com.careflow.auth.security.CustomUserDetails;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;


/**
 * 모든 엔드포인트는 AGENCY 역할만 접근 가능하다.
 */
@RestController
@RequestMapping("/api/agency/settlements")
@RequiredArgsConstructor
@PreAuthorize("hasRole('AGENCY')")
public class AgencySettlementController {

    private final AgencySettlementService agencySettlementService;

    /**
     * 대행사 정산 목록 조회
     * GET /api/agency/settlements?status=&keyword=&dateFrom=&dateTo=&page=0&size=10
     *
     * - 필터 조건(status/keyword/dateFrom/dateTo)은 쿼리 파라미터로 수신
     *   (GET 요청 바디는 표준이 아니라 클라이언트/프록시 환경에 따라 안정적으로 전달되지 않아 쿼리 파라미터로 변경)
     * - 파라미터 생략 시 전체 조회로 처리
     */
    @GetMapping
    public ResponseEntity<AgencySettlementListResponse> getSettlements(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String dateFrom,
            @RequestParam(required = false) String dateTo,
            @PageableDefault(size = 10) Pageable pageable) throws IllegalAccessException {

        AgencySettlementSearchRequest filter =
                new AgencySettlementSearchRequest(status, keyword, dateFrom, dateTo);

        return ResponseEntity.ok(agencySettlementService.getSettlements(userDetails, filter, pageable));
    }

    /**
     * 정산 상태 변경
     * PATCH /api/agency/settlements/{settlementId}/status
     *
     * 전이 규칙 ([DDL v11] APPROVED 제거 — PENDING에서 바로 PAID로 전이):
     *   - PAID → 변경 불가
     *   - 그 외(PENDING↔DISPUTED, → PAID) 간 전이 자유
     */
    @PatchMapping("/{settlementId}/status")
    public ResponseEntity<Void> updateStatus(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable Long settlementId,
            @Valid @RequestBody SettlementStatusUpdateRequest request) throws IllegalAccessException {

        agencySettlementService.updateStatus(userDetails, settlementId, request);
        return ResponseEntity.noContent().build();
    }
}
