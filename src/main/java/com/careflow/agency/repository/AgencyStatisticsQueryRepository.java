package com.careflow.agency.repository;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 대행사 통계 전용 네이티브 SQL 집계 레포지토리
 * 복잡한 집계(DATE/HOUR/JOIN/LIMIT)는 JPQL 표현이 어려우므로 EntityManager 네이티브 쿼리 사용
 * H2(MySQL 모드) 및 MySQL 양쪽 호환 쿼리만 사용
 */
@Repository
public class AgencyStatisticsQueryRepository {

    @PersistenceContext
    private EntityManager em;

    // ──────────────────────────────────────────────────────────────
    // Summary 집계
    // ──────────────────────────────────────────────────────────────

    /** 기간 내 총 접수 건수 */
    public long countReceipts(Long agencyId, LocalDateTime from, LocalDateTime to) {
        Number result = (Number) em.createNativeQuery(
                "SELECT COUNT(*) FROM as_requests " +
                "WHERE agency_id = :agencyId AND created_at >= :from AND created_at < :to")
                .setParameter("agencyId", agencyId)
                .setParameter("from", from)
                .setParameter("to", to)
                .getSingleResult();
        return result.longValue();
    }

    /** 기간 내 완료 건수 (COMPLETED, PAID) */
    public long countCompleted(Long agencyId, LocalDateTime from, LocalDateTime to) {
        Number result = (Number) em.createNativeQuery(
                "SELECT COUNT(*) FROM as_requests " +
                "WHERE agency_id = :agencyId AND created_at >= :from AND created_at < :to " +
                "AND status IN ('COMPLETED','PAID')")
                .setParameter("agencyId", agencyId)
                .setParameter("from", from)
                .setParameter("to", to)
                .getSingleResult();
        return result.longValue();
    }

    /**
     * 완료 건 기준 평균 처리 시간 (분 단위)
     * created_at → updated_at 차이 집계 (TIMESTAMPDIFF: H2 MySQL모드·MySQL 공통 지원)
     */
    public Double findAvgProcessingTimeMinutes(Long agencyId, LocalDateTime from, LocalDateTime to) {
        Object result = em.createNativeQuery(
                "SELECT AVG(TIMESTAMPDIFF(MINUTE, created_at, updated_at)) FROM as_requests " +
                "WHERE agency_id = :agencyId AND created_at >= :from AND created_at < :to " +
                "AND status IN ('COMPLETED','PAID')")
                .setParameter("agencyId", agencyId)
                .setParameter("from", from)
                .setParameter("to", to)
                .getSingleResult();
        return result == null ? null : ((Number) result).doubleValue();
    }

    /**
     * 기간 내 reviews 평균 평점 (agency 기준 — as_requests JOIN)
     */
    public Double findAvgRating(Long agencyId, LocalDateTime from, LocalDateTime to) {
        Object result = em.createNativeQuery(
                "SELECT AVG(r.rating) " +
                "FROM reviews r " +
                "JOIN as_requests ar ON r.request_id = ar.request_id " +
                "WHERE ar.agency_id = :agencyId AND r.is_visible = 1 " +
                "AND r.created_at >= :from AND r.created_at < :to")
                .setParameter("agencyId", agencyId)
                .setParameter("from", from)
                .setParameter("to", to)
                .getSingleResult();
        return result == null ? null : ((Number) result).doubleValue();
    }

    /**
     * 기간 내 정산 총액 (settlements.gross_amount 합산)
     * settlements.created_at 기준 필터
     */
    public long sumSettlementAmount(Long agencyId, LocalDateTime from, LocalDateTime to) {
        Object result = em.createNativeQuery(
                "SELECT COALESCE(SUM(gross_amount), 0) FROM settlements " +
                "WHERE agency_id = :agencyId AND created_at >= :from AND created_at < :to")
                .setParameter("agencyId", agencyId)
                .setParameter("from", from)
                .setParameter("to", to)
                .getSingleResult();
        return ((Number) result).longValue();
    }

    // ──────────────────────────────────────────────────────────────
    // Daily Trend 집계
    // ──────────────────────────────────────────────────────────────

