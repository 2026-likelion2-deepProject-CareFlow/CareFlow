package com.careflow.payment.repository;

import com.careflow.payment.entity.Payment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PaymentRepository extends JpaRepository<Payment, Long> {

    /** 동일 request_id 에 대한 중복 결제 여부 확인 */
    boolean existsByAsRequest_Id(Long requestId);

    // 특정 고객이 특정 대행사로 접수한 A/S 건의 결제 내역 전체 — 상태 필터 없음(READY/FAILED/CANCELLED/REFUNDED 포함 전체)
    // GET /api/agency/customers/{userId}/payments 용 — asRequest·appliance JOIN FETCH로 N+1 방지
    @Query("SELECT p FROM Payment p " +
           "JOIN FETCH p.asRequest r " +
           "JOIN FETCH r.appliance " +
           "WHERE p.customer.id = :customerId AND r.agency.id = :agencyId " +
           "ORDER BY p.createdAt DESC")
    List<Payment> findByCustomerIdAndAgencyId(
            @Param("customerId") Long customerId, @Param("agencyId") Long agencyId);
}
