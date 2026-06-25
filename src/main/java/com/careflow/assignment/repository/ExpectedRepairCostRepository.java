package com.careflow.assignment.repository;

import com.careflow.assignment.entity.ExpectedRepairCost;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ExpectedRepairCostRepository extends JpaRepository<ExpectedRepairCost, Long> {
    // symptom_id 로 예상 수리 비용 단건 조회 (Quartz 집계 전 데이터 없을 수 있으므로 Optional)
    Optional<ExpectedRepairCost> findBySymptom_Id(Long symptomId);
}
