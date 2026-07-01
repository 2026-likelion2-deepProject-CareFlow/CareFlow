package com.careflow.agency.dto.response;

import org.springframework.data.domain.Page;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public record AgencySettlementListResponse(
        Stats stats,
        List<SettlementSummary> content,
        long totalElements,
        int totalPages,
        int currentPage,
        int size
) {

    public record Stats(
            long thisMonthGrossAmount,
            long paidAmount,
            long pendingAmount,
            long disputedAmount,
            long thisMonthCount,
            long prevMonthCountDiff,
            long prevMonthGrossDiff
    ) {}

    public record SettlementSummary(
            Long settlementId,
            String type,
            Long engineerId,
            String engineerName,
            String engineerPhone,
            String agencyName,
            String periodStart,
            String periodEnd,
            int completedCount,
            int grossAmount,
            BigDecimal platformFeeRate,
            int platformFee,
            BigDecimal agencyFeeRate,
            int agencyFee,
            int engineerNetAmount,
            String status,
            LocalDateTime settledAt,
            // payMethod / bankAccount: bank_accounts 테이블 미존재로 현재 null 반환
            // 추후 해당 테이블 추가 시 매핑 예정
            String payMethod,
            String bankAccount
    ) {}

    public static AgencySettlementListResponse of(Stats stats, Page<SettlementSummary> page) {
        return new AgencySettlementListResponse(
                stats,
                page.getContent(),
                page.getTotalElements(),
                page.getTotalPages(),
                page.getNumber(),
                page.getSize()
        );
    }
}
