package com.careflow.user.repository;

import com.careflow.common.enums.Role;
import com.careflow.user.entity.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByEmail(String email);
    boolean existsByEmail(String email);

    // 대행사 소속 + 특정 role의 user_id 목록 조회 (알림 수신 대상 범위 산정용)
    @Query("SELECT u.id FROM User u WHERE u.agency.id = :agencyId AND u.role = :role")
    List<Long> findIdsByAgency_IdAndRole(@Param("agencyId") Long agencyId, @Param("role") Role role);

    // ── GET /api/agency/customers 고객 관리 목록 ──────────────────────────
    // stats: COMPLETED 서비스 모수(customerIds) 기준 전체 집계 — 검색 필터와 무관
    long countByIdInAndRole(List<Long> ids, Role role);
    long countByIdInAndStatus(List<Long> ids, String status);

    // start 이상, end 미만(end 제외)으로 가입일 구간 집계 — 월 경계 자정 시각의 이중 집계 방지를 위해 Between 대신 명시적 범위 사용
    @Query("SELECT COUNT(u) FROM User u WHERE u.id IN :ids AND u.createdAt >= :start AND u.createdAt < :end")
    long countByIdInAndCreatedAtRange(
            @Param("ids") List<Long> ids,
            @Param("start") LocalDateTime start,
            @Param("end") LocalDateTime end);

    // content: customerIds 범위 내에서 keyword/status/가입일 필터 적용 페이징 조회
    // region을 LEFT JOIN FETCH 하여 N+1 방지(고객은 region_id가 NULL일 수 있어 LEFT JOIN)
    @Query(value = "SELECT u FROM User u LEFT JOIN FETCH u.regionId " +
            "WHERE u.id IN :customerIds " +
            "AND (:status IS NULL OR u.status = :status) " +
            "AND (:keyword IS NULL OR u.name LIKE CONCAT('%', :keyword, '%') " +
            "     OR u.phone LIKE CONCAT('%', :keyword, '%') " +
            "     OR u.email LIKE CONCAT('%', :keyword, '%')) " +
            "AND (:joinedFrom IS NULL OR u.createdAt >= :joinedFrom) " +
            "AND (:joinedTo IS NULL OR u.createdAt < :joinedTo) " +
            "ORDER BY u.createdAt DESC",
            countQuery = "SELECT COUNT(u) FROM User u " +
            "WHERE u.id IN :customerIds " +
            "AND (:status IS NULL OR u.status = :status) " +
            "AND (:keyword IS NULL OR u.name LIKE CONCAT('%', :keyword, '%') " +
            "     OR u.phone LIKE CONCAT('%', :keyword, '%') " +
            "     OR u.email LIKE CONCAT('%', :keyword, '%')) " +
            "AND (:joinedFrom IS NULL OR u.createdAt >= :joinedFrom) " +
            "AND (:joinedTo IS NULL OR u.createdAt < :joinedTo)")
    Page<User> searchAgencyCustomers(
            @Param("customerIds") List<Long> customerIds,
            @Param("status") String status,
            @Param("keyword") String keyword,
            @Param("joinedFrom") LocalDateTime joinedFrom,
            @Param("joinedTo") LocalDateTime joinedTo,
            Pageable pageable);

    // ── GET /api/admin/users 관리자용 Customer 목록/통계 ─────────────────────
    // 기사/대행사 계정은 role != CUSTOMER 로 자연히 제외됨 (추후 역할 통합 시 확장 예정)
    long countByRole(Role role);
    long countByRoleAndStatus(Role role, String status);

    // start 이상, end 미만(end 제외)으로 가입일시 구간 집계 — 오늘 신규 가입자 수(KPI), 최근 7일 추이(member-trend) 공용
    @Query("SELECT COUNT(u) FROM User u WHERE u.role = :role AND u.createdAt >= :start AND u.createdAt < :end")
    long countByRoleAndCreatedAtRange(
            @Param("role") Role role,
            @Param("start") LocalDateTime start,
            @Param("end") LocalDateTime end);

    // 관리자용 Customer 목록 검색 — keyword(이름/연락처/이메일)/status 필터, region을 LEFT JOIN FETCH 하여 N+1 방지
    @Query(value = "SELECT u FROM User u LEFT JOIN FETCH u.regionId " +
            "WHERE u.role = :role " +
            "AND (:status IS NULL OR u.status = :status) " +
            "AND (:keyword IS NULL OR u.name LIKE CONCAT('%', :keyword, '%') " +
            "     OR u.phone LIKE CONCAT('%', :keyword, '%') " +
            "     OR u.email LIKE CONCAT('%', :keyword, '%')) " +
            "ORDER BY u.createdAt DESC",
            countQuery = "SELECT COUNT(u) FROM User u " +
            "WHERE u.role = :role " +
            "AND (:status IS NULL OR u.status = :status) " +
            "AND (:keyword IS NULL OR u.name LIKE CONCAT('%', :keyword, '%') " +
            "     OR u.phone LIKE CONCAT('%', :keyword, '%') " +
            "     OR u.email LIKE CONCAT('%', :keyword, '%'))")
    Page<User> searchCustomersForAdmin(
            @Param("role") Role role,
            @Param("status") String status,
            @Param("keyword") String keyword,
            Pageable pageable);
}