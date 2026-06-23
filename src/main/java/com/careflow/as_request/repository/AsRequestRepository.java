package com.careflow.as_request.repository;

import com.careflow.as_request.entity.AsRequest;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AsRequestRepository extends JpaRepository<AsRequest, Long> {
    List<AsRequest> findByCustomerIdOrderByIdDesc(Long customerId);
}