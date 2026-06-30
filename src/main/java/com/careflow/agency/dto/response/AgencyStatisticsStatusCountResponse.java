package com.careflow.agency.dto.response;

public record AgencyStatisticsStatusCountResponse(
        String status,
        long count,
        double percentage
) {}
