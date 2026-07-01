// 파일 경로: src/main/java/com/careflow/engineer/dto/EngineerSettlementSummaryResponse.java
package com.careflow.engineer.dto;

import lombok.Builder;
import lombok.Getter;
import java.util.List;

@Getter
@Builder
public class EngineerSettlementSummaryResponse {
    private long totalCompletedCount;
    private long totalGrossAmount;
    private double avgRating;
    private double customerSatisfaction; // 4~5점 비율 (%)
    private long rejectedCount;

    private List<DailyTrend> dailyTrends;
    private List<BrandDist> brandDistributions;
    private List<StatusDist> statusDistributions;

    private List<PerformanceItem> performanceList;
    private SettlementSummary settlementSummary;

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
}