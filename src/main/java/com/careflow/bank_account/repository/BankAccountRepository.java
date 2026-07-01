package com.careflow.bank_account.repository;

import com.careflow.bank_account.entity.BankAccount;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

public interface BankAccountRepository extends JpaRepository<BankAccount, Long> {

    /** 기사 ID로 단건 조회 (1:1) */
    Optional<BankAccount> findByEngineerId(Long engineerId);

    /** 기사 ID 목록 일괄 조회 — 정산 목록 응답 시 N+1 방지용 배치 로드 */
    List<BankAccount> findByEngineerIdIn(List<Long> engineerIds);
}
