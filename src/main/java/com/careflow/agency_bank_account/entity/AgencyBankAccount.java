package com.careflow.agency_bank_account.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 대행사 정산금 수취 계좌 정보
 * - CareFlow가 platform_settlements 지급 승인 시 이 계좌로 payout_amount_sum을 송금
 * - agency_id 기준 1:1 (UNIQUE)
 * - bank_accounts(기사 계좌)와 방향이 다름: 본 엔티티는 CareFlow→대행사, bank_accounts는 대행사→기사(참고용)
 */
@Entity
@Table(name = "agency_bank_accounts")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AgencyBankAccount {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "agency_bank_account_id")
    private Long id;

    // agencies.agency_id 참조, 1:1 UNIQUE — JPA 연관 대신 FK 컬럼 직접 보유 (BankAccount와 동일한 패턴)
    @Column(name = "agency_id", nullable = false, unique = true)
    private Long agencyId;

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
    public static AgencyBankAccount create(Long agencyId, String bankName,
                                           String accountNumber, String accountHolder) {
        AgencyBankAccount aba = new AgencyBankAccount();
        aba.agencyId      = agencyId;
        aba.bankName      = bankName;
        aba.accountNumber = accountNumber;
        aba.accountHolder = accountHolder;
        aba.payMethod     = PayMethod.BANK_TRANSFER;
        aba.createdAt     = LocalDateTime.now();
        aba.updatedAt     = LocalDateTime.now();
        return aba;
    }

    /** 계좌 정보 수정 — 대행사 관리자가 설정 화면에서 업데이트 */
    public void update(String bankName, String accountNumber, String accountHolder) {
        this.bankName      = bankName;
        this.accountNumber = accountNumber;
        this.accountHolder = accountHolder;
        this.updatedAt     = LocalDateTime.now();
    }

    /** 지급 응답용 포맷: "신한은행 110-123-456789" */
    public String formatBankAccount() {
        return bankName + " " + accountNumber;
    }
}