    /**
     * 날짜별 접수·완료 건수 목록
     * 반환: Object[] { String dateStr(yyyy-MM-dd), Long receiptCount, Long completedCount }
     */
    @SuppressWarnings("unchecked")
    public List<Object[]> findDailyTrend(Long agencyId, LocalDateTime from, LocalDateTime to) {
        return em.createNativeQuery(
                "SELECT DATE(created_at) AS date_str, " +
                "       COUNT(*) AS receipt_count, " +
                "       SUM(CASE WHEN status IN ('COMPLETED','PAID') THEN 1 ELSE 0 END) AS completed_count " +
                "FROM as_requests " +
                "WHERE agency_id = :agencyId AND created_at >= :from AND created_at < :to " +
                "GROUP BY DATE(created_at) " +
                "ORDER BY date_str")
                .setParameter("agencyId", agencyId)
                .setParameter("from", from)
                .setParameter("to", to)
                .getResultList();
    }

    // ──────────────────────────────────────────────────────────────
    // Hourly 집계
    // ──────────────────────────────────────────────────────────────

    /**
     * 3시간 단위 슬롯별 접수 건수
     * 반환: Object[] { Integer slotIndex(0~7), Long count }
     */
    @SuppressWarnings("unchecked")
    public List<Object[]> findHourlyDist(Long agencyId, LocalDateTime from, LocalDateTime to) {
        return em.createNativeQuery(
                "SELECT (HOUR(created_at) / 3) AS slot_idx, COUNT(*) AS cnt " +
                "FROM as_requests " +
                "WHERE agency_id = :agencyId AND created_at >= :from AND created_at < :to " +
                "GROUP BY (HOUR(created_at) / 3) " +
                "ORDER BY slot_idx")
                .setParameter("agencyId", agencyId)
                .setParameter("from", from)
                .setParameter("to", to)
                .getResultList();
    }

    // ──────────────────────────────────────────────────────────────
    // Category Dist 집계
    // ──────────────────────────────────────────────────────────────

    /**
     * 가전 카테고리별 접수 건수 (as_requests → appliances → appliance_categories JOIN)
     * 반환: Object[] { String categoryName, Long count }
     */
    @SuppressWarnings("unchecked")
    public List<Object[]> findCategoryDist(Long agencyId, LocalDateTime from, LocalDateTime to) {
        return em.createNativeQuery(
                "SELECT ac.name AS category_name, COUNT(ar.request_id) AS cnt " +
                "FROM as_requests ar " +
                "JOIN appliances a           ON ar.appliance_id = a.appliance_id " +
                "JOIN appliance_categories ac ON a.category_id  = ac.category_id " +
                "WHERE ar.agency_id = :agencyId AND ar.created_at >= :from AND ar.created_at < :to " +
                "GROUP BY ac.category_id, ac.name " +
                "ORDER BY cnt DESC")
                .setParameter("agencyId", agencyId)
                .setParameter("from", from)
                .setParameter("to", to)
                .getResultList();
    }

    // ──────────────────────────────────────────────────────────────
    // Status Count 집계
    // ──────────────────────────────────────────────────────────────

    /**
     * A/S 상태(enum String)별 건수
     * 반환: Object[] { String status, Long count }
     */
    @SuppressWarnings("unchecked")
    public List<Object[]> findStatusCount(Long agencyId, LocalDateTime from, LocalDateTime to) {
        return em.createNativeQuery(
                "SELECT status, COUNT(*) AS cnt " +
                "FROM as_requests " +
                "WHERE agency_id = :agencyId AND created_at >= :from AND created_at < :to " +
                "GROUP BY status")
                .setParameter("agencyId", agencyId)
                .setParameter("from", from)
                .setParameter("to", to)
                .getResultList();
    }

    // ──────────────────────────────────────────────────────────────
    // Engineer Top5 집계
    // ──────────────────────────────────────────────────────────────

    /**
     * 완료 배차 기준 기사별 건수 TOP5 (as_assignments.status = 'COMPLETED')
     * 반환: Object[] { String engineerName, Long completedCount }
     */
    @SuppressWarnings("unchecked")
    public List<Object[]> findEngineerTop5(Long agencyId, LocalDateTime from, LocalDateTime to) {
        return em.createNativeQuery(
                "SELECT u.name AS engineer_name, COUNT(aa.assignment_id) AS completed_count " +
                "FROM as_assignments aa " +
                "JOIN users u ON aa.engineer_id = u.user_id " +
                "WHERE aa.agency_id = :agencyId AND aa.status = 'COMPLETED' " +
                "AND aa.assigned_at >= :from AND aa.assigned_at < :to " +
                "GROUP BY aa.engineer_id, u.name " +
                "ORDER BY completed_count DESC " +
                "LIMIT 5")
                .setParameter("agencyId", agencyId)
                .setParameter("from", from)
                .setParameter("to", to)
                .getResultList();
    }

