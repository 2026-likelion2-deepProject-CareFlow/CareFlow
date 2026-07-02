// 파일 경로: src/main/java/com/careflow/engineer/dto/EngineerSettlementSummaryResponse.java
package com.careflow.settlement.dto;

import lombok.Builder;
import lombok.Getter;
import java.util.List;

@Getter
@Builder
public class EngineerSettlementSummaryResponse {
    private long totalCompletedCount;
    private long inProgressCount;   // [추가] 진행 중(ACCEPTED, 미완료) 건수 — 조회 기간(scheduledDate) 기준
    private long cancelledCount;    // [추가] 취소(as_requests.status=CANCELLED, 기사 거절건 제외) 건수 — 조회 기간(scheduledDate) 기준
    private long totalGrossAmount;
    private double avgRating;
    private double customerSatisfaction; // 4~5점 비율 (%)
    private long rejectedCount;

    private List<DailyTrend> dailyTrends;
    private List<BrandDist> brandDistributions;
    private List<StatusDist> statusDistributions;

    private List<PerformanceItem> performanceList;
    private SettlementSummary settlementSummary;
    private MonthlyComparison monthlyComparison; // [추가] 이번 달/지난 달 비교 (조회 기간 필터와 무관하게 항상 '이번 달 vs 전월')

    @Getter @Builder public static class DailyTrend {
        private String date;
        private long count;
    }
    @Getter @Builder public static class BrandDist {
        private String name;
        private long value;
        private String pct;
        private String color;
    }
    @Getter @Builder public static class StatusDist {
        private String name;
        private long value;
        private String pct;
        private String color;
    }
    @Getter @Builder public static class PerformanceItem {
        private String requestId;
        private String workDate;
        private String customerName;
        private String productName;
        private String brand;
        private int grossAmount;
        private String diagnosisResult;
        private double rating;
    }
    @Getter @Builder public static class SettlementSummary {
        private int grossAmount;
        private int platformFee;
        private int agencyFee;
        private int engineerNetAmount;
        private String paidAt;
        private String bankName; // v21 명세 추가 사항
        private String accountNumber;
    }

    /**
     * 이번 달 / 지난 달 비교 블록.
     * 조회 기간(dateFrom·dateTo)과 무관하게 항상 '이번 달 vs 전월'을 고정 산정한다(대행사 리뷰 통계와 동일 패턴).
     * - 정산 금액(net): settlements.engineer_net_amount 합, createdAt 기준
     * - 평점: 해당 월 신규 리뷰 평균, createdAt 기준 (프로필 누적 평균과는 다른 값)
     * - 완료 건수: COMPLETED 배정 수, scheduledDate 기준
     * diff = 이번 달 - 지난 달
     */
    @Getter @Builder public static class MonthlyComparison {
        private int thisMonthNetAmount;
        private int prevMonthNetAmount;
        private int netAmountDiff;

        private double thisMonthAvgRating;
        private double prevMonthAvgRating;
        private double avgRatingDiff;

        private long thisMonthCompletedCount;
        private long prevMonthCompletedCount;
        private long completedCountDiff;
    }
}