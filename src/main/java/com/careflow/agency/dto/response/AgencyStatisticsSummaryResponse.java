package com.careflow.agency.dto.response;

public record AgencyStatisticsSummaryResponse(
        long totalReceiptCount,           // 조회 기간 내 총 A/S 접수 건수
        long completedCount,              // 조회 기간 내 처리 완료 건수
        double completionRate,            // 완료율 (%) = completedCount / totalReceiptCount × 100
        double avgProcessingHours,        // 평균 처리 소요 시간 (시간 단위)
        double avgRating,                 // 완료 건에 대한 평균 고객 평점
        long totalSettlementAmount,       // 조회 기간 내 총 정산 금액 (원)
        double prevMonthReceiptDiff,      // 전 기간 대비 접수 건수 증감률 (%)
        double prevMonthCompletedDiff,    // 전 기간 대비 완료 건수 증감률 (%)
        double prevMonthRatingDiff,       // 전 기간 대비 평점 변화량 (절대 차이)
        double prevMonthAmountDiff        // 전 기간 대비 정산 금액 증감률 (%)
) {}
