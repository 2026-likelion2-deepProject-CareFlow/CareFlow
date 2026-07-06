package com.careflow.agency.dto.response;

public record AgencyStatisticsPeriodSummaryResponse(
        String periodLabel,          // 예: "2026.05.01 ~ 2026.05.31"
        long receiptCount,           // 해당 기간 접수 건수
        long completedCount,         // 해당 기간 완료 건수
        double completionRate,       // 완료율 (%)
        double avgProcessingHours,   // 평균 처리 시간 (시간)
        double avgRating,            // 평균 평점
        long totalSettlementAmount   // 총 정산 금액 (원)
) {}
