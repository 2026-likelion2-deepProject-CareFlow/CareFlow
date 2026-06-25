package com.careflow.settlement.dto;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class MonthlySummaryResponse {
    private int year;
    private int month;
    private long totalCount;
    private long totalGrossAmount;
    private long totalPlatformFee;
    private long totalAgencyFee;
    private long totalEngineerPayout;
}
