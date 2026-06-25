package com.careflow.as_request.repository;

import com.careflow.as_request.entity.AsRequest;
import com.careflow.common.enums.AsStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface AsRequestRepository extends JpaRepository<AsRequest, Long> {
    // customer 연관관계 객체의 id(user_id)로 조회
    List<AsRequest> findByCustomer_IdOrderByIdDesc(Long customerId);

    // 대행사 소속 A/S 요청 전체 목록 조회 — COMPLETED 제외, N+1 방지용 JOIN FETCH
    @Query("SELECT r FROM AsRequest r " +
           "JOIN FETCH r.symptom " +
           "JOIN FETCH r.customer " +
           "JOIN FETCH r.visitRegion " +
           "WHERE r.agency.id = :agencyId " +
           "AND r.status != :excludeStatus " +
           "ORDER BY r.createdAt DESC")
    List<AsRequest> findByAgencyIdExcludeStatus(
            @Param("agencyId") Long agencyId,
            @Param("excludeStatus") AsStatus excludeStatus);

    // 대행사 소속 A/S 요청 필터링 조회 — 날짜·상태 선택 적용, COMPLETED 항상 제외
    // date/filterStatus null 허용 — null 파라미터는 해당 조건 미적용
    @Query("SELECT r FROM AsRequest r " +
           "JOIN FETCH r.symptom " +
           "JOIN FETCH r.customer " +
           "JOIN FETCH r.visitRegion " +
           "WHERE r.agency.id = :agencyId " +
           "AND r.status != :excludeStatus " +
           "AND (:date IS NULL OR r.scheduledDate = :date) " +
           "AND (:filterStatus IS NULL OR r.status = :filterStatus) " +
           "ORDER BY r.scheduledDate ASC, r.createdAt ASC")
    List<AsRequest> searchByAgencyFilter(
            @Param("agencyId") Long agencyId,
            @Param("excludeStatus") AsStatus excludeStatus,
            @Param("date") LocalDate date,
            @Param("filterStatus") AsStatus filterStatus);

    // 대행사용 단건 상세 조회 — appliance까지 JOIN FETCH (N+1 방지)
    @Query("SELECT r FROM AsRequest r " +
           "JOIN FETCH r.symptom " +
           "JOIN FETCH r.customer " +
           "JOIN FETCH r.appliance " +
           "JOIN FETCH r.visitRegion " +
           "WHERE r.id = :requestId")
    Optional<AsRequest> findDetailById(@Param("requestId") Long requestId);
}