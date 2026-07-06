package com.careflow.payment.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * 토스페이먼츠 결제 승인(POST /v1/payments/confirm) API 응답 중 실제로 쓰는 필드만 추림
 * (카드/가상계좌/현금영수증 등 나머지 필드는 무시)
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record TossConfirmResponse(
        String paymentKey,
        String orderId,
        String status,
        Integer totalAmount,
        String method,
        String approvedAt
) {}
