package com.careflow.settlement.dto;

import com.careflow.settlement.entity.Settlement;
import org.springframework.data.domain.Page;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * GET /api/agency/settlements/engineers/performance 응답 DTO
 *
 * 대행사 소속 기사들의 개별 정산 내역 목록 (페이징 + 동적 필터 지원)
 */
public record EngineerSettlementListResponse(
        int year,
        int month,
        List<SettlementRow> settlements,
        long totalElements,
        int totalPages,
        int currentPage,
        int size
) {

    private static final DateTimeFormatter PERIOD_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    public static EngineerSettlementListResponse of(int year, int month, Page<Settlement> page) {
        List<SettlementRow> rows = page.getContent().stream()
                .map(EngineerSettlementListResponse::toRow)
                .toList();

        return new EngineerSettlementListResponse(
                year,
                month,
                rows,
                page.getTotalElements(),
                page.getTotalPages(),
                page.getNumber(),
                page.getSize()
        );
    }

    private static SettlementRow toRow(Settlement s) {
        // periodStart / periodEnd: createdAt 기준 해당 월 1일 ~ 말일로 파생
        YearMonth month = YearMonth.from(s.getCreatedAt());
        String periodStart = month.atDay(1).format(PERIOD_FMT);
        String periodEnd   = month.atEndOfMonth().format(PERIOD_FMT);

        return new SettlementRow(
                s.getId(),
                "ENGINEER",
                s.getEngineer().getId(),
                s.getEngineer().getName(),
                s.getEngineer().getPhone(),
                s.getAgency().getAgencyName(),
                periodStart,
                periodEnd,
                s.getGrossAmount(),
                s.getFeeRate(),
                s.getPlatformFee(),
                s.getAgencyFeeRate(),
                s.getAgencyFee(),
                s.getEngineerNetAmount(),
                s.getStatus(),
                s.getCreatedAt(),
                s.getPaidAt()
        );
    }

    /**
     * 정산 목록의 개별 행 — UI 테이블 1행에 대응
     */
    public record SettlementRow(
            Long settlementId,
            String type,
            Long engineerId,
            String engineerName,
            String engineerPhone,
            String agencyName,
            String periodStart,
            String periodEnd,
            int grossAmount,
            BigDecimal platformFeeRate,
            int platformFee,
            BigDecimal agencyFeeRate,
            int agencyFee,
            int engineerNetAmount,
            String status,
            LocalDateTime calculatedAt,   // 정산 레코드가 계산/생성된 시각 — 상태와 무관하게 항상 존재
            LocalDateTime paidAt          // 기사에게 실제 지급 완료된 일시 — null이면 미지급(PENDING/DISPUTED)
    ) {}
}
