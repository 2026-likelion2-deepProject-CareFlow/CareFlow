package com.careflow.payment.dto;

import java.time.LocalDateTime;

/**
 * 결제 처리 결과 응답 DTO
 * Phase 1(MOCK): status 는 항상 SUCCESS, pgProvider 는 항상 MOCK
 */
public record PaymentResponse(
        Long paymentId,
        Long requestId,
        Integer amount,
        String status,
        String pgProvider,
        LocalDateTime paidAt
) {}
