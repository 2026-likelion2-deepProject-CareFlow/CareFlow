package com.careflow.as_request.repository;

import com.careflow.as_request.entity.AsRequest;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AsRequestRepository extends JpaRepository<AsRequest, Long> {
    // customer 연관관계 객체의 id(user_id)로 조회
    List<AsRequest> findByCustomer_IdOrderByIdDesc(Long customerId);
}