package com.careflow.payment.controller;

import com.careflow.auth.security.CustomUserDetails;
import com.careflow.payment.dto.PaymentResponse;
import com.careflow.payment.service.PaymentService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/customer/as-requests")
@RequiredArgsConstructor
public class PaymentController {

    private final PaymentService paymentService;

    /**
     * 고객용: A/S 요청 결제 API (Phase 1: MOCK)
     * - COMPLETED 상태이며 고객 승인된 work_reports가 있어야 결제 가능
     * - 결제 금액은 work_reports.final_amount 기준으로 서버에서 확정 (클라이언트 amount 미사용)
     * - 본인 요청이 아닌 경우 401 반환
     * - 이미 결제된 요청에 재결제 시도 시 403 반환
     * - 결제 성공 시 as_requests.status → PAID, 201 반환
     */
    @PostMapping("/{requestId}/payment")
    public ResponseEntity<PaymentResponse> processPayment(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable Long requestId) throws IllegalAccessException {

        PaymentResponse response = paymentService.processPayment(
                userDetails.getUserId(), requestId);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }
}
