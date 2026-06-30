package com.careflow.agency.dto.response;

public record AgencyStatisticsMonthlySummaryResponse(
        String topReceiptDayOfWeek,     // 이번 달 최다 접수 요일
        long topReceiptDayCount,        // 해당 요일의 접수 건수
        String topReceiptHour,          // 이번 달 최다 접수 시간대
        long topReceiptHourCount,       // 해당 시간대의 접수 건수
        String topRatingEngineerName,   // 이번 달 최고 평점 기사명
        double topRatingEngineerScore   // 해당 기사의 평점
) {}
