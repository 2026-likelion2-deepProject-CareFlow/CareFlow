package com.careflow.agency.dto.response;

public record AgencyStatisticsSummaryResponse(
        long totalReceipts,
        double totalReceiptsChangeRate,
        long completedCount,
        double completedCountChangeRate,
        double completionRate,
        double completionRateChange,
        double avgProcessingTimeHours,
        double avgProcessingTimeChange,
        double avgRating,
        double avgRatingChange,
        long totalSettlementAmount,
        double totalSettlementAmountChangeRate
) {}
