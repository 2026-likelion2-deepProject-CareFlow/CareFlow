package com.careflow.agency_bank_account.repository;

import com.careflow.agency_bank_account.entity.AgencyBankAccount;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

public interface AgencyBankAccountRepository extends JpaRepository<AgencyBankAccount, Long> {

    /** 대행사 ID로 단건 조회 (1:1) */
    Optional<AgencyBankAccount> findByAgencyId(Long agencyId);
}
