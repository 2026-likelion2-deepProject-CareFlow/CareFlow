package com.careflow.payment.dto;

/**
 * 고객 월별 결제액 추이 응답 DTO
 */
public record CustomerMonthlyPaymentResponse(
        String yearMonth, // "yyyy-MM" 형식 (예: "2026-07")
        long amount       // 해당 월 SUCCESS 결제 합계 (원)
) {}
