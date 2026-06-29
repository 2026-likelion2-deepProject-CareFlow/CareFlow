package com.careflow.agency.dto.response;

public record AgencyStatisticsMonthlySummaryResponse(
        String topDayOfWeek,
        String topHourSlot,
        String topRatedEngineerName,
        double customerSatisfactionRate
) {}
