package com.careflow.part.repository;
import com.careflow.part.domain.entity.RepairPart;
import io.lettuce.core.dynamic.annotation.Param;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface RepairPartRepository extends JpaRepository<RepairPart, Long> {
    // 🌟 신규: 메모리 필터링 방지! DB 레벨에서 부분 일치(LIKE) 검색 수행
    @Query("SELECT r FROM RepairPart r " +
            "WHERE :search IS NULL " +
            "   OR r.partName LIKE CONCAT('%', :search, '%') " +
            "   OR r.partCode LIKE CONCAT('%', :search, '%') " +
            "ORDER BY r.partName ASC")
    List<RepairPart> searchByKeyword(@Param("search") String search);
}