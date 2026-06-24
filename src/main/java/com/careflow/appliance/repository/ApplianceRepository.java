package com.careflow.appliance.repository;

import com.careflow.appliance.entity.Appliance;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ApplianceRepository extends JpaRepository<Appliance, Long> {

    // 특정 사용자의 삭제되지 않은 가전 목록 (최신순)
    List<Appliance> findByUser_IdAndDeletedAtIsNullOrderByCreatedAtDesc(Long userId);

    // 삭제되지 않은 단건 조회 (상세 조회 시 사용)
    Optional<Appliance> findByIdAndDeletedAtIsNull(Long id);
}
