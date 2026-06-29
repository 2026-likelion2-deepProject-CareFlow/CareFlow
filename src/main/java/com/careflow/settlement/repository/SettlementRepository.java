package com.careflow.settlement.repository;

import com.careflow.settlement.dto.EngineerSettlementSummary;
import com.careflow.settlement.entity.Settlement;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import org.springframework.data.jpa.repository.Modifying;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface SettlementRepository extends JpaRepository<Settlement, Long> {

    /**
     * 통합 테스트에서 paid_at 을 지정 일시로 직접 설정하기 위한 UPDATE 쿼리
     * (markPaid()는 현재 시각으로 설정되므로 테스트 월 범위 제어에 필요)
     */
    @Modifying
    @Transactional
    @Query("UPDATE Settlement s SET s.paidAt = :paidAt WHERE s.id = :settlementId")
    void updatePaidAt(@Param("settlementId") Long settlementId,
                      @Param("paidAt") LocalDateTime paidAt);

    /**
     * 특정 대행사의 해당 월 기사별 실적 집계
     * - status = 'PAID' + paid_at 범위 필터링
     * - engineer_id 기준 그룹핑하여 완료 건수 / 수령액 합산
     */
    @Query("""
            SELECT s.engineer.id AS engineerId,
                   s.engineer.name AS engineerName,
                   COUNT(s.id) AS completedCount,
                   SUM(s.engineerNetAmount) AS totalEarning
            FROM Settlement s
            WHERE s.agency.id = :agencyId
              AND s.status = 'PAID'
              AND s.paidAt >= :from
              AND s.paidAt < :to
            GROUP BY s.engineer.id, s.engineer.name
            """)
    List<EngineerSettlementSummary> findEngineerPerformance(
            @Param("agencyId") Long agencyId,
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to);

    /**
     * 특정 대행사의 해당 월 정산 합산 집계
     * - status = 'PAID' + paid_at 범위 필터링
     * - 전체 합산을 DB 레벨에서 한 번에 처리
     */
    @Query("""
            SELECT COUNT(s.id) AS totalCount,
                   COALESCE(SUM(s.grossAmount), 0) AS totalGrossAmount,
                   COALESCE(SUM(s.platformFee), 0) AS totalPlatformFee,
                   COALESCE(SUM(s.agencyFee), 0) AS totalAgencyFee,
                   COALESCE(SUM(s.engineerNetAmount), 0) AS totalEngineerPayout
            FROM Settlement s
            WHERE s.agency.id = :agencyId
              AND s.status = 'PAID'
              AND s.paidAt >= :from
              AND s.paidAt < :to
            """)
    MonthlySummaryProjection findMonthlySummary(
            @Param("agencyId") Long agencyId,
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to);

    /**
     * paid_at 기준 월 범위 내 대행사 정산 목록 전체 조회 (CSV 다운로드용)
     */
    @Query("""
            SELECT s FROM Settlement s
            WHERE s.agency.id = :agencyId
              AND s.status = 'PAID'
              AND s.paidAt >= :from
              AND s.paidAt < :to
            ORDER BY s.engineer.id, s.paidAt
            """)
    List<Settlement> findAllByAgencyAndMonth(
            @Param("agencyId") Long agencyId,
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to);

    /**
     * 특정 기사의 정산 내역 전체 조회 (최신순)
     * GET /api/agency/engineers/{id}/settlements 에서 사용
     */
    @Query("""
            SELECT s FROM Settlement s
            JOIN FETCH s.asRequest
            WHERE s.engineer.id = :engineerId
            ORDER BY s.createdAt DESC
            """)
    List<Settlement> findByEngineerIdWithRequest(@Param("engineerId") Long engineerId);

    /**
     * 합산 집계 결과를 받을 인터페이스 프로젝션
     */
    interface MonthlySummaryProjection {
        Long getTotalCount();
        Long getTotalGrossAmount();
        Long getTotalPlatformFee();
        Long getTotalAgencyFee();
        Long getTotalEngineerPayout();
    }
}
