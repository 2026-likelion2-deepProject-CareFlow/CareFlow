package com.careflow.settlement.dto;

import com.careflow.settlement.entity.EngineerPayout;

import java.time.LocalDateTime;

// 기사 본인 지급 배치 이력 조회(GET /api/engineer/payouts) 응답 DTO
public record EngineerPayoutPageResponse(
        Long engineerPayoutId,
        String agencyName,
        Integer payoutYear,
        Integer payoutMonth,
        int netAmountSum,
        int caseCount,
        String status,
        LocalDateTime paidAt
) {
    public static EngineerPayoutPageResponse from(EngineerPayout ep) {
        return new EngineerPayoutPageResponse(
                ep.getId(),
                ep.getAgency().getAgencyName(),
                ep.getPayoutYear(),
                ep.getPayoutMonth(),
                ep.getNetAmountSum(),
                ep.getCaseCount(),
                ep.getStatus(),
                ep.getPaidAt());
    }
}
