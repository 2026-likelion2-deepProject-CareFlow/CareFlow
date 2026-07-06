package com.careflow.payment.service;

import com.careflow.as_request.entity.AsRequest;
import com.careflow.as_request.repository.AsRequestRepository;
import com.careflow.assignment.entity.AsAssignment;
import com.careflow.assignment.repository.AsAssignmentRepository;
import com.careflow.common.enums.AsStatus;
import com.careflow.payment.dto.CustomerMonthlyPaymentResponse;
import com.careflow.payment.dto.CustomerPaymentSummaryResponse;
import com.careflow.payment.dto.PaymentResponse;
import com.careflow.payment.entity.Payment;
import com.careflow.payment.repository.PaymentRepository;
import com.careflow.report.domain.entity.WorkReport;
import com.careflow.report.repository.WorkReportRepository;
import com.careflow.settlement.entity.Settlement;
import com.careflow.settlement.repository.SettlementRepository;
import com.careflow.user.entity.User;
import com.careflow.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PaymentService {

    private final PaymentRepository paymentRepository;
    private final AsRequestRepository asRequestRepository;
    private final UserRepository userRepository;
    private final WorkReportRepository workReportRepository;
    private final AsAssignmentRepository asAssignmentRepository;
    private final SettlementRepository settlementRepository;

    // 🌟 플랫폼 고정 수수료율 (10%)
    private static final BigDecimal PLATFORM_FEE_RATE = BigDecimal.valueOf(0.10);

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

        // 8. 결제 즉시 정산(Settlement) 데이터 PENDING 상태로 생성
        List<AsAssignment> allAssignments = asAssignmentRepository.findByAsRequest_Id(requestId);

        List<AsAssignment> assignments = allAssignments.stream()
                .filter(a -> "COMPLETED".equals(a.getStatus()))
                .sorted((a, b) -> b.getAssignedAt().compareTo(a.getAssignedAt()))
                .toList();

        if (assignments.isEmpty()) {
            // 배정을 COMPLETED로 전이하는 로직이 없던 구버전에서 이미 보고서가
            // 제출된 레거시 건은 배정이 ACCEPTED 상태로 남아있을 수 있다.
            // as_requests.status가 이미 COMPLETED까지 온 시점이므로, 가장 최근
            // ACCEPTED 배정을 여기서 완료 처리해 자연스럽게 복구한다.
            AsAssignment legacyAssignment = allAssignments.stream()
                    .filter(a -> "ACCEPTED".equals(a.getStatus()))
                    .sorted((a, b) -> b.getAssignedAt().compareTo(a.getAssignedAt()))
                    .findFirst()
                    .orElseThrow(() -> new IllegalStateException("해당 요청에 대해 완료된 배차 내역이 없어 정산을 생성할 수 없습니다."));
            legacyAssignment.complete();
            assignments = List.of(legacyAssignment);
        }
        AsAssignment assignment = assignments.get(0);

        BigDecimal agencyFeeRate = BigDecimal.valueOf(assignment.getAgency().getAgencyFeeRate());

        int platformFee = PLATFORM_FEE_RATE.multiply(BigDecimal.valueOf(finalAmount))
                .setScale(0, RoundingMode.HALF_UP).intValue();

        int agencyFee = agencyFeeRate.multiply(BigDecimal.valueOf(finalAmount))
                .setScale(0, RoundingMode.HALF_UP).intValue();

        int engineerNetAmount = finalAmount - platformFee - agencyFee;

        Settlement settlement = Settlement.create(
                payment,
                asRequest,
                assignment.getEngineer(),
                assignment.getAgency(),
                finalAmount,
                platformFee,
                PLATFORM_FEE_RATE,
                agencyFee,
                agencyFeeRate,
                engineerNetAmount
        );
        settlementRepository.save(settlement); // 정산 데이터 생성 끝

        return new PaymentResponse(
                payment.getId(),
                requestId,
                asRequest.getAppliance().getBrand(),
                asRequest.getAppliance().getModelName(),
                workReport.getEngineer().getName(),
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

    /**
     * 고객 월별 결제액 추이 조회 (customerId 기준 — 본인 데이터만)
     * - 이번 달을 포함한 최근 6개월, 오래된 달부터 최신 달 순으로 반환
     * - 결제 내역이 없는 달은 0으로 채움
     */
    @Transactional(readOnly = true)
    public List<CustomerMonthlyPaymentResponse> getMonthlyPayments(Long customerId) {

        YearMonth thisMonth = YearMonth.now();
        List<YearMonth> months = IntStream.rangeClosed(0, 5)
                .mapToObj(i -> thisMonth.minusMonths(5 - i))
                .toList();

        LocalDateTime from = months.get(0).atDay(1).atStartOfDay();
        LocalDateTime to = thisMonth.plusMonths(1).atDay(1).atStartOfDay();

        Map<YearMonth, Long> amountsByMonth = paymentRepository
                .findSuccessByCustomerIdAndPaidAtBetween(customerId, from, to).stream()
                .collect(Collectors.groupingBy(
                        p -> YearMonth.from(p.getPaidAt()),
                        Collectors.summingLong(Payment::getAmount)));

        return months.stream()
                .map(month -> new CustomerMonthlyPaymentResponse(
                        month.format(DateTimeFormatter.ofPattern("yyyy-MM")),
                        amountsByMonth.getOrDefault(month, 0L)))
                .toList();
    }

    /**
     * 고객 결제 내역 전체 조회 (customerId 기준 — 본인 데이터만)
     * - 상태 필터 없이 전체 반환, 생성일 기준 최신순 정렬
     */
    @Transactional(readOnly = true)
    public List<PaymentResponse> getPaymentList(Long customerId) {

        return paymentRepository.findByCustomer_IdOrderByCreatedAtDesc(customerId).stream()
                .map(p -> new PaymentResponse(
                        p.getId(),
                        p.getAsRequest().getId(),
                        p.getAsRequest().getAppliance().getBrand(),
                        p.getAsRequest().getAppliance().getModelName(),
                        p.getAsRequest().getWorkReport() != null
                                ? p.getAsRequest().getWorkReport().getEngineer().getName()
                                : null,
                        p.getAmount(),
                        p.getStatus().name(),
                        p.getPgProvider().name(),
                        p.getPaidAt()))
                .toList();
    }
}
