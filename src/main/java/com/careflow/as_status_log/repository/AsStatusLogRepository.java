package com.careflow.as_status_log.repository;

import com.careflow.as_status_log.entity.AsStatusLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AsStatusLogRepository extends JpaRepository<AsStatusLog, Long> {

    // request_id 기준 상태 변경 이력을 시간순 조회
    List<AsStatusLog> findByAsRequest_IdOrderByCreatedAtAsc(Long requestId);

    // request_id 기준 상태 변경 이력을 시간순 조회 (고객용 — N+1 방지 JOIN FETCH 포함)
    @Query("SELECT l FROM AsStatusLog l " +
           "JOIN FETCH l.asRequest r " +
           "JOIN FETCH l.changedBy " +
           "WHERE r.id = :requestId " +
           "ORDER BY l.createdAt ASC")
    List<AsStatusLog> findByRequestIdWithDetails(@Param("requestId") Long requestId);

    // 소속 대행사의 모든 A/S 요청에 대한 상태 변경 이력 조회 (최신순)
    @Query("SELECT l FROM AsStatusLog l " +
           "JOIN FETCH l.asRequest r " +
           "JOIN FETCH l.changedBy " +
           "WHERE r.agency.id = :agencyId " +
           "ORDER BY l.createdAt DESC")
    List<AsStatusLog> findAllByAgencyIdOrderByCreatedAtDesc(@Param("agencyId") Long agencyId);

    // 특정 요청 ID 목록에 해당하는 상태 변경 이력 조회 (최신순) — in-progress 배정 목록 로그 배치 조회용
    @Query("SELECT l FROM AsStatusLog l " +
           "JOIN FETCH l.asRequest r " +
           "WHERE r.id IN :requestIds " +
           "ORDER BY l.createdAt DESC")
    List<AsStatusLog> findByRequestIdsOrderByCreatedAtDesc(@Param("requestIds") List<Long> requestIds);

    // 소속 대행사의 to_status 별 이력 건수 집계
    @Query("SELECT l.toStatus, COUNT(l) FROM AsStatusLog l " +
           "JOIN l.asRequest r " +
           "WHERE r.agency.id = :agencyId " +
           "GROUP BY l.toStatus")
    List<Object[]> countGroupByToStatusForAgency(@Param("agencyId") Long agencyId);
}
