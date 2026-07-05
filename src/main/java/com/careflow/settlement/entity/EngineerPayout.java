package com.careflow.settlement.entity;

import com.careflow.agency.entity.Agencies;
import com.careflow.user.entity.User;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * [DDL v14 신규] 대행사→기사 지급 (일괄 지급) — 월 단위 집계 배치 테이블
 * platform_settlements(CareFlow→대행사)와 대칭 구조이나, 그레인이 한 단계 더 세분화됨:
 * (agency_id, engineer_id, payout_year, payout_month) 당 1행 — 대행사가 여러 기사에게 각각 나눠 지급하기 때문.
 * CareFlow는 이 자금을 소유·집행하지 않는다(기사 급여 지급은 대행사 자체 책임) — 그래서 platform_settlements와
 * 달리 지급 계좌 스냅샷(paid_bank_account_id 상당)이 없다. 그럼에도 이 테이블이 존재하는 이유는, 기사가
 * "급여를 못 받았다"는 분쟁을 제기할 때 CareFlow가 최소한의 조정자 역할을 하려면 "대행사가 스스로 PAID라고
 * 표시했는지"에 대한 기록이 필요하기 때문.
 */
@Entity
@Table(
        name = "engineer_payouts",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_engineer_payout_period",
                        columnNames = {"agency_id", "engineer_id", "payout_year", "payout_month"})
        },
        indexes = {
                @Index(name = "idx_engineer_payout_status", columnList = "status, payout_year, payout_month")
        }
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class EngineerPayout {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "engineer_payout_id")
    private Long id;

    // 지급 주체 대행사
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "agency_id", nullable = false)
    private Agencies agency;

    // 수취 기사
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "engineer_id", nullable = false)
    private User engineer;

    // 지급 대상 연도 (집계 대상 settlements.created_at 기준)
    @Column(name = "payout_year", nullable = false, columnDefinition = "YEAR")
    private Integer payoutYear;

    // 지급 대상 월 (1~12)
    @Column(name = "payout_month", nullable = false)
    private Integer payoutMonth;

    // 집계 대상 settlements.engineer_net_amount 합계 (원)
    @Column(name = "net_amount_sum", nullable = false)
    private Integer netAmountSum;

    // 집계된 settlements 건수
    @Column(name = "case_count", nullable = false)
    private Integer caseCount = 0;

    // columnDefinition 삭제하지 말아주세요(H2 DB 테스트에 필요)
    @Column(name = "status", nullable = false, length = 20,
            columnDefinition = "ENUM('PENDING','PAID','DISPUTED') DEFAULT 'PENDING'")
    private String status = "PENDING";

    // 이 지급의 재원이 된 CareFlow→대행사 정산 배치 (참고·감사용, 선행 조건 아님, 독립적으로 채워짐)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "platform_settlement_id", nullable = true)
    private PlatformSettlement platformSettlement;

    // 대행사→기사 지급 완료 일시
    @Column(name = "paid_at")
    private LocalDateTime paidAt;

    // columnDefinition 삭제하지 말아주세요(H2 DB 테스트에 필요)
    @Column(name = "created_at", nullable = false, updatable = false,
            columnDefinition = "DATETIME DEFAULT CURRENT_TIMESTAMP")
    private LocalDateTime createdAt = LocalDateTime.now();

    @Builder
    public EngineerPayout(Agencies agency, User engineer, Integer payoutYear, Integer payoutMonth,
                          Integer netAmountSum, Integer caseCount) {
        this.agency = agency;
        this.engineer = engineer;
        this.payoutYear = payoutYear;
        this.payoutMonth = payoutMonth;
        this.netAmountSum = netAmountSum;
        this.caseCount = caseCount != null ? caseCount : 0;
        this.status = "PENDING";
        this.createdAt = LocalDateTime.now();
    }

    public static EngineerPayout create(Agencies agency, User engineer, Integer payoutYear, Integer payoutMonth,
                                        Integer netAmountSum, Integer caseCount) {
        return EngineerPayout.builder()
                .agency(agency)
                .engineer(engineer)
                .payoutYear(payoutYear)
                .payoutMonth(payoutMonth)
                .netAmountSum(netAmountSum)
                .caseCount(caseCount)
                .build();
    }

    // [배치 재실행 대비] 동일 기간(agency+engineer+year+month)에 대해 추가로 집계된 건을 기존 합계에 누적 — 더티 체킹으로 UPDATE
    public void accumulate(int additionalNetAmount, int additionalCount) {
        this.netAmountSum += additionalNetAmount;
        this.caseCount += additionalCount;
    }

    // 대행사→기사 지급 완료 처리 (대행사가 직접 실행) — 더티 체킹으로 UPDATE
    public void markPaid() {
        this.status = "PAID";
        this.paidAt = LocalDateTime.now();
    }

    // 기사·대행사 간 분쟁 발생 시 보류 처리 — 더티 체킹으로 UPDATE
    public void dispute() {
        this.status = "DISPUTED";
    }

    // 지급 대기로 복귀 (DISPUTED → PENDING 재검토) — 더티 체킹으로 UPDATE
    public void revertToPending() {
        this.status = "PENDING";
    }

    // 이 배치의 재원이 된 CareFlow→대행사 정산 배치 참고 링크 설정 (감사용, 선택적)
    public void assignPlatformSettlement(PlatformSettlement platformSettlement) {
        this.platformSettlement = platformSettlement;
    }
}
