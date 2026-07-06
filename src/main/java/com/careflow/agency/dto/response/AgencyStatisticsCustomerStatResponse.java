package com.careflow.agency.dto.response;

public record AgencyStatisticsCustomerStatResponse(
        Long customerId,
        String customerName,
        long requestCount,
        Double avgRating   // 해당 고객이 작성한 리뷰 평균 평점, 리뷰가 없으면 null
) {}
