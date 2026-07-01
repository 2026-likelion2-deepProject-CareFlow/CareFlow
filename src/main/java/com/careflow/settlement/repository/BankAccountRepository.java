// 파일 경로: src/main/java/com/careflow/settlement/repository/BankAccountRepository.java
package com.careflow.settlement.repository;

import com.careflow.settlement.entity.BankAccount;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface BankAccountRepository extends JpaRepository<BankAccount, Long> {
    Optional<BankAccount> findByEngineer_Id(Long engineerId);
}