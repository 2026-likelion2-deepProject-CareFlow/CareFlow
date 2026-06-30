package com.careflow.agency.dto.response;

public record AgencyStatisticsHourlyResponse(
        String timeRange,
        long count
) {}
