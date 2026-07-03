package com.careflow.payment.controller;

import com.careflow.auth.security.CustomUserDetails;
import com.careflow.payment.dto.CustomerMonthlyPaymentResponse;
import com.careflow.payment.dto.CustomerPaymentSummaryResponse;
import com.careflow.payment.dto.PaymentResponse;
import com.careflow.payment.service.PaymentService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

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

    /**
     * 고객용: 월별 결제액 추이 조회
     * - 이번 달을 포함한 최근 6개월, 오래된 달부터 최신 달 순으로 반환
     * - 로그인한 본인(customerId) 데이터만 조회 — 클라이언트가 보낸 값은 사용하지 않음
     */
    @GetMapping("/monthly")
    public ResponseEntity<List<CustomerMonthlyPaymentResponse>> getMonthlyPayments(
            @AuthenticationPrincipal CustomUserDetails userDetails) {

        List<CustomerMonthlyPaymentResponse> response = paymentService.getMonthlyPayments(userDetails.getUserId());
        return ResponseEntity.ok(response);
    }

    /**
     * 고객용: 결제 내역 전체 조회
     * - 상태 필터 없이 최신순으로 반환
     * - 로그인한 본인(customerId) 데이터만 조회 — 클라이언트가 보낸 값은 사용하지 않음
     */
    @GetMapping
    public ResponseEntity<List<PaymentResponse>> getPaymentList(
            @AuthenticationPrincipal CustomUserDetails userDetails) {

        List<PaymentResponse> response = paymentService.getPaymentList(userDetails.getUserId());
        return ResponseEntity.ok(response);
    }
}
