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
            String status // PENDING / PAID / NONE
    ) {
    }
}
