package com.careflow.assignment.repository;

import com.careflow.assignment.entity.AsAssignment;
import com.careflow.assignment.dto.EngineerCompletedCount;
import com.careflow.common.enums.DiagnosisResult;
import com.careflow.user.entity.User;
import org.springframework.data.domain.Page;
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

    // 고객 A/S 목록(GET /api/as-requests/me)에 배정 기사 정보를 붙이기 위한 배치 조회 — N+1 방지용 JOIN FETCH
    @Query("SELECT a FROM AsAssignment a JOIN FETCH a.engineer WHERE a.asRequest.id IN :requestIds")
    List<AsAssignment> findByAsRequest_IdInWithEngineer(@Param("requestIds") List<Long> requestIds);

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

    /**
     * 기사의 특정 기간 내 이미 활성 배정(REJECTED 제외)이 잡혀 있는 (날짜, 예약시각) 쌍 조회.
     * 고객용 "기사 예약 가능 일정" 조회에서, 근무표에 등록된 슬롯 중 이미 배정이 찬 시각만
     * 골라서 빼기 위해 사용 — 근무표 status(하루 단위)로는 슬롯 4개 중 하나만 찼는지 구분이 안 됨.
     */
    @Query("SELECT r.scheduledDate, r.scheduledTime FROM AsAssignment a JOIN a.asRequest r " +
           "WHERE a.engineer.id = :engineerId " +
           "AND a.status <> 'REJECTED' " +
           "AND r.scheduledDate BETWEEN :start AND :end")
    List<Object[]> findBookedDateTimesByEngineerIdAndDateRange(
            @Param("engineerId") Long engineerId,
            @Param("start") LocalDate start,
            @Param("end") LocalDate end);

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

    // 기사용 배정 목록 조회 (페이징 + 상태 필터링) - N+1 방지를 위해 JOIN FETCH 사용
    @Query(value = "SELECT a FROM AsAssignment a " +
            "JOIN FETCH a.asRequest r " +
            "JOIN FETCH r.appliance app " +
            "JOIN FETCH r.customer c " +
            "JOIN FETCH r.symptom s " +
            "WHERE a.engineer.id = :engineerId " +
            "AND (:status IS NULL OR a.status = :status) " +
            "ORDER BY a.assignedAt DESC",
            countQuery = "SELECT COUNT(a) FROM AsAssignment a WHERE a.engineer.id = :engineerId AND (:status IS NULL OR a.status = :status)")
    org.springframework.data.domain.Page<AsAssignment> findByEngineerIdAndStatus(
            @Param("engineerId") Long engineerId,
            @Param("status") String status,
            org.springframework.data.domain.Pageable pageable);


    // [대시보드용] 오늘 날짜의 내 배정 목록 전체 조회
    @Query("SELECT a FROM AsAssignment a " +
            "JOIN FETCH a.asRequest r " +
            "JOIN FETCH r.customer c " +
            "JOIN FETCH r.appliance app " +
            "JOIN FETCH r.symptom s " +
            "LEFT JOIN FETCH c.regionId " +
            "WHERE a.engineer.id = :engineerId " +
            "AND r.scheduledDate = :today " +
            "AND a.status != 'REJECTED' " +
            "ORDER BY r.scheduledTime ASC")
    List<AsAssignment> findTodayAssignments(@Param("engineerId") Long engineerId, @Param("today") java.time.LocalDate today);

    // [실적/정산용] 특정 기간 내 완료(COMPLETED)된 배정 목록 상세 조회 (+ 동적 필터 추가)
    @Query("SELECT a FROM AsAssignment a " +
            "JOIN FETCH a.asRequest r " +
            "JOIN FETCH r.customer c " +
            "JOIN FETCH r.appliance app " +
            "LEFT JOIN FETCH r.workReport w " +
            "LEFT JOIN FETCH r.review rv " +
            "WHERE a.engineer.id = :engineerId " +
            "AND r.scheduledDate >= :startDate " +
            "AND r.scheduledDate <= :endDate " +
            "AND a.status = 'COMPLETED' " +
            "AND (:brand IS NULL OR app.brand = :brand) " + // 🌟 브랜드 필터
            "AND (:status IS NULL OR w.diagnosisResult = :status) " + // 🌟 진단결과 필터
            "ORDER BY r.scheduledDate ASC")
    List<AsAssignment> findCompletedAssignmentsWithDetails(
            @Param("engineerId") Long engineerId,
            @Param("startDate") java.time.LocalDate startDate,
            @Param("endDate") java.time.LocalDate endDate,
            @Param("brand") String brand,
            @Param("status") DiagnosisResult status);

    // [실적 목록용] 기간·브랜드·상태 필터 없이, 완료(COMPLETED)된 배정 전체를 최신 작업일 순으로 페이징 조회
    // "최근 실적 5개"(size=5)와 "실적 전체 목록 모달"(size=15) 양쪽에서 재사용
    @Query(value = "SELECT a FROM AsAssignment a " +
            "JOIN FETCH a.asRequest r " +
            "JOIN FETCH r.customer c " +
            "JOIN FETCH r.appliance app " +
            "LEFT JOIN FETCH r.workReport w " +
            "LEFT JOIN FETCH r.review rv " +
            "WHERE a.engineer.id = :engineerId " +
            "AND a.status = 'COMPLETED' " +
            "ORDER BY r.scheduledDate DESC",
            countQuery = "SELECT COUNT(a) FROM AsAssignment a JOIN a.asRequest r " +
                    "WHERE a.engineer.id = :engineerId AND a.status = 'COMPLETED'")
    Page<AsAssignment> findCompletedAssignmentsByEngineerId(
            @Param("engineerId") Long engineerId,
            Pageable pageable);

    // [기사용] 본인이 담당한 적 있는 고객(User) 목록 조회 (중복 제거)
    @org.springframework.data.jpa.repository.Query(
            value = "SELECT DISTINCT c FROM AsAssignment a " +
                    "JOIN a.asRequest r " +
                    "JOIN r.customer c " +
                    "LEFT JOIN FETCH c.regionId " +
                    "WHERE a.engineer.id = :engineerId " +
                    "ORDER BY c.createdAt DESC",
            countQuery = "SELECT COUNT(DISTINCT c) FROM AsAssignment a JOIN a.asRequest r JOIN r.customer c WHERE a.engineer.id = :engineerId"
    )
    org.springframework.data.domain.Page<com.careflow.user.entity.User> findCustomersByEngineerId(@org.springframework.data.repository.query.Param("engineerId") Long engineerId, org.springframework.data.domain.Pageable pageable);

    // [실적 요약] 특정 배정 상태(assignment status)의 기간 내 건수 — scheduledDate 기준(양끝 포함).
    // findCompletedAssignmentsWithDetails 와 동일한 날짜 시맨틱. 진행 중(status='ACCEPTED')·완료(status='COMPLETED') 카운트에 재사용.
    @Query("SELECT COUNT(a) FROM AsAssignment a JOIN a.asRequest r " +
           "WHERE a.engineer.id = :engineerId " +
           "AND a.status = :status " +
           "AND r.scheduledDate >= :startDate AND r.scheduledDate <= :endDate")
    long countByEngineerAndStatusInPeriod(@Param("engineerId") Long engineerId,
                                          @Param("status") String status,
                                          @Param("startDate") java.time.LocalDate startDate,
                                          @Param("endDate") java.time.LocalDate endDate);

    // [실적 요약] 이 기사에게 배정됐던 A/S 중 특정 요청 상태(as_requests.status)의 기간 내 건수 — 취소(CANCELLED) 집계용.
    // 기사가 거절(REJECTED)한 배정은 제외(= 실제로 담당하던 건이 취소된 경우만). 한 요청에 배정 행이 여러 개일 수 있어 DISTINCT.
    @Query("SELECT COUNT(DISTINCT r.id) FROM AsAssignment a JOIN a.asRequest r " +
           "WHERE a.engineer.id = :engineerId " +
           "AND a.status <> 'REJECTED' " +
           "AND r.status = :reqStatus " +
           "AND r.scheduledDate >= :startDate AND r.scheduledDate <= :endDate")
    long countRequestsByEngineerAndRequestStatusInPeriod(@Param("engineerId") Long engineerId,
                                                         @Param("reqStatus") com.careflow.common.enums.AsStatus reqStatus,
                                                         @Param("startDate") java.time.LocalDate startDate,
                                                         @Param("endDate") java.time.LocalDate endDate);

    // 🌟 신규: 동적 필터링이 추가된 쿼리 작성
    @Query(value = "SELECT DISTINCT c FROM AsAssignment a " +
            "JOIN a.asRequest r " +
            "JOIN r.customer c " +
            "LEFT JOIN FETCH c.regionId " +
            "LEFT JOIN Appliance app ON app.user.id = c.id " +
            "WHERE a.engineer.id = :engineerId " +
            "AND (:status IS NULL OR c.status = :status) " +
            "AND (:regionId IS NULL OR c.regionId.id = :regionId) " +
            "AND (:brand IS NULL OR app.brand = :brand) " +
            "AND (:search IS NULL OR c.name LIKE CONCAT('%', :search, '%') " +
            "      OR c.phone LIKE CONCAT('%', :search, '%') " +
            "      OR c.email LIKE CONCAT('%', :search, '%')) " +
            "ORDER BY c.createdAt DESC",
            countQuery = "SELECT COUNT(DISTINCT c) FROM AsAssignment a " +
                    "JOIN a.asRequest r " +
                    "JOIN r.customer c " +
                    "LEFT JOIN Appliance app ON app.user.id = c.id " +
                    "WHERE a.engineer.id = :engineerId " +
                    "AND (:status IS NULL OR c.status = :status) " +
                    "AND (:regionId IS NULL OR c.regionId.id = :regionId) " +
                    "AND (:brand IS NULL OR app.brand = :brand) " +
                    "AND (:search IS NULL OR c.name LIKE CONCAT('%', :search, '%') " +
                    "      OR c.phone LIKE CONCAT('%', :search, '%') " +
                    "      OR c.email LIKE CONCAT('%', :search, '%'))")
    Page<User> findCustomersByEngineerIdWithFilters(
            @Param("engineerId") Long engineerId,
            @Param("search") String search,
            @Param("status") String status,
            @Param("regionId") Integer regionId,
            @Param("brand") String brand,
            Pageable pageable);

    // 담당 고객들이 보유한 활성 가전의 브랜드 목록 중복 제거 조회
    @Query("SELECT DISTINCT app.brand FROM Appliance app " +
            "WHERE app.user.id IN (" +
            "    SELECT DISTINCT r.customer.id " +
            "    FROM AsAssignment a JOIN a.asRequest r " +
            "    WHERE a.engineer.id = :engineerId" +
            ") AND app.deletedAt IS NULL AND app.brand IS NOT NULL")
    List<String> findCustomerApplianceBrandsByEngineerId(@Param("engineerId") Long engineerId);

    // 고객별 진행 중인 작업 건수 집계 (N+1 방지)
    @Query("SELECT r.customer.id, COUNT(a) FROM AsAssignment a " +
            "JOIN a.asRequest r " +
            "WHERE a.engineer.id = :engineerId " +
            "AND r.customer.id IN :customerIds " +
            "AND a.status = 'ACCEPTED' " + // 배차가 수락되어 진행 중인 상태
            "GROUP BY r.customer.id")
    List<Object[]> countInProgressByCustomerIds(
            @Param("engineerId") Long engineerId,
            @Param("customerIds") List<Long> customerIds);
}
