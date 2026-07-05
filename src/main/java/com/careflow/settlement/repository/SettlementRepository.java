package com.careflow.settlement.repository;

import com.careflow.common.enums.DiagnosisResult;
import com.careflow.settlement.dto.EngineerSettlementSummary;
import com.careflow.settlement.entity.Settlement;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
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
     * ADMIN이 platform_settlement 1건을 지급 승인 처리할 때, 그 배치에 묶인 하위 Settlement 중
     * PENDING 상태인 건만 일괄 PAID로 전이한다 (건별 조회 후 markPaid() 대신 벌크 UPDATE로 처리 — 대상이 많을 수 있음).
     * DISPUTED로 보류 중인 건은 관리자가 개별적으로 재검토(PENDING 복귀) 후 다시 승인해야 하므로 제외한다.
     */
    @Modifying
    @Transactional
    @Query("UPDATE Settlement s SET s.status = 'PAID', s.paidAt = :paidAt WHERE s.platformSettlement.id = :platformSettlementId AND s.status = 'PENDING'")
    int markPaidByPlatformSettlementId(@Param("platformSettlementId") Long platformSettlementId,
                                        @Param("paidAt") LocalDateTime paidAt);

    /**
     * 통합 테스트에서 created_at 을 지정 일시로 직접 설정하기 위한 UPDATE 쿼리
     * (findMonthlySummary 는 createdAt 기준으로 월 범위를 필터링하므로 테스트 제어에 필요)
     */
    @Modifying
    @Transactional
    @Query("UPDATE Settlement s SET s.createdAt = :createdAt WHERE s.id = :settlementId")
    void updateCreatedAt(@Param("settlementId") Long settlementId,
                         @Param("createdAt") LocalDateTime createdAt);

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
     * - createdAt 범위 기준으로 전체 status 포함 집계 ([DDL v11] PENDING/PAID/DISPUTED 모두, APPROVED 제거)
     * - CASE WHEN 으로 상태별 gross 합산 분리 (UI 통계카드 대응)
     * - 전체 합산을 DB 레벨에서 한 번에 처리
     */
    @Query("""
            SELECT COUNT(s.id) AS totalCount,
                   COALESCE(SUM(s.grossAmount), 0) AS totalGrossAmount,
                   COALESCE(SUM(CASE WHEN s.status = 'PAID' THEN s.grossAmount ELSE 0 END), 0) AS paidAmount,
                   COALESCE(SUM(CASE WHEN s.status = 'PENDING' THEN s.grossAmount ELSE 0 END), 0) AS pendingAmount,
                   COALESCE(SUM(CASE WHEN s.status = 'DISPUTED' THEN s.grossAmount ELSE 0 END), 0) AS disputedAmount,
                   COALESCE(SUM(s.platformFee), 0) AS totalPlatformFee,
                   COALESCE(SUM(s.agencyFee), 0) AS totalAgencyFee,
                   COALESCE(SUM(s.engineerNetAmount), 0) AS totalEngineerPayout
            FROM Settlement s
            WHERE s.agency.id = :agencyId
              AND s.createdAt >= :from
              AND s.createdAt < :to
            """)
    MonthlySummaryProjection findMonthlySummary(
            @Param("agencyId") Long agencyId,
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to);

    /**
     * 특정 기사(engineer)의 해당 기간 정산 합산 집계 — 기사 대시보드 실적/정산 요약(GET /api/engineer/settlements/summary)용.
     * - createdAt 범위 기준, 전체 status 포함([DDL v11] PENDING/PAID/DISPUTED, APPROVED 제거)
     * - settlements 테이블에 저장된 실제 스냅샷 컬럼(grossAmount·platformFee·agencyFee·engineerNetAmount)을 그대로 합산
     *   → 하드코딩 수수료율(10%/5%) 대신 정산 시점의 실제 값을 사용하며, net = gross - platform - agency 로 정합.
     * - agency 용 findMonthlySummary 와 동일한 MonthlySummaryProjection 을 재사용(모수만 engineer 로 교체).
     */
    /**
     * 특정 기사(engineer)의 해당 기간 정산 합산 집계 (+ 동적 필터 추가)
     */
    @Query("""
            SELECT COUNT(s.id) AS totalCount,
                   COALESCE(SUM(s.grossAmount), 0) AS totalGrossAmount,
                   COALESCE(SUM(CASE WHEN s.status = 'PAID' THEN s.grossAmount ELSE 0 END), 0) AS paidAmount,
                   COALESCE(SUM(CASE WHEN s.status = 'PENDING' THEN s.grossAmount ELSE 0 END), 0) AS pendingAmount,
                   COALESCE(SUM(CASE WHEN s.status = 'DISPUTED' THEN s.grossAmount ELSE 0 END), 0) AS disputedAmount,
                   COALESCE(SUM(s.platformFee), 0) AS totalPlatformFee,
                   COALESCE(SUM(s.agencyFee), 0) AS totalAgencyFee,
                   COALESCE(SUM(s.engineerNetAmount), 0) AS totalEngineerPayout
            FROM Settlement s
            JOIN s.asRequest r
            JOIN r.appliance app
            LEFT JOIN r.workReport w
            WHERE s.engineer.id = :engineerId
              AND s.createdAt >= :from
              AND s.createdAt < :to
              AND (:brand IS NULL OR app.brand = :brand)
              AND (:status IS NULL OR w.diagnosisResult = :status)
            """)
    MonthlySummaryProjection findEngineerMonthlySummary(
            @Param("engineerId") Long engineerId,
            @Param("from") java.time.LocalDateTime from,
            @Param("to") java.time.LocalDateTime to,
            @Param("brand") String brand,
            @Param("status") DiagnosisResult status);

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

    // ─────────────────────────────────────────────────────────────
    // GET /api/agency/settlements 전용 쿼리
    // ─────────────────────────────────────────────────────────────

    /**
     * 대행사 정산 목록 조회 (필터 + 페이징)
     * - JOIN FETCH로 N+1 방지 (engineer, agency)
     * - status / dateFrom / dateTo 조건 선택적 적용
     * - keyword 분기:
     *   - nameKeyword: 기사명 부분 일치 (LIKE) — 숫자가 아닌 문자열 입력 시
     *   - settlementId: 정산 ID 정확히 일치 — 숫자 문자열 입력 시
     *   (둘 다 null이면 조건 미적용)
     * - createdAt DESC 정렬
     */
    @Query("""
            SELECT s FROM Settlement s
            JOIN FETCH s.engineer
            JOIN FETCH s.agency
            WHERE s.agency.id = :agencyId
              AND (:status IS NULL OR s.status = :status)
              AND (:dateFrom IS NULL OR s.createdAt >= :dateFrom)
              AND (:dateTo IS NULL OR s.createdAt < :dateTo)
              AND (:nameKeyword IS NULL OR s.engineer.name LIKE %:nameKeyword%)
              AND (:settlementId IS NULL OR s.id = :settlementId)
            ORDER BY s.createdAt DESC
            """)
    Page<Settlement> findAgencySettlements(
            @Param("agencyId") Long agencyId,
            @Param("status") String status,
            @Param("dateFrom") LocalDateTime dateFrom,
            @Param("dateTo") LocalDateTime dateTo,
            @Param("nameKeyword") String nameKeyword,
            @Param("settlementId") Long settlementId,
            Pageable pageable);

    /**
     * 대행사 정산 통계 집계 (stats 용, 특정 기간)
     * - 전체 건수 / 총 gross_amount / PAID 합계 / PENDING 합계 / DISPUTED 합계 ([DDL v11] APPROVED 제거)
     * - result.get(0): Object[] { count, totalGross, paidGross, pendingGross, disputedGross }
     */
    @Query("""
            SELECT COUNT(s),
                   COALESCE(SUM(s.grossAmount), 0L),
                   COALESCE(SUM(CASE WHEN s.status = 'PAID'     THEN s.grossAmount ELSE 0 END), 0L),
                   COALESCE(SUM(CASE WHEN s.status = 'PENDING'  THEN s.grossAmount ELSE 0 END), 0L),
                   COALESCE(SUM(CASE WHEN s.status = 'DISPUTED' THEN s.grossAmount ELSE 0 END), 0L)
            FROM Settlement s
            WHERE s.agency.id = :agencyId
              AND s.createdAt >= :from
              AND s.createdAt < :to
            """)
    List<Object[]> findAgencySettlementStatsByPeriod(
            @Param("agencyId") Long agencyId,
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to);

    /**
     * 대행사 정산 통계 집계 (stats 용, 현재 조회 필터 기준)
     * - findAgencySettlements(목록 조회)와 동일한 WHERE 조건을 사용해 "현재 필터로 조회된 결과 전체"와 stats가 항상 일치하도록 함
     * - 필터 파라미터가 모두 null이면 전체 기간 집계
     * - result.get(0): Object[] { count, totalGross, paidGross, pendingGross, disputedGross }
     */
    @Query("""
            SELECT COUNT(s),
                   COALESCE(SUM(s.grossAmount), 0L),
                   COALESCE(SUM(CASE WHEN s.status = 'PAID'     THEN s.grossAmount ELSE 0 END), 0L),
                   COALESCE(SUM(CASE WHEN s.status = 'PENDING'  THEN s.grossAmount ELSE 0 END), 0L),
                   COALESCE(SUM(CASE WHEN s.status = 'DISPUTED' THEN s.grossAmount ELSE 0 END), 0L)
            FROM Settlement s
            WHERE s.agency.id = :agencyId
              AND (:status IS NULL OR s.status = :status)
              AND (:dateFrom IS NULL OR s.createdAt >= :dateFrom)
              AND (:dateTo IS NULL OR s.createdAt < :dateTo)
              AND (:nameKeyword IS NULL OR s.engineer.name LIKE %:nameKeyword%)
              AND (:settlementId IS NULL OR s.id = :settlementId)
            """)
    List<Object[]> findAgencySettlementStatsByFilter(
            @Param("agencyId") Long agencyId,
            @Param("status") String status,
            @Param("dateFrom") LocalDateTime dateFrom,
            @Param("dateTo") LocalDateTime dateTo,
            @Param("nameKeyword") String nameKeyword,
            @Param("settlementId") Long settlementId);

    /**
     * 기사별 정산 목록 조회 (GET /api/agency/settlements/engineers/performance)
     *
     * 동적 필터:
     *   - status / keyword(기사명 LIKE) / settlementId(정확히 일치) 모두 null 허용
     *   - 날짜 범위는 paidAt 기준 (null 이면 해당 월 전체)
     * JOIN FETCH 로 engineer / agency N+1 방지
     */
    @Query("""
            SELECT s FROM Settlement s
            JOIN FETCH s.engineer
            JOIN FETCH s.agency
            WHERE s.agency.id = :agencyId
              AND s.paidAt >= :from
              AND s.paidAt < :to
              AND (:status IS NULL OR s.status = :status)
              AND (:keyword IS NULL OR s.engineer.name LIKE %:keyword%)
              AND (:settlementId IS NULL OR s.id = :settlementId)
            ORDER BY s.paidAt DESC
            """)
    Page<Settlement> findSettlementListByAgency(
            @Param("agencyId") Long agencyId,
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to,
            @Param("status") String status,
            @Param("keyword") String keyword,
            @Param("settlementId") Long settlementId,
            Pageable pageable);

    // ─────────────────────────────────────────────────────────────
    // GET /api/admin/settlements 전용 쿼리 (⑦~⑩)
    // ─────────────────────────────────────────────────────────────

    /**
     * [⑦] 전체 대행사 대상 월별 정산 집계 (승인된 대행사만 포함)
     * - Agencies 기준으로 Settlement를 LEFT JOIN(ON agency + createdAt 범위)하여
     *   해당 월 정산이 0건인 대행사도 결과에 포함시킨다 (asCount=0)
     * - unpaidCount: status <> 'PAID'인 건수 — 서비스 레이어에서 대행사별 status(PENDING/PAID/NONE) 파생에 사용
     * - agencyPay는 서비스 레이어에서 totalRevenue - careflowFee로 계산(프로젝션에는 원본 careflowFee까지만 포함)
     */
    @Query("""
            SELECT a.id AS agencyId,
                   a.agencyName AS agencyName,
                   COUNT(s.id) AS asCount,
                   COALESCE(SUM(s.grossAmount), 0) AS totalRevenue,
                   COALESCE(SUM(s.platformFee), 0) AS careflowFee,
                   COALESCE(SUM(CASE WHEN s.status <> 'PAID' THEN 1 ELSE 0 END), 0) AS unpaidCount
            FROM Agencies a
            LEFT JOIN Settlement s
                ON s.agency = a
                AND s.createdAt >= :from
                AND s.createdAt < :to
            WHERE a.approvalStatus = com.careflow.common.enums.AgencyStatus.APPROVED
            GROUP BY a.id, a.agencyName
            ORDER BY a.id
            """)
    List<AdminAgencySettlementProjection> findAllAgenciesMonthlySummary(
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to);

    /**
     * [⑧] 특정 대행사의 월별 건별 정산 내역 조회
     * - JOIN FETCH로 asRequest → appliance → category, asRequest → customer N+1 방지
     * - createdAt ASC 정렬 (오래된 건부터)
     */
    @Query("""
            SELECT s FROM Settlement s
            JOIN FETCH s.asRequest r
            JOIN FETCH r.appliance app
            JOIN FETCH app.category
            JOIN FETCH r.customer
            WHERE s.agency.id = :agencyId
              AND s.createdAt >= :from
              AND s.createdAt < :to
            ORDER BY s.createdAt ASC
            """)
    List<Settlement> findAgencySettlementDetails(
            @Param("agencyId") Long agencyId,
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to);

    /**
     * [⑦] 대행사별 월별 정산 집계 프로젝션
     */
    interface AdminAgencySettlementProjection {
        Long getAgencyId();
        String getAgencyName();
        Long getAsCount();
        Long getTotalRevenue();
        Long getCareflowFee();
        Long getUnpaidCount();
    }

    /**
     * 합산 집계 결과를 받을 인터페이스 프로젝션
     * - 상태별 gross 합산(paidAmount/pendingAmount/disputedAmount)은 CASE WHEN 쿼리 결과와 매핑
     */
    interface MonthlySummaryProjection {
        Long getTotalCount();
        Long getTotalGrossAmount();
        Long getPaidAmount();
        Long getPendingAmount();
        Long getDisputedAmount();
        Long getTotalPlatformFee();
        Long getTotalAgencyFee();
        Long getTotalEngineerPayout();
    }

    // [대시보드/정산용] 기사의 특정 기간 예상 수익 합산
    @Query("SELECT COALESCE(SUM(s.engineerNetAmount), 0) FROM Settlement s " +
            "WHERE s.engineer.id = :engineerId " +
            "AND s.createdAt >= :start AND s.createdAt < :end")
    Integer sumExpectedEarningByEngineerIdAndDate(
            @Param("engineerId") Long engineerId,
            @Param("start") java.time.LocalDateTime start,
            @Param("end") java.time.LocalDateTime end);

    // 기사용 정산 내역 페이징 조회 (N+1 방지)
    @org.springframework.data.jpa.repository.Query(
            value = "SELECT s FROM Settlement s " +
                    "JOIN FETCH s.asRequest r " +
                    "JOIN FETCH r.appliance app " +
                    "WHERE s.engineer.id = :engineerId " +
                    "ORDER BY s.createdAt DESC",
            countQuery = "SELECT COUNT(s) FROM Settlement s WHERE s.engineer.id = :engineerId"
    )
    org.springframework.data.domain.Page<Settlement> findPageByEngineerId(@org.springframework.data.repository.query.Param("engineerId") Long engineerId, org.springframework.data.domain.Pageable pageable);
}
