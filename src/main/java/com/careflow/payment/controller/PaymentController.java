package com.careflow.payment.controller;

import com.careflow.auth.security.CustomUserDetails;
import com.careflow.payment.dto.PaymentConfirmRequest;
import com.careflow.payment.dto.PaymentResponse;
import com.careflow.payment.service.PaymentService;
import jakarta.validation.Valid;
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
     * 고객용: A/S 요청 결제 승인 API (토스페이먼츠 결제위젯 연동)
     * - 프론트가 결제위젯 successUrl 리다이렉트로 받은 paymentKey/orderId/amount를 그대로 전달
     * - COMPLETED 상태이며 고객 승인된 work_reports가 있어야 결제 가능
     * - 결제 금액은 work_reports.final_amount 기준으로 서버에서 재검증 (클라이언트 amount는 대조용)
     * - 본인 요청이 아닌 경우 401 반환
     * - 이미 결제된 요청에 재결제 시도 시 403 반환
     * - 결제 성공 시 as_requests.status → PAID, 201 반환
     */
    @PostMapping("/{requestId}/payment")
    public ResponseEntity<PaymentResponse> processPayment(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable Long requestId,
            @Valid @RequestBody PaymentConfirmRequest request) throws IllegalAccessException {

        PaymentResponse response = paymentService.processPayment(
                userDetails.getUserId(), requestId,
                request.paymentKey(), request.orderId(), request.amount());
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }
}
