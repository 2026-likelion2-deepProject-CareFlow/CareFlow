package com.careflow.agency.dto.response;

public record AgencyStatisticsDailyTrendResponse(
        String date,
        long receiptCount,
        long completedCount
) {}
