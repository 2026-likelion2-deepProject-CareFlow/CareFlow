package com.careflow.admin.dto.response;

// GET /api/admin/settlements/{agencyId}/details 응답 DTO — 대행사 건별 정산 내역
public record AdminSettlementDetailResponse(
        String settlementId, // "SET-001" 형식 (PK 3자리 zero-padding)
        String completedAt,  // yyyy-MM-dd (settlements.created_at 날짜부)
        String applianceName,
        String customerName,
        long totalAmount,
        long careflowFee,
        long agencyPay
) {
}
