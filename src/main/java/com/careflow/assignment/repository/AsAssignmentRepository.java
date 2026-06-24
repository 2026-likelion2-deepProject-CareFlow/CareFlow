package com.careflow.assignment.repository;

import com.careflow.assignment.entity.AsAssignment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AsAssignmentRepository extends JpaRepository<AsAssignment, Long> {
    List<AsAssignment> findByAsRequest_Id(Long requestId);
    List<AsAssignment> findByEngineer_IdAndStatus(Long engineerId, String status);

    // 부하 반영 복합 점수(Option B) 산정을 위해 기사별 대기 중 배차 수 조회
    long countByEngineer_IdAndStatus(Long engineerId, String status);
}
