package com.careflow.agency.controller;

import com.careflow.agency.dto.request.EngineerPayoutStatusUpdateRequest;
import com.careflow.agency.dto.response.AgencyEngineerPayoutListResponse;
import com.careflow.agency.service.AgencyEngineerPayoutService;
import com.careflow.auth.security.CustomUserDetails;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/**
 * 대행사용 "대행사→기사 지급" 관리 컨트롤러
 * 모든 엔드포인트는 AGENCY 역할만 접근 가능하다.
 */
@RestController
@RequestMapping("/api/agency/engineer-payouts")
@RequiredArgsConstructor
public class AgencyEngineerPayoutController {

    private final AgencyEngineerPayoutService agencyEngineerPayoutService;

    /**
     * 기사별 지급 대상 목록 조회
     * GET /api/agency/engineer-payouts?year=&month=&page=0&size=10
     */
    @GetMapping
    public ResponseEntity<AgencyEngineerPayoutListResponse> getEngineerPayouts(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @RequestParam int year,
            @RequestParam int month,
            @PageableDefault(size = 10) Pageable pageable) throws IllegalAccessException {

        return ResponseEntity.ok(
                agencyEngineerPayoutService.getEngineerPayouts(userDetails, year, month, pageable));
    }

    /**
     * 기사 지급 완료 처리
     * PATCH /api/agency/engineer-payouts/{engineerPayoutId}/pay
     * - CareFlow가 자금을 집행하지 않으므로 계좌 등록 여부를 검증하지 않는다(대행사 자체 신고).
     */
    @PatchMapping("/{engineerPayoutId}/pay")
    public ResponseEntity<Void> payEngineerPayout(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable Long engineerPayoutId) throws IllegalAccessException {

        agencyEngineerPayoutService.payEngineerPayout(userDetails, engineerPayoutId);
        return ResponseEntity.noContent().build();
    }

    /**
     * 지급 건별 상태 변경 (이의/보류용 — PENDING↔DISPUTED 간 전이만 허용)
     * PATCH /api/agency/engineer-payouts/{engineerPayoutId}/status
     */
    @PatchMapping("/{engineerPayoutId}/status")
    public ResponseEntity<Void> updateStatus(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable Long engineerPayoutId,
            @Valid @RequestBody EngineerPayoutStatusUpdateRequest request) throws IllegalAccessException {

        agencyEngineerPayoutService.updateStatus(userDetails, engineerPayoutId, request.status());
        return ResponseEntity.noContent().build();
    }
}
