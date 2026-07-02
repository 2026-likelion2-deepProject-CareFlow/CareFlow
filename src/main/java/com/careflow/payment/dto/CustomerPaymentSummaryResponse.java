package com.careflow.payment.dto;

/**
 * 고객 결제 요약 KPI 응답 DTO
 */
public record CustomerPaymentSummaryResponse(
        long totalAmount,      // payments.status='SUCCESS' 전체 합계 (원)
        long thisMonthAmount,  // 위 중 이번 달(paid_at 기준) 합계 (원)
        long unpaidCount,      // as_requests.status='COMPLETED' 건수 — 결제 대기 중인 건
        long partsAmount,      // SUCCESS 결제 건 기준 부품비 합계 — work_report_parts(quantity * applied_unit_price)를 as_request 단위로 집계
        long otherAmount       // 부품비를 제외한 나머지 금액 = totalAmount - partsAmount (공임 등)
) {}
