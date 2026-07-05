package com.careflow.admin.dto.response;

import com.careflow.settlement.entity.EngineerPayout;

import java.time.LocalDateTime;
import java.util.List;

// 관리자용 대행사→기사 지급 배치 전체 조회(GET /api/admin/engineer-payouts) 응답 DTO
public record AdminEngineerPayoutListResponse(
        List<Item> items
) {
    public record Item(
            Long engineerPayoutId,
            Long agencyId,
            String agencyName,
            Long engineerId,
            String engineerName,
            int netAmountSum,
            int caseCount,
            String status,
            LocalDateTime paidAt
    ) {
        public static Item from(EngineerPayout ep) {
            return new Item(
                    ep.getId(),
                    ep.getAgency().getId(),
                    ep.getAgency().getAgencyName(),
                    ep.getEngineer().getId(),
                    ep.getEngineer().getName(),
                    ep.getNetAmountSum(),
                    ep.getCaseCount(),
                    ep.getStatus(),
                    ep.getPaidAt());
        }
    }
}
