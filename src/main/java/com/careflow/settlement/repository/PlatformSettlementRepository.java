package com.careflow.settlement.repository;

import com.careflow.settlement.entity.PlatformSettlement;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PlatformSettlementRepository extends JpaRepository<PlatformSettlement, Long> {

    // 대행사 + 정산 연월 단위 조회 (uk_platform_settlement_period와 동일 키)
    Optional<PlatformSettlement> findByAgency_IdAndSettlementYearAndSettlementMonth(
            Long agencyId, Integer settlementYear, Integer settlementMonth);

    // 해당 연월의 미지급(PAID가 아닌) 배치 전체 조회 — ADMIN 일괄 지급 승인 대상
    List<PlatformSettlement> findBySettlementYearAndSettlementMonthAndStatusNot(
            Integer settlementYear, Integer settlementMonth, String status);

    // 해당 연월 전체 배치 조회(상태 무관) — ADMIN 월별 요약 화면에서 대행사별 실제 배치 상태/지급 계좌 노출용
    List<PlatformSettlement> findBySettlementYearAndSettlementMonth(
            Integer settlementYear, Integer settlementMonth);
}
