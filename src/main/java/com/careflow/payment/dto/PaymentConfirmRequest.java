package com.careflow.payment.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * 고객용: 토스페이먼츠 결제 승인 요청 바디
 * - paymentKey/orderId/amount는 토스 결제위젯의 successUrl 리다이렉트로 전달받은 값을 그대로 사용
 * - amount는 신뢰하지 않고 서버가 work_reports.final_amount와 반드시 대조한다 (금액 위변조 방지)
 */
public record PaymentConfirmRequest(
        @NotBlank(message = "paymentKey는 필수입니다.") String paymentKey,
        @NotBlank(message = "orderId는 필수입니다.") String orderId,
        @NotNull(message = "결제 금액은 필수입니다.") Integer amount
) {}