    // ──────────────────────────────────────────────────────────────
    // Monthly Summary 집계
    // ──────────────────────────────────────────────────────────────

    /**
     * 최다 접수 요일 (DAYOFWEEK: 1=일, 2=월, ..., 7=토)
     * 반환: Object[] { Integer dayOfWeek, Long count } or null(데이터 없음)
     */
    public Object[] findTopDayOfWeek(Long agencyId, LocalDateTime from, LocalDateTime to) {
        @SuppressWarnings("unchecked")
        List<Object[]> rows = em.createNativeQuery(
                "SELECT DAYOFWEEK(created_at) AS dow, COUNT(*) AS cnt " +
                "FROM as_requests " +
                "WHERE agency_id = :agencyId AND created_at >= :from AND created_at < :to " +
                "GROUP BY DAYOFWEEK(created_at) " +
                "ORDER BY cnt DESC " +
                "LIMIT 1")
                .setParameter("agencyId", agencyId)
                .setParameter("from", from)
                .setParameter("to", to)
                .getResultList();
        return rows.isEmpty() ? null : rows.get(0);
    }

    /**
     * 최다 접수 시간대 (1시간 단위)
     * 반환: Object[] { Integer hour(0~23), Long count } or null
     */
    public Object[] findTopHourSlot(Long agencyId, LocalDateTime from, LocalDateTime to) {
        @SuppressWarnings("unchecked")
        List<Object[]> rows = em.createNativeQuery(
                "SELECT HOUR(created_at) AS hr, COUNT(*) AS cnt " +
                "FROM as_requests " +
                "WHERE agency_id = :agencyId AND created_at >= :from AND created_at < :to " +
                "GROUP BY HOUR(created_at) " +
                "ORDER BY cnt DESC " +
                "LIMIT 1")
                .setParameter("agencyId", agencyId)
                .setParameter("from", from)
                .setParameter("to", to)
                .getResultList();
        return rows.isEmpty() ? null : rows.get(0);
    }

    /**
     * 이달 평균 평점 최고 기사 (agency 소속 기사 리뷰 기준)
     * 반환: Object[] { String engineerName, Double avgRating } or null
     */
    public Object[] findTopRatedEngineer(Long agencyId, LocalDateTime from, LocalDateTime to) {
        @SuppressWarnings("unchecked")
        List<Object[]> rows = em.createNativeQuery(
                "SELECT u.name AS engineer_name, AVG(r.rating) AS avg_rating " +
                "FROM reviews r " +
                "JOIN as_requests ar ON r.request_id = ar.request_id " +
                "JOIN users u        ON r.engineer_id = u.user_id " +
                "WHERE ar.agency_id = :agencyId AND r.is_visible = 1 " +
                "AND r.created_at >= :from AND r.created_at < :to " +
                "GROUP BY r.engineer_id, u.name " +
                "ORDER BY avg_rating DESC " +
                "LIMIT 1")
                .setParameter("agencyId", agencyId)
                .setParameter("from", from)
                .setParameter("to", to)
                .getResultList();
        return rows.isEmpty() ? null : rows.get(0);
    }

    /**
     * 고객 만족도: 평점 4점 이상 리뷰 수 / 전체 리뷰 수
     * 반환: Object[] { Long satisfiedCount, Long totalCount }
     */
    public Object[] findSatisfactionStats(Long agencyId, LocalDateTime from, LocalDateTime to) {
        @SuppressWarnings("unchecked")
        List<Object[]> rows = em.createNativeQuery(
                "SELECT COUNT(CASE WHEN r.rating >= 4 THEN 1 END) AS satisfied, " +
                "       COUNT(*) AS total " +
                "FROM reviews r " +
                "JOIN as_requests ar ON r.request_id = ar.request_id " +
                "WHERE ar.agency_id = :agencyId AND r.is_visible = 1 " +
                "AND r.created_at >= :from AND r.created_at < :to")
                .setParameter("agencyId", agencyId)
                .setParameter("from", from)
                .setParameter("to", to)
                .getResultList();
        return rows.isEmpty() ? null : rows.get(0);
    }
}
