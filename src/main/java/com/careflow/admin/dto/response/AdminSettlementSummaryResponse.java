package com.careflow.admin.dto.response;

import java.util.List;

// GET /api/admin/settlements 응답 DTO — 전체 대행사 대상 월별 정산 현황
public record AdminSettlementSummaryResponse(
        Summary summary,
        List<AgencySettlementItem> agencies
) {
    public record Summary(
            long totalRevenue,
            long totalCareflowFee,
            long totalAgencyPay,
            long pendingCount
    ) {
    }

    public record AgencySettlementItem(
            Long agencyId,
            String agencyName,
            long asCount,
            long totalRevenue,
            long careflowFee,
            long agencyPay,
            String status, // PENDING / PAID / NONE — settlements 집계 기반 파생 상태(화면 필터/버튼 노출용)
            String platformSettlementStatus, // platform_settlements.status 원본값(PENDING/PAID/DISPUTED) — 배치가 없으면 null
            String paidBankAccount // 지급 시점 스냅샷 계좌 "은행명 계좌번호" — 미지급/배치 없음이면 null
    ) {
    }
}
