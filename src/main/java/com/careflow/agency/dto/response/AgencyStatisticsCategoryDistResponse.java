package com.careflow.agency.dto.response;

public record AgencyStatisticsCategoryDistResponse(
        String categoryName,
        long count,
        double percentage
) {}
