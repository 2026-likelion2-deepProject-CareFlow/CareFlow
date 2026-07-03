package com.careflow.settlement.entity;

import com.careflow.agency.entity.Agencies;
import com.careflow.as_request.entity.AsRequest;
import com.careflow.payment.entity.Payment;
import com.careflow.user.entity.User;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(
        name = "settlements",
        indexes = {
                @Index(name = "idx_settlement_engineer", columnList = "engineer_id, status, created_at"),
                @Index(name = "idx_settlement_agency",   columnList = "agency_id, status, created_at"),
                @Index(name = "idx_settlement_status",   columnList = "status, created_at")
        }
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Settlement {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "settlement_id")
    private Long id;

    // 결제 정보 (1:1)
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "payment_id", nullable = false, unique = true)
    private Payment payment;

    // A/S 신청 정보
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "request_id", nullable = false)
    private AsRequest asRequest;

    // 담당 기사
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "engineer_id", nullable = false)
    private User engineer;

    // 소속 대행사
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "agency_id", nullable = false)
    private Agencies agency;

    // 작업 총 금액 (원)
    @Column(name = "gross_amount", nullable = false)
    private Integer grossAmount;

    // CareFlow 수수료 (원)
    @Column(name = "platform_fee", nullable = false)
    private Integer platformFee;

    // CareFlow 수수료율 스냅샷
    @Column(name = "fee_rate", nullable = false, precision = 5, scale = 2)
    private BigDecimal feeRate;

    // 대행사 수수료 (원)
    @Column(name = "agency_fee", nullable = false)
    private Integer agencyFee;

    // 대행사 수수료율 스냅샷
    @Column(name = "agency_fee_rate", nullable = false, precision = 5, scale = 2)
    private BigDecimal agencyFeeRate;

    // 기사 실수령액 (원)
    @Column(name = "engineer_net_amount", nullable = false)
    private Integer engineerNetAmount;

    // columnDefinition 삭제하지 말아주세요(H2 DB 테스트에 필요)
    // [DDL v11] APPROVED 제거 — 월초 일괄 승인 단계가 상태 전이 없이 바로 지급으로 이어져 실익 없음
    @Column(name = "status", nullable = false, length = 20,
            columnDefinition = "ENUM('PENDING','PAID','DISPUTED') DEFAULT 'PENDING'")
    private String status = "PENDING";

    // [DDL v11 신규] 이 건의 platform_fee가 집계된 월별 대행사→플랫폼 정산 (집계 전 NULL)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "platform_settlement_id", nullable = true)
    private PlatformSettlement platformSettlement;

    @Column(name = "paid_at")
    private LocalDateTime paidAt;

    // columnDefinition 삭제하지 말아주세요(H2 DB 테스트에 필요)
    @Column(name = "created_at", nullable = false, updatable = false,
            columnDefinition = "DATETIME DEFAULT CURRENT_TIMESTAMP")
    private LocalDateTime createdAt = LocalDateTime.now();

    @Builder
    public Settlement(Payment payment, AsRequest asRequest, User engineer, Agencies agency,
                      Integer grossAmount, Integer platformFee, BigDecimal feeRate,
                      Integer agencyFee, BigDecimal agencyFeeRate, Integer engineerNetAmount) {
        this.payment = payment;
        this.asRequest = asRequest;
        this.engineer = engineer;
        this.agency = agency;
        this.grossAmount = grossAmount;
        this.platformFee = platformFee;
        this.feeRate = feeRate;
        this.agencyFee = agencyFee;
        this.agencyFeeRate = agencyFeeRate;
        this.engineerNetAmount = engineerNetAmount;
        this.status = "PENDING";
        this.createdAt = LocalDateTime.now();
    }

    public static Settlement create(Payment payment, AsRequest asRequest, User engineer,
                                    Agencies agency, Integer grossAmount, Integer platformFee,
                                    BigDecimal feeRate, Integer agencyFee, BigDecimal agencyFeeRate,
                                    Integer engineerNetAmount) {
        return Settlement.builder()
                .payment(payment)
                .asRequest(asRequest)
                .engineer(engineer)
                .agency(agency)
                .grossAmount(grossAmount)
                .platformFee(platformFee)
                .feeRate(feeRate)
                .agencyFee(agencyFee)
                .agencyFeeRate(agencyFeeRate)
                .engineerNetAmount(engineerNetAmount)
                .build();
    }

    // 기사 지급 완료 처리 — [DDL v11] PENDING/DISPUTED 상태에서 바로 호출 가능(APPROVED 경유 불필요), 더티 체킹으로 UPDATE
    public void markPaid() {
        this.status = "PAID";
        this.paidAt = LocalDateTime.now();
    }

    // 보류 처리 — 더티 체킹으로 UPDATE
    public void dispute() {
        this.status = "DISPUTED";
    }

    // 지급 대기로 복귀 (DISPUTED → PENDING 재검토) — 더티 체킹으로 UPDATE
    public void revertToPending() {
        this.status = "PENDING";
    }

    // [DDL v11 신규] 월초 배치 Job이 platform_settlements 1건을 생성하며 이 건의 집계 소속을 채움
    public void assignPlatformSettlement(PlatformSettlement platformSettlement) {
        this.platformSettlement = platformSettlement;
    }
}
