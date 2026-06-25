package com.careflow.settlement.dto;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class EngineerPerformanceItem {
    private Long engineerId;
    private String engineerName;
    private long completedCount;
    private Double avgRating;    // 해당 월 리뷰가 없으면 null
    private long totalEarning;
}
