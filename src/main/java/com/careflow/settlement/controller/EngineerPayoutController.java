package com.careflow.settlement.controller;

import com.careflow.auth.security.CustomUserDetails;
import com.careflow.settlement.dto.EngineerPayoutPageResponse;
import com.careflow.settlement.service.EngineerPayoutService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/engineer/payouts")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ENGINEER')")
public class EngineerPayoutController {

    private final EngineerPayoutService engineerPayoutService;

    /**
     * 기사 본인의 지급 배치 이력 조회 (건별 정산 원장이 아니라 월별 지급 완료 여부 확인용)
     * GET /api/engineer/payouts?page=0&size=10
     */
    @GetMapping
    public ResponseEntity<Page<EngineerPayoutPageResponse>> getMyPayouts(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) throws IllegalAccessException {

        Page<EngineerPayoutPageResponse> response =
                engineerPayoutService.getMyPayouts(userDetails, PageRequest.of(page, size));

        return ResponseEntity.ok(response);
    }
}
