package com.careflow.payment.entity;

import com.careflow.as_request.entity.AsRequest;
import com.careflow.common.enums.PgProvider;
import com.careflow.common.enums.PaymentStatus;
import com.careflow.user.entity.User;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "payments", uniqueConstraints = {
        @UniqueConstraint(name = "uk_payment_request", columnNames = "request_id"),
        @UniqueConstraint(name = "uk_payment_pg_txid", columnNames = "pg_transaction_id")
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Payment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "payment_id")
    private Long id;

    // A/S 요청 (1:1 관계 — uk_payment_request 제약으로 유일성 보장)
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "request_id", nullable = false)
    private AsRequest asRequest;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "customer_id", nullable = false)
    private User customer;

    @Column(nullable = false)
    private Integer amount;

    @Enumerated(EnumType.STRING)
    @Column(name = "pg_provider", nullable = false, length = 10,
            columnDefinition = "ENUM('MOCK','KAKAO','TOSS','NAVER') DEFAULT 'MOCK'")
    private PgProvider pgProvider = PgProvider.MOCK;

    @Column(name = "pg_transaction_id", length = 100)
    private String pgTransactionId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20,
            columnDefinition = "ENUM('READY','SUCCESS','FAILED','CANCELLED','REFUNDED') DEFAULT 'READY'")
    private PaymentStatus status = PaymentStatus.READY;

    @Column(name = "paid_at")
    private LocalDateTime paidAt;

    @Column(name = "cancelled_at")
    private LocalDateTime cancelledAt;

    // columnDefinition 삭제하지 말아주세요(H2 DB 테스트에 필요)
    @Column(name = "created_at", nullable = false, updatable = false,
            columnDefinition = "DATETIME DEFAULT CURRENT_TIMESTAMP")
    private LocalDateTime createdAt = LocalDateTime.now();

    @Builder
    public Payment(AsRequest asRequest, User customer, Integer amount) {
        this.asRequest = asRequest;
        this.customer = customer;
        this.amount = amount;
        this.pgProvider = PgProvider.MOCK;
        this.status = PaymentStatus.READY;
        this.createdAt = LocalDateTime.now();
    }

    /** READY 상태로 Payment row 생성 */
    public static Payment create(AsRequest asRequest, User customer, Integer amount) {
        return Payment.builder()
                .asRequest(asRequest)
                .customer(customer)
                .amount(amount)
                .build();
    }

    /** 모의(MOCK) 결제 즉시 SUCCESS 처리 — PG 호출 없이 바로 성공으로 전환 */
    public void markSuccess() {
        this.status = PaymentStatus.SUCCESS;
        this.pgProvider = PgProvider.MOCK;
        this.paidAt = LocalDateTime.now();
    }
}
