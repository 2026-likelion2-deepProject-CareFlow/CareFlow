package com.careflow.assignment.repository;

import com.careflow.assignment.entity.AsStatusLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AsStatusLogRepository extends JpaRepository<AsStatusLog, Long> {
    // request_id 기준 상태 변경 이력을 시간순 조회
    List<AsStatusLog> findByAsRequest_IdOrderByCreatedAtAsc(Long requestId);
}
