package com.careflow.settlement.entity;

import com.careflow.agency.entity.Agencies;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * [DDL v11 신규] 대행사→플랫폼 정산 (수수료 납부) — 월 단위 집계 배치 테이블
 * settlements(기사·대행사 정산)는 A/S 신청 1건당 1행이지만,
 * 대행사→플랫폼 정산은 "해당 대행사의 지난 달 platform_fee 합계"라는
 * 월 단위 집계 그 자체이므로 agency_id + settlement_year + settlement_month 당 1행으로 관리한다.
 */
@Entity
@Table(
        name = "platform_settlements",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_platform_settlement_period",
                        columnNames = {"agency_id", "settlement_year", "settlement_month"})
        },
        indexes = {
                @Index(name = "idx_platform_settlement_status", columnList = "status, settlement_year, settlement_month")
        }
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PlatformSettlement {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "platform_settlement_id")
    private Long id;

    // 대행사
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "agency_id", nullable = false)
    private Agencies agency;

    // 정산 대상 연도 (집계 대상 settlements.paid_at 기준)
    @Column(name = "settlement_year", nullable = false, columnDefinition = "YEAR")
    private Integer settlementYear;

    // 정산 대상 월 (1~12)
    @Column(name = "settlement_month", nullable = false)
    private Integer settlementMonth;

    // 집계 대상 settlements.gross_amount 합계 (원) — 검증·감사용
    @Column(name = "total_gross_amount", nullable = false)
    private Integer totalGrossAmount;

    // 집계 대상 settlements.platform_fee 합계 (원) — 대행사가 플랫폼에 납부할 금액
    @Column(name = "total_platform_fee", nullable = false)
    private Integer totalPlatformFee;

    // 집계된 settlements 건수
    @Column(name = "settlement_count", nullable = false)
    private Integer settlementCount = 0;

    // columnDefinition 삭제하지 말아주세요(H2 DB 테스트에 필요)
    // APPROVED 없음 — settlements와 동일 사유 ([DDL v11] 월초 일괄 승인 단계가 상태 전이 없이 바로 지급으로 이어짐)
    @Column(name = "status", nullable = false, length = 20,
            columnDefinition = "ENUM('PENDING','PAID','DISPUTED') DEFAULT 'PENDING'")
    private String status = "PENDING";

    // 플랫폼 수수료 납부 완료 일시
    @Column(name = "paid_at")
    private LocalDateTime paidAt;

    // columnDefinition 삭제하지 말아주세요(H2 DB 테스트에 필요)
    @Column(name = "created_at", nullable = false, updatable = false,
            columnDefinition = "DATETIME DEFAULT CURRENT_TIMESTAMP")
    private LocalDateTime createdAt = LocalDateTime.now();

    @Builder
    public PlatformSettlement(Agencies agency, Integer settlementYear, Integer settlementMonth,
                              Integer totalGrossAmount, Integer totalPlatformFee, Integer settlementCount) {
        this.agency = agency;
        this.settlementYear = settlementYear;
        this.settlementMonth = settlementMonth;
        this.totalGrossAmount = totalGrossAmount;
        this.totalPlatformFee = totalPlatformFee;
        this.settlementCount = settlementCount != null ? settlementCount : 0;
        this.status = "PENDING";
        this.createdAt = LocalDateTime.now();
    }

    public static PlatformSettlement create(Agencies agency, Integer settlementYear, Integer settlementMonth,
                                             Integer totalGrossAmount, Integer totalPlatformFee, Integer settlementCount) {
        return PlatformSettlement.builder()
                .agency(agency)
                .settlementYear(settlementYear)
                .settlementMonth(settlementMonth)
                .totalGrossAmount(totalGrossAmount)
                .totalPlatformFee(totalPlatformFee)
                .settlementCount(settlementCount)
                .build();
    }

    // 플랫폼 수수료 납부 완료 처리 — 더티 체킹으로 UPDATE
    public void markPaid() {
        this.status = "PAID";
        this.paidAt = LocalDateTime.now();
    }

    // 집계 오류 등으로 인한 보류 처리 — 더티 체킹으로 UPDATE
    public void dispute() {
        this.status = "DISPUTED";
    }

    // 지급 대기로 복귀 (DISPUTED → PENDING 재검토) — 더티 체킹으로 UPDATE
    public void revertToPending() {
        this.status = "PENDING";
    }
}
