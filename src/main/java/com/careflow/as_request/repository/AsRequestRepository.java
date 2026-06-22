package com.careflow.as_request.repository;

import com.careflow.as_request.entity.AsRequest;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface AsRequestRepository extends JpaRepository<AsRequest, Long> {
    // 특정 고객이 신청한 A/S 내역을 최신순으로 전체 조회
    List<AsRequest> findByCustomerIdOrderByIdDesc(Long customerId);
}