package com.careflow.appliance.repository;

import com.careflow.appliance.entity.Appliance;
import io.lettuce.core.dynamic.annotation.Param;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
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
}
