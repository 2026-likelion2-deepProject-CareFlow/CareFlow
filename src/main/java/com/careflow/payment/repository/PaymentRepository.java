package com.careflow.payment.repository;

import com.careflow.payment.entity.Payment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface PaymentRepository extends JpaRepository<Payment, Long> {

    /** 동일 request_id 에 대한 중복 결제 여부 확인 */
    boolean existsByAsRequest_Id(Long requestId);
}
