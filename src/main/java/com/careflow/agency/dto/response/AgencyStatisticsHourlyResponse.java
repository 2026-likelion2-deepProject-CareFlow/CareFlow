package com.careflow.agency.dto.response;

public record AgencyStatisticsHourlyResponse(
        String timeSlot,
        long count
) {}
