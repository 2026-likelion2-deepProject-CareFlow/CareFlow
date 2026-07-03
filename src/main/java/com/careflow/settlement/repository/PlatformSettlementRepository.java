package com.careflow.settlement.repository;

import com.careflow.settlement.entity.PlatformSettlement;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PlatformSettlementRepository extends JpaRepository<PlatformSettlement, Long> {

    // 대행사 + 정산 연월 단위 조회 (uk_platform_settlement_period와 동일 키)
    Optional<PlatformSettlement> findByAgency_IdAndSettlementYearAndSettlementMonth(
            Long agencyId, Integer settlementYear, Integer settlementMonth);
}
