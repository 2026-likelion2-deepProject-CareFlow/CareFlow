package com.careflow.assignment.repository;

import com.careflow.assignment.entity.AsAssignment;
import com.careflow.assignment.dto.EngineerCompletedCount;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import org.springframework.data.jpa.repository.Modifying;
import org.springframework.transaction.annotation.Transactional;

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

    // 대행사 소속 기사들의 수리 완료(COMPLETED) 건수 집계 — 내림차순 상위 N건
    // as_requests.status = COMPLETED 기준으로 집계하며, Pageable 로 상위 3건만 반환
    @Query("SELECT a.engineer.id AS engineerUserId, " +
           "a.engineer.name AS engineerName, " +
           "COUNT(a) AS completedCount " +
           "FROM AsAssignment a " +
           "JOIN a.asRequest r " +
           "WHERE a.agency.id = :agencyId " +
           "AND r.status = :status " +
           "GROUP BY a.engineer.id, a.engineer.name " +
           "ORDER BY COUNT(a) DESC")
    List<EngineerCompletedCount> findTopByCompletedCount(
            @Param("agencyId") Long agencyId,
            @Param("status") com.careflow.common.enums.AsStatus status,
            Pageable pageable);

    // 테스트 픽스처용 status 강제 업데이트 (도메인 메서드 없는 상태 세팅)
    @Modifying
    @Transactional
    @Query("UPDATE AsAssignment a SET a.status = :status WHERE a.id = :id")
    void updateStatus(@Param("id") Long id, @Param("status") String status);

    // 기사의 특정 날짜 작업 일정 조회 — REJECTED 건 제외, N+1 방지용 JOIN FETCH
    @Query("SELECT a FROM AsAssignment a " +
           "JOIN FETCH a.asRequest r " +
           "JOIN FETCH r.customer " +
           "JOIN FETCH r.appliance " +
           "JOIN FETCH r.symptom " +
           "JOIN FETCH r.visitRegion " +
           "WHERE a.engineer.id = :engineerUserId " +
           "AND r.scheduledDate = :date " +
           "AND a.status <> 'REJECTED' " +
           "ORDER BY r.scheduledTime ASC")
    List<AsAssignment> findTaskSchedule(
            @Param("engineerUserId") Long engineerUserId,
            @Param("date") LocalDate date);

    /**
     * 기사의 현재 활성 배정 조회 (실시간 현황용)
     * REJECTED·COMPLETED 상태 제외, 배정 일시 최신 기준 1건
     * GET /api/agency/engineers/realtime-status 에서 사용
     */
    @Query("SELECT a FROM AsAssignment a " +
           "JOIN FETCH a.asRequest r " +
           "JOIN FETCH r.appliance " +
           "WHERE a.engineer.id = :engineerId " +
           "AND a.status NOT IN ('REJECTED', 'COMPLETED') " +
           "ORDER BY a.assignedAt DESC")
    List<AsAssignment> findActiveByEngineerId(@Param("engineerId") Long engineerId);

    // 진행 중(ACCEPTED) 배정 목록 — as_requests·appliance·customer·engineer JOIN FETCH, N+1 방지
    @Query("SELECT DISTINCT a FROM AsAssignment a " +
           "JOIN FETCH a.asRequest r " +
           "JOIN FETCH r.appliance ap " +
           "JOIN FETCH r.customer c " +
           "JOIN FETCH a.engineer e " +
           "WHERE a.agency.id = :agencyId " +
           "AND a.status = 'ACCEPTED' " +
           "ORDER BY r.scheduledDate ASC, a.assignedAt ASC")
    List<AsAssignment> findInProgressByAgencyId(@Param("agencyId") Long agencyId);

    // 진행 중(ACCEPTED) 배정 필터 조회 — date·regionId·brand·engineerId 조건 선택 적용
    // latestLogStatus·keyword 는 로그 집계 후 서비스 레이어에서 인메모리 필터링
    @Query("SELECT DISTINCT a FROM AsAssignment a " +
           "JOIN FETCH a.asRequest r " +
           "JOIN FETCH r.appliance ap " +
           "JOIN FETCH r.customer c " +
           "JOIN FETCH a.engineer e " +
           "WHERE a.agency.id = :agencyId " +
           "AND a.status = 'ACCEPTED' " +
           "AND (:date IS NULL OR r.scheduledDate = :date) " +
           "AND (:regionId IS NULL OR r.visitRegion.id = :regionId) " +
           "AND (:brand IS NULL OR ap.brand = :brand) " +
           "AND (:engineerId IS NULL OR e.id = :engineerId) " +
           "ORDER BY r.scheduledDate ASC, a.assignedAt ASC")
    List<AsAssignment> findInProgressWithFilter(
            @Param("agencyId") Long agencyId,
            @Param("date") LocalDate date,
            @Param("regionId") Long regionId,
            @Param("brand") String brand,
            @Param("engineerId") Long engineerId);

    // 완료(COMPLETED) 배정 총 건수 조회 — stats.completedCount 산정용
    @Query("SELECT COUNT(a) FROM AsAssignment a WHERE a.agency.id = :agencyId AND a.status = 'COMPLETED'")
    int countCompletedByAgencyId(@Param("agencyId") Long agencyId);

    // 완료(COMPLETED) 배정 목록 — as_requests·appliance·customer·engineer JOIN FETCH
    @Query("SELECT DISTINCT a FROM AsAssignment a " +
           "JOIN FETCH a.asRequest r " +
           "JOIN FETCH r.appliance ap " +
           "JOIN FETCH r.customer c " +
           "JOIN FETCH a.engineer e " +
           "WHERE a.agency.id = :agencyId " +
           "AND a.status = 'COMPLETED' " +
           "ORDER BY a.assignedAt DESC")
    List<AsAssignment> findCompletedByAgencyId(@Param("agencyId") Long agencyId);

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

    /**
     * [기사용 API] 기사 본인의 작업 보고서 목록 조회 (페이징)
     * 배차(AsAssignment)를 기준으로 AsRequest, Appliance, Customer를 JOIN FETCH 하고,
     * WorkReport는 작성 대기(DRAFT) 상태일 때 null일 수 있으므로 LEFT JOIN FETCH 처리!
     */
    @Query(value = "SELECT a FROM AsAssignment a " +
            "JOIN FETCH a.asRequest req " +
            "JOIN FETCH req.customer c " +
            "JOIN FETCH req.appliance app " +
            "LEFT JOIN FETCH req.workReport wr " +
            "WHERE a.engineer.id = :engineerId " +
            "AND a.status IN ('ACCEPTED', 'COMPLETED') " +
            "ORDER BY req.scheduledDate DESC, req.scheduledTime DESC",
            countQuery = "SELECT COUNT(a) FROM AsAssignment a WHERE a.engineer.id = :engineerId AND a.status IN ('ACCEPTED', 'COMPLETED')")
    org.springframework.data.domain.Page<AsAssignment> findWorkReportListByEngineerId(
            @org.springframework.data.repository.query.Param("engineerId") Long engineerId,
            org.springframework.data.domain.Pageable pageable);

    // 대행사 소속 기사에게 COMPLETED 서비스를 1회 이상 받은 고객 user_id DISTINCT 목록
    // GET /api/agency/customers (고객 관리 목록 모수 산정용)
    @Query("SELECT DISTINCT a.asRequest.customer.id FROM AsAssignment a " +
           "WHERE a.agency.id = :agencyId AND a.status = 'COMPLETED'")
    List<Long> findDistinctCompletedCustomerIdsByAgencyId(@Param("agencyId") Long agencyId);
}
