package com.careflow.agency.dto.response;

public record AgencyStatisticsStatusCountResponse(
        String statusLabel,
        long count,
        double percentage
) {}
