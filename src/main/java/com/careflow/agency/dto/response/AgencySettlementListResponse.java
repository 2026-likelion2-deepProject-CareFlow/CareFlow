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

    /**
     * thisMonthGrossAmount/paidAmount/pendingAmount/disputedAmount/thisMonthCount:
     *   현재 조회 필터(status/dateFrom/dateTo/keyword) 적용 결과 전체 기준 집계. 필터 미지정 시 전체 기간 기준.
     *   (필드명은 프론트 하위 호환을 위해 유지하나, 필터가 걸린 경우 "이번 달"이 아닌 "필터링된 기간"을 의미함)
     * prevMonthCountDiff/prevMonthGrossDiff:
     *   필터가 전혀 없을 때(=이번 달 뷰)만 "이번 달 대비 전월" 차이를 계산해 내려주고, 필터가 하나라도 걸려 있으면 null.
     */
    public record Stats(
            long thisMonthGrossAmount,
            long paidAmount,
            long pendingAmount,
            long disputedAmount,
            long thisMonthCount,
            Long prevMonthCountDiff,
            Long prevMonthGrossDiff
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
            String status,                // [E 수정] CareFlow→대행사 지급 상태 (settlements.status) — ADMIN 배치 승인으로만 PAID 전이
            LocalDateTime calculatedAt,   // 정산 레코드가 계산/생성된 시각 — 상태와 무관하게 항상 존재
            LocalDateTime paidAt,         // CareFlow→대행사 지급 완료 일시 — null이면 미지급(PENDING/DISPUTED)
            // payMethod / bankAccount: bank_accounts 테이블 미존재로 현재 null 반환
            // 추후 해당 테이블 추가 시 매핑 예정
            String payMethod,
            String bankAccount,
            // [engineer_payouts 신규] 대행사→기사 지급 상태 — settings.status와 별개의 자금 흐름.
            // engineerPayoutId가 null이면 아직 이 건이 월별 지급 배치에 집계되지 않은 것(정상적으로는 발생 안 함).
            Long engineerPayoutId,
            String engineerPayoutStatus,   // PENDING / PAID / DISPUTED
            LocalDateTime engineerPayoutPaidAt
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
