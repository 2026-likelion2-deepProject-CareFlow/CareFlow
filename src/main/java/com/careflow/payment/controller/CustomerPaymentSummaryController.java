package com.careflow.payment.controller;

import com.careflow.auth.security.CustomUserDetails;
import com.careflow.payment.dto.CustomerPaymentSummaryResponse;
import com.careflow.payment.service.PaymentService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/customer/payments")
@RequiredArgsConstructor
public class CustomerPaymentSummaryController {

    private final PaymentService paymentService;

    /**
     * 고객용: 결제 요약 KPI 조회
     * - totalAmount: status=SUCCESS 결제 전체 합계
     * - thisMonthAmount: 위 중 이번 달(paid_at 기준) 합계
     * - unpaidCount: status=COMPLETED (결제 대기) A/S 요청 건수
     * - 로그인한 본인(customerId) 데이터만 조회 — 클라이언트가 보낸 값은 사용하지 않음
     */
    @GetMapping("/summary")
    public ResponseEntity<CustomerPaymentSummaryResponse> getPaymentSummary(
            @AuthenticationPrincipal CustomUserDetails userDetails) {

        CustomerPaymentSummaryResponse response = paymentService.getPaymentSummary(userDetails.getUserId());
        return ResponseEntity.ok(response);
    }
}
