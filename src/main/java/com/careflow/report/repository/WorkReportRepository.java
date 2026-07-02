package com.careflow.report.repository;

import com.careflow.report.domain.entity.WorkReport;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface WorkReportRepository extends JpaRepository<WorkReport, Long> {
    boolean existsByAsRequest_Id(Long requestId);   // 해당 A/S 건에 대한 보고서가 이미 존재하는지 선검사 (중복 제출 방어용)

    // request_id 복수 조회 — 완료 배정 목록 N+1 방지 배치용
    @Query("SELECT w FROM WorkReport w WHERE w.asRequest.id IN :requestIds")
    List<WorkReport> findByAsRequestIdIn(@Param("requestIds") List<Long> requestIds);

    // requestId(AsRequest PK)로 수리 보고서 단건 조회
    Optional<WorkReport> findByAsRequest_Id(Long requestId);

    // 동일 가전제품(appliance_id)에 대해 작성된 모든 수리 보고서 조회 — 이전 수리 이력 표시용
    List<WorkReport> findByAsRequest_Appliance_Id(Long applianceId);

    // 추가할 내용: 특정 가전의 수리 이력을 최신순으로 조회 (N+1 방지 튜닝)
    @Query("SELECT w FROM WorkReport w " +
            "JOIN FETCH w.asRequest r " +
            "JOIN FETCH r.symptom s " +
            "JOIN FETCH w.engineer e " +
            "WHERE r.appliance.id = :applianceId " +
            "ORDER BY w.submittedAt DESC")
    List<WorkReport> findByApplianceIdOrderBySubmittedAtDesc(@Param("applianceId") Long applianceId);

    @Query("SELECT w FROM WorkReport w " +
            "JOIN FETCH w.asRequest r " +
            "JOIN FETCH r.customer c " +
            "JOIN FETCH w.engineer e " +
            "LEFT JOIN FETCH w.parts p " +
            "LEFT JOIN FETCH p.repairPart rp " +
            "WHERE w.reportId = :reportId")
    Optional<WorkReport> findByIdWithParts(@Param("reportId") Long reportId);

    /**
     * [기사용 API] 특정 기사가 특정 고객에게 처리한 작업 완료 보고서 이력 조회 (N+1 방지)
     */
    @org.springframework.data.jpa.repository.Query(
            "SELECT w FROM WorkReport w " +
                    "JOIN FETCH w.asRequest r " +
                    "JOIN FETCH r.appliance a " +
                    "JOIN FETCH r.symptom s " +
                    "WHERE w.engineer.id = :engineerId AND r.customer.id = :customerId " +
                    "ORDER BY w.submittedAt DESC"
    )
    List<WorkReport> findHistoryByEngineerAndCustomer(
            @org.springframework.data.repository.query.Param("engineerId") Long engineerId,
            @org.springframework.data.repository.query.Param("customerId") Long customerId
    );

}

