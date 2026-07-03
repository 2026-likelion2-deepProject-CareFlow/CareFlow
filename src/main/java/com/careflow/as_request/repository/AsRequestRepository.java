package com.careflow.as_request.repository;

import com.careflow.as_request.entity.AsRequest;
import com.careflow.common.enums.AsStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface AsRequestRepository extends JpaRepository<AsRequest, Long> {
    // customer 연관관계 객체의 id(user_id)로 조회
    List<AsRequest> findByCustomer_IdOrderByIdDesc(Long customerId);

    // 대행사 소속 A/S 요청 전체 목록 조회 — COMPLETED 제외, N+1 방지용 JOIN FETCH
    @Query("SELECT r FROM AsRequest r " +
           "JOIN FETCH r.symptom " +
           "JOIN FETCH r.customer " +
           "JOIN FETCH r.visitRegion " +
           "WHERE r.agency.id = :agencyId " +
           "AND r.status != :excludeStatus " +
           "ORDER BY r.createdAt DESC")
    List<AsRequest> findByAgencyIdExcludeStatus(
            @Param("agencyId") Long agencyId,
            @Param("excludeStatus") AsStatus excludeStatus);

    // 대행사 소속 A/S 요청 필터링 조회 — 접수일(created_at)·상태 선택 적용, COMPLETED 항상 제외
    // startOfDay/endOfDay null 허용 — null 이면 날짜 조건 미적용
    // filterStatus null 허용 — null 이면 상태 조건 미적용
    @Query("SELECT r FROM AsRequest r " +
           "JOIN FETCH r.symptom " +
           "JOIN FETCH r.customer " +
           "JOIN FETCH r.visitRegion " +
           "WHERE r.agency.id = :agencyId " +
           "AND r.status != :excludeStatus " +
           "AND (:startOfDay IS NULL OR r.createdAt >= :startOfDay) " +
           "AND (:endOfDay IS NULL OR r.createdAt < :endOfDay) " +
           "AND (:filterStatus IS NULL OR r.status = :filterStatus) " +
           "ORDER BY r.createdAt DESC")
    List<AsRequest> searchByAgencyFilter(
            @Param("agencyId") Long agencyId,
            @Param("excludeStatus") AsStatus excludeStatus,
            @Param("startOfDay") LocalDateTime startOfDay,
            @Param("endOfDay") LocalDateTime endOfDay,
            @Param("filterStatus") AsStatus filterStatus);

    // 대행사용 단건 상세 조회 — appliance까지 JOIN FETCH (N+1 방지)
    @Query("SELECT r FROM AsRequest r " +
           "JOIN FETCH r.symptom " +
           "JOIN FETCH r.customer " +
           "JOIN FETCH r.appliance " +
           "JOIN FETCH r.visitRegion " +
           "WHERE r.id = :requestId")
    Optional<AsRequest> findDetailById(@Param("requestId") Long requestId);

    // 대행사 소속 A/S 요청 전체 누적 건수 집계
    @Query("SELECT COUNT(r) FROM AsRequest r WHERE r.agency.id = :agencyId")
    long countByAgencyId(@Param("agencyId") Long agencyId);

    // 대행사로부터 A/S를 받은 고객 user_id DISTINCT 목록 (알림 수신 대상 범위 산정용)
    @Query("SELECT DISTINCT r.customer.id FROM AsRequest r WHERE r.agency.id = :agencyId")
    List<Long> findDistinctCustomerIdsByAgencyId(@Param("agencyId") Long agencyId);

    // 대행사 소속 A/S 요청 중 특정 날짜 범위 + 상태 조건 건수 집계
    // created_at 범위 조건(startOfDay 이상, endOfDay 미만)으로 H2·MySQL 양쪽 호환
    @Query("SELECT COUNT(r) FROM AsRequest r " +
           "WHERE r.agency.id = :agencyId " +
           "AND r.createdAt >= :startOfDay AND r.createdAt < :endOfDay " +
           "AND (:status IS NULL OR r.status = :status)")
    long countByAgencyIdAndCreatedDateAndStatus(
            @Param("agencyId") Long agencyId,
            @Param("startOfDay") LocalDateTime startOfDay,
            @Param("endOfDay") LocalDateTime endOfDay,
            @Param("status") AsStatus status);

    // 특정 고객이 특정 대행사로 접수한 A/S 요청 전체 이력 — 상태 필터 없음(취소/완료 포함 전체)
    // GET /api/agency/customers/{userId}/as-requests 용 — appliance·symptom·visitRegion JOIN FETCH로 N+1 방지
    @Query("SELECT r FROM AsRequest r " +
           "JOIN FETCH r.appliance " +
           "JOIN FETCH r.symptom " +
           "JOIN FETCH r.visitRegion " +
           "WHERE r.customer.id = :customerId AND r.agency.id = :agencyId " +
           "ORDER BY r.createdAt DESC")
    List<AsRequest> findByCustomerIdAndAgencyId(
            @Param("customerId") Long customerId, @Param("agencyId") Long agencyId);

    // 고객 결제 요약 KPI용 — 해당 고객의 특정 상태(COMPLETED=결제 대기) A/S 요청 건수
    long countByCustomer_IdAndStatus(Long customerId, AsStatus status);

    // ─────────────────────────────────────────────────────────────
    // [ADMIN 전용 API 쿼리] GET /api/admin/as-requests
    // ─────────────────────────────────────────────────────────────

    /**
     * [관리자용] 전체 A/S 내역 페이징 및 동적 필터 조회 (N+1 방지 완벽 적용)
     * - status, region, from(startOfDay), to(endOfDay) 조건 선택적 적용
     */
    @Query(value = "SELECT r FROM AsRequest r " +
            "JOIN FETCH r.customer c " +
            "JOIN FETCH r.appliance app " +
            "JOIN FETCH app.category " +
            "JOIN FETCH r.symptom s " +
            "JOIN FETCH r.visitRegion reg " +
            "WHERE (:status IS NULL OR r.status = :status) " +
            "AND (:region IS NULL OR reg.name LIKE CONCAT('%', :region, '%')) " +
            "AND (:startOfDay IS NULL OR r.createdAt >= :startOfDay) " +
            "AND (:endOfDay IS NULL OR r.createdAt < :endOfDay) " +
            "ORDER BY r.createdAt DESC",
            countQuery = "SELECT COUNT(r) FROM AsRequest r " +
                    "JOIN r.visitRegion reg " +
                    "WHERE (:status IS NULL OR r.status = :status) " +
                    "AND (:region IS NULL OR reg.name LIKE CONCAT('%', :region, '%')) " +
                    "AND (:startOfDay IS NULL OR r.createdAt >= :startOfDay) " +
                    "AND (:endOfDay IS NULL OR r.createdAt < :endOfDay)")
    org.springframework.data.domain.Page<AsRequest> searchAllForAdmin(
            @org.springframework.data.repository.query.Param("status") com.careflow.common.enums.AsStatus status,
            @org.springframework.data.repository.query.Param("region") String region,
            @org.springframework.data.repository.query.Param("startOfDay") java.time.LocalDateTime startOfDay,
            @org.springframework.data.repository.query.Param("endOfDay") java.time.LocalDateTime endOfDay,
            org.springframework.data.domain.Pageable pageable);

    /**
     * [관리자용] 필터 조건이 적용된 A/S 상태별 그룹 집계 쿼리 (stats 용)
     * - 필터링된 결과 내에서 상태별 건수를 제공하여 UI 상단의 통계와 하단 리스트의 정합성을 맞춥니다.
     */
    @Query("SELECT r.status, COUNT(r) FROM AsRequest r " +
            "JOIN r.visitRegion reg " +
            "WHERE (:region IS NULL OR reg.name LIKE CONCAT('%', :region, '%')) " +
            "AND (:startOfDay IS NULL OR r.createdAt >= :startOfDay) " +
            "AND (:endOfDay IS NULL OR r.createdAt < :endOfDay) " +
            "GROUP BY r.status")
    List<Object[]> countGroupByStatusForAdmin(
            @org.springframework.data.repository.query.Param("region") String region,
            @org.springframework.data.repository.query.Param("startOfDay") java.time.LocalDateTime startOfDay,
            @org.springframework.data.repository.query.Param("endOfDay") java.time.LocalDateTime endOfDay);
}