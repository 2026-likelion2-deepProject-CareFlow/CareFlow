package com.careflow.payment.service;

import com.careflow.as_request.entity.AsRequest;
import com.careflow.as_request.repository.AsRequestRepository;
import com.careflow.common.enums.AsStatus;
import com.careflow.payment.dto.CustomerPaymentSummaryResponse;
import com.careflow.payment.dto.PaymentResponse;
import com.careflow.payment.entity.Payment;
import com.careflow.payment.repository.PaymentRepository;
import com.careflow.report.domain.entity.WorkReport;
import com.careflow.report.repository.WorkReportRepository;
import com.careflow.user.entity.User;
import com.careflow.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.NoSuchElementException;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PaymentService {

    private final PaymentRepository paymentRepository;
    private final AsRequestRepository asRequestRepository;
    private final UserRepository userRepository;
    private final WorkReportRepository workReportRepository;

    /**
     * 고객 결제 처리 (Phase 1: MOCK — PG 호출 없이 즉시 SUCCESS)
     *
     * 처리 순서:
     * 1. A/S 요청 조회 및 본인 소유 검증
     * 2. COMPLETED 상태 여부 확인 (결제 가능한 선행 상태)
     * 3. 작업 완료 보고서 조회 및 고객 승인 여부 확인 (C-21, C-22)
     * 4. 중복 결제 방지
     * 5. Payment row 생성 (status=READY) — 금액은 work_reports.final_amount 사용
     * 6. 즉시 SUCCESS 전환 (pg_provider=MOCK, paid_at=NOW())
     * 7. as_requests.status → PAID
     */
    @Transactional
    public PaymentResponse processPayment(Long customerId, Long requestId)
            throws IllegalAccessException {

        // 1. A/S 요청 조회
        AsRequest asRequest = asRequestRepository.findById(requestId)
                .orElseThrow(() -> new NoSuchElementException("존재하지 않는 A/S 요청입니다."));

        // 본인 요청인지 검증 — 타인의 request_id로 결제 시도 차단
        if (!asRequest.getCustomer().getId().equals(customerId)) {
            throw new IllegalAccessException("본인의 A/S 요청에 대해서만 결제할 수 있습니다.");
        }

        // 2. COMPLETED 상태인지 확인 (markPaid 내부에도 가드가 있으나 명시적 메시지를 위해 선행 검증)
        if (asRequest.getStatus() != AsStatus.COMPLETED) {
            throw new IllegalStateException(
                    "작업이 완료된(COMPLETED) 상태에서만 결제할 수 있습니다. (현재 상태: " + asRequest.getStatus() + ")");
        }

        // 3. 작업 완료 보고서 조회 (C-21: 보고서가 있어야 결제 가능)
        WorkReport workReport = workReportRepository.findByAsRequest_Id(requestId)
                .orElseThrow(() -> new NoSuchElementException("작업 완료 보고서가 존재하지 않습니다."));

        // 고객 승인 여부 확인 (C-22: customer_approved=true 인 보고서만 결제 허용)
        if (!workReport.isCustomerApproved()) {
            throw new IllegalStateException("고객 승인이 완료된 보고서만 결제할 수 있습니다.");
        }

        // 4. 중복 결제 방지
        if (paymentRepository.existsByAsRequest_Id(requestId)) {
            throw new IllegalStateException("이미 결제가 완료된 요청입니다.");
        }

        // 5. 고객 엔티티 조회 (Payment FK 세팅용)
        User customer = userRepository.findById(customerId)
                .orElseThrow(() -> new NoSuchElementException("존재하지 않는 사용자입니다."));

        // 결제 금액은 dto가 아닌 work_reports.final_amount 기준으로 확정 (C-22)
        int finalAmount = workReport.getFinalAmount();

        // 6. Payment row 생성 (READY) 및 즉시 SUCCESS 전환
        Payment payment = Payment.create(asRequest, customer, finalAmount);
        paymentRepository.save(payment);
        payment.markSuccess();  // MOCK: PG 호출 없이 바로 SUCCESS

        // 7. A/S 요청 상태 COMPLETED → PAID
        asRequest.markPaid();

        return new PaymentResponse(
                payment.getId(),
                requestId,
                payment.getAmount(),
                payment.getStatus().name(),
                payment.getPgProvider().name(),
                payment.getPaidAt()
        );
    }

    /**
     * 고객 결제 요약 KPI 조회 (customerId 기준 — 본인 데이터만)
     * - totalAmount: status=SUCCESS 결제 전체 합계
     * - thisMonthAmount: 위 중 이번 달(paid_at 기준) 합계
     * - unpaidCount: status=COMPLETED (결제 대기) A/S 요청 건수
     */
    @Transactional(readOnly = true)
    public CustomerPaymentSummaryResponse getPaymentSummary(Long customerId) {

        long totalAmount = paymentRepository.sumSuccessAmountByCustomerId(customerId);

        // 이번 달 1일 00:00 ~ 다음 달 1일 00:00 (H2/MySQL 양쪽 호환을 위해 범위 비교 사용)
        LocalDateTime monthStart = LocalDate.now().withDayOfMonth(1).atStartOfDay();
        LocalDateTime monthEnd = monthStart.plusMonths(1);
        long thisMonthAmount = paymentRepository.sumSuccessAmountByCustomerIdAndPaidAtBetween(
                customerId, monthStart, monthEnd);

        long unpaidCount = asRequestRepository.countByCustomer_IdAndStatus(customerId, AsStatus.COMPLETED);

        long partsAmount = workReportRepository.sumPartsAmountByCustomerId(customerId);
        long otherAmount = totalAmount - partsAmount;

        return new CustomerPaymentSummaryResponse(totalAmount, thisMonthAmount, unpaidCount, partsAmount, otherAmount);
    }
}
