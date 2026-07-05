package com.careflow.admin.dto.response;

// GET /api/admin/settlements/{agencyId}/details 응답 DTO — 대행사 건별 정산 내역
public record AdminSettlementDetailResponse(
        Long settlementId,       // PK 원본 값 — PATCH /api/admin/settlements/{settlementId}/status 호출 시 사용
        String settlementCode,   // "SET-001" 형식 (PK 3자리 zero-padding, 화면 표시용)
        String completedAt,  // yyyy-MM-dd (settlements.created_at 날짜부)
        String applianceName,
        String customerName,
        long totalAmount,
        long careflowFee,
        long agencyPay,
        String status // PENDING / PAID / DISPUTED — CareFlow→대행사 정산 상태
) {
}
