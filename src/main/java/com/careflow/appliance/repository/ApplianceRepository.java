package com.careflow.appliance.repository;

import com.careflow.appliance.entity.Appliance;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface ApplianceRepository extends JpaRepository<Appliance, Long> {

    // 특정 사용자의 삭제되지 않은 가전 목록 (최신순)
    List<Appliance> findByUser_IdAndDeletedAtIsNullOrderByCreatedAtDesc(Long userId);

    // 삭제되지 않은 단건 조회 (상세 조회 시 사용)
    Optional<Appliance> findByIdAndDeletedAtIsNull(Long id);

    List<Appliance> findByUser_IdOrderByIdDesc(Long ownerId);

    // 🌟 무상 A/S 만료 알림 대상을 조회하는 쿼리
    @Query("SELECT a FROM Appliance a " +
            "JOIN FETCH a.user " +
            "WHERE a.warrantyEndDate = :targetDate " +
            "AND a.deletedAt IS NULL " +
            "AND a.status != 'SOLD'")
    List<Appliance> findByWarrantyEndDateWithUser(@Param("targetDate") LocalDate targetDate);

    // 다수 고객의 미삭제 가전 개수를 한 번에 집계 (N+1 방지) — GET /api/agency/customers 목록용
    // 결과는 [user_id(Long), count(Long)] 형태의 Object[] 목록
    @Query("SELECT a.user.id, COUNT(a) FROM Appliance a " +
            "WHERE a.user.id IN :userIds AND a.deletedAt IS NULL " +
            "GROUP BY a.user.id")
    List<Object[]> countActiveByUserIds(@Param("userIds") List<Long> userIds);

    // 특정 고객의 미삭제 가전 목록을 category까지 JOIN FETCH 하여 단일 쿼리로 조회 (N+1 방지)
    // GET /api/agency/customers/{userId}/appliances 용 — 최신 등록순 정렬
    @Query("SELECT a FROM Appliance a " +
            "JOIN FETCH a.category " +
            "WHERE a.user.id = :userId AND a.deletedAt IS NULL " +
            "ORDER BY a.createdAt DESC")
    List<Appliance> findByUserIdWithCategory(@Param("userId") Long userId);
}
