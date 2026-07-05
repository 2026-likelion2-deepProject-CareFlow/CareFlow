package com.careflow.settlement.repository;

import com.careflow.settlement.entity.EngineerPayout;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface EngineerPayoutRepository extends JpaRepository<EngineerPayout, Long> {

    // 대행사 + 기사 + 지급 연월 단위 조회 (uk_engineer_payout_period와 동일 키)
    Optional<EngineerPayout> findByAgency_IdAndEngineer_IdAndPayoutYearAndPayoutMonth(
            Long agencyId, Long engineerId, Integer payoutYear, Integer payoutMonth);

    // 대행사 기준 지급 배치 목록 페이징 조회 (연월 필터)
    Page<EngineerPayout> findByAgency_IdAndPayoutYearAndPayoutMonth(
            Long agencyId, Integer payoutYear, Integer payoutMonth, Pageable pageable);

    // 기사 본인의 지급 배치 이력 페이징 조회
    Page<EngineerPayout> findByEngineer_IdOrderByPayoutYearDescPayoutMonthDesc(Long engineerId, Pageable pageable);

    // ADMIN 분쟁 조정용 — 해당 연월 전체 대행사 대상 배치 조회, status가 null이면 전체
    @Query("SELECT ep FROM EngineerPayout ep " +
           "WHERE ep.payoutYear = :year AND ep.payoutMonth = :month " +
           "AND (:status IS NULL OR ep.status = :status) " +
           "ORDER BY ep.agency.id, ep.engineer.id")
    List<EngineerPayout> findAllByPeriodAndOptionalStatus(
            @Param("year") Integer year, @Param("month") Integer month, @Param("status") String status);
}
