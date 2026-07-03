package com.careflow.settlement.dto;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class MonthlySummaryResponse {
    private int year;
    private int month;
    // 전체 건수 / 총 gross (모든 status 포함)
    private long totalCount;
    private long totalGrossAmount;
    // 상태별 gross 합산 — UI 통계카드 대응
    private long paidAmount;       // status = PAID
    private long pendingAmount;    // status = PENDING
    private long disputedAmount;   // status = DISPUTED
    // 수수료 / 기사지급액 합산 (CSV 다운로드용)
    private long totalPlatformFee;
    private long totalAgencyFee;
    private long totalEngineerPayout;
}
