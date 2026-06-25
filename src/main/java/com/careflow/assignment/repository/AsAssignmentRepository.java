package com.careflow.assignment.repository;

import com.careflow.assignment.entity.AsAssignment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface AsAssignmentRepository extends JpaRepository<AsAssignment, Long> {
    List<AsAssignment> findByAsRequest_Id(Long requestId);
    List<AsAssignment> findByEngineer_IdAndStatus(Long engineerId, String status);

    // 부하 반영 복합 점수(Option B) 산정을 위해 기기별 대기 중 배차 수 조회
    long countByEngineer_IdAndStatus(Long engineerId, String status);

    // 대행사 소속 수리 기사들의 배차 내역 조회
    List<AsAssignment> findByAgency_Id(Long agencyId);

    // 상세 조회 전용 — as_requests·symptom·customer·appliance·engineer 를 한 번의 JOIN FETCH 로 로딩
    // 기존 findById 의 LAZY 초기화 5회(쿼리 2-1~2-5)를 단일 쿼리로 압축
    @Query("SELECT a FROM AsAssignment a " +
           "JOIN FETCH a.asRequest r " +
           "JOIN FETCH r.symptom " +
           "JOIN FETCH r.customer " +
           "JOIN FETCH r.appliance " +
           "JOIN FETCH a.engineer " +
           "WHERE a.id = :id")
    Optional<AsAssignment> findDetailById(@Param("id") Long id);

    // 날짜·상태 동적 필터 조회 — 두 파라미터 모두 null 허용 (null = 해당 조건 미적용)
    // JOIN FETCH 로 as_requests·symptom·engineer 를 한 번에 로딩해 N+1 방지
    @Query("SELECT DISTINCT a FROM AsAssignment a " +
           "JOIN FETCH a.asRequest r " +
           "JOIN FETCH r.symptom " +
           "JOIN FETCH a.engineer " +
           "WHERE a.agency.id = :agencyId " +
           "AND (:date IS NULL OR r.scheduledDate = :date) " +
           "AND (:status IS NULL OR a.status = :status) " +
           "ORDER BY r.scheduledDate ASC, a.assignedAt ASC")
    List<AsAssignment> searchByFilter(
            @Param("agencyId") Long agencyId,
            @Param("date") LocalDate date,
            @Param("status") String status);
}
