package com.careflow.agency.dto.response;

public record AgencyStatisticsEngineerStatResponse(
        int rank,
        Long engineerId,
        String engineerName,
        long completedCount,
        Double avgRating   // 리뷰가 없으면 null
) {}
