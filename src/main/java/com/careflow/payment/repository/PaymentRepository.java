package com.careflow.payment.repository;

import com.careflow.payment.entity.Payment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface PaymentRepository extends JpaRepository<Payment, Long> {

    /** 동일 request_id 에 대한 중복 결제 여부 확인 */
    boolean existsByAsRequest_Id(Long requestId);

    /**
     * 월별 정산 스케줄러용 — 정산 미생성 결제 건 조회
     *
     * 조건:
     *   - payment.status = 'SUCCESS'
     *   - payment.paid_at 이 [from, to) 범위
     *   - 해당 payment 에 대한 Settlement 가 아직 없는 것만 (중복 방지)
     *
     * JOIN FETCH 로 asRequest / agency / 고객 정보를 한 번에 로딩해 N+1 방지
     */
    @Query("""
            SELECT p FROM Payment p
            JOIN FETCH p.asRequest r
            JOIN FETCH r.agency
            WHERE p.status = com.careflow.common.enums.PaymentStatus.SUCCESS
              AND p.paidAt >= :from
              AND p.paidAt < :to
              AND NOT EXISTS (
                  SELECT 1 FROM Settlement s WHERE s.payment.id = p.id
              )
            ORDER BY p.paidAt ASC
            """)
    List<Payment> findSettlementTargets(
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to);

    // 특정 고객이 특정 대행사로 접수한 A/S 건의 결제 내역 전체 — 상태 필터 없음(READY/FAILED/CANCELLED/REFUNDED 포함 전체)
    // GET /api/agency/customers/{userId}/payments 용 — asRequest·appliance JOIN FETCH로 N+1 방지
    @Query("SELECT p FROM Payment p " +
           "JOIN FETCH p.asRequest r " +
           "JOIN FETCH r.appliance " +
           "WHERE p.customer.id = :customerId AND r.agency.id = :agencyId " +
           "ORDER BY p.createdAt DESC")
    List<Payment> findByCustomerIdAndAgencyId(
            @Param("customerId") Long customerId, @Param("agencyId") Long agencyId);

    // 고객 결제 요약 KPI용 — 해당 고객의 SUCCESS 결제 전체 합계 (원). 데이터 없으면 0
    @Query("SELECT COALESCE(SUM(p.amount), 0) FROM Payment p " +
           "WHERE p.customer.id = :customerId " +
           "AND p.status = com.careflow.common.enums.PaymentStatus.SUCCESS")
    long sumSuccessAmountByCustomerId(@Param("customerId") Long customerId);

    // 고객 결제 요약 KPI용 — 해당 고객의 이번 달(paid_at 기준 [from, to)) SUCCESS 결제 합계 (원). 데이터 없으면 0
    @Query("SELECT COALESCE(SUM(p.amount), 0) FROM Payment p " +
           "WHERE p.customer.id = :customerId " +
           "AND p.status = com.careflow.common.enums.PaymentStatus.SUCCESS " +
           "AND p.paidAt >= :from AND p.paidAt < :to")
    long sumSuccessAmountByCustomerIdAndPaidAtBetween(
            @Param("customerId") Long customerId,
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to);

    // 고객 월별 결제액 추이용 — 해당 고객의 [from, to) 범위 SUCCESS 결제 내역 전체 (월별 집계는 서비스 레이어에서 수행)
    @Query("SELECT p FROM Payment p " +
           "WHERE p.customer.id = :customerId " +
           "AND p.status = com.careflow.common.enums.PaymentStatus.SUCCESS " +
           "AND p.paidAt >= :from AND p.paidAt < :to")
    List<Payment> findSuccessByCustomerIdAndPaidAtBetween(
            @Param("customerId") Long customerId,
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to);

    // 고객 결제 내역 전체 조회용 — 상태 필터 없이 최신순(생성일 기준) 전체 반환
    List<Payment> findByCustomer_IdOrderByCreatedAtDesc(Long customerId);
}
