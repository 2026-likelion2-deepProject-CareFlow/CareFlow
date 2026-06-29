package com.careflow.agency.dto.response;

public record AgencyStatisticsEngineerTop5Response(
        int rank,
        String engineerName,
        long completedCount
) {}
