package com.careflow.bank_account.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 기사(엔지니어) 정산금 지급 계좌 정보
 * - 대행사 관리자가 기사 프로필 수정 화면에서 등록/수정
 * - engineer_id 기준 1:1 (UNIQUE)
 * - payments.pg_provider(고객→플랫폼 결제)와 무관, 플랫폼/대행사→기사 정산금 지급 방향
 */
@Entity
@Table(name = "bank_accounts")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class BankAccount {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "bank_account_id")
    private Long id;

    // users.user_id 참조 (기사), 1:1 UNIQUE — JPA 연관 대신 FK 컬럼 직접 보유
    @Column(name = "engineer_id", nullable = false, unique = true)
    private Long engineerId;

    @Column(name = "bank_name", nullable = false, length = 50)
    private String bankName;

    @Column(name = "account_number", nullable = false, length = 50)
    private String accountNumber;

    @Column(name = "account_holder", nullable = false, length = 50)
    private String accountHolder;

    // 현재 계좌이체 단일 지원, 추후 확장 대비 ENUM화
    @Enumerated(EnumType.STRING)
    @Column(name = "pay_method", nullable = false,
            columnDefinition = "ENUM('BANK_TRANSFER') DEFAULT 'BANK_TRANSFER'")
    private PayMethod payMethod;

    // columnDefinition 삭제하지 말아주세요(H2 DB 테스트에 필요)
    @Column(name = "created_at", nullable = false, updatable = false,
            columnDefinition = "DATETIME DEFAULT CURRENT_TIMESTAMP")
    private LocalDateTime createdAt;

    // columnDefinition 삭제하지 말아주세요(H2 DB 테스트에 필요)
    @Column(name = "updated_at", nullable = false,
            columnDefinition = "DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP")
    private LocalDateTime updatedAt;

    public enum PayMethod {
        BANK_TRANSFER;

        /** UI 표시용 한글 레이블 */
        public String toLabel() {
            return "계좌이체";
        }
    }

    /**
     * 계좌 정보 신규 등록 팩토리 메서드
     * pay_method 는 현재 BANK_TRANSFER 고정
     */
    public static BankAccount create(Long engineerId, String bankName,
                                     String accountNumber, String accountHolder) {
        BankAccount ba = new BankAccount();
        ba.engineerId    = engineerId;
        ba.bankName      = bankName;
        ba.accountNumber = accountNumber;
        ba.accountHolder = accountHolder;
        ba.payMethod     = PayMethod.BANK_TRANSFER;
        ba.createdAt     = LocalDateTime.now();
        ba.updatedAt     = LocalDateTime.now();
        return ba;
    }

    /** 계좌 정보 수정 — 대행사 관리자가 기사 프로필 화면에서 업데이트 */
    public void update(String bankName, String accountNumber, String accountHolder) {
        this.bankName      = bankName;
        this.accountNumber = accountNumber;
        this.accountHolder = accountHolder;
        this.updatedAt     = LocalDateTime.now();
    }

    /** 정산 응답용 포맷: "신한은행 110-123-456789" */
    public String formatBankAccount() {
        return bankName + " " + accountNumber;
    }
}
