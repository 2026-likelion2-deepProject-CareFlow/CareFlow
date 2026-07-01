package com.careflow.agency.scheduler;

import com.careflow.assignment.entity.AsAssignment;
import com.careflow.assignment.repository.AsAssignmentRepository;
import com.careflow.payment.entity.Payment;
import com.careflow.payment.repository.PaymentRepository;
import com.careflow.settlement.entity.Settlement;
import com.careflow.settlement.repository.SettlementRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.List;

/**
 * 월별 정산 자동 생성 서비스
 *
 * Quartz Job(SettlementGenerationJob)에서 호출되며,
 * 전월 결제 완료 건을 조회하여 PENDING 상태의 Settlement 레코드를 생성한다.
 *
 * 트랜잭션을 Job 클래스가 아닌 Service 에서 관리하는 이유:
 * Quartz Job 은 Spring 컨텍스트 밖에서 생성될 수 있어 트랜잭션 AOP 가 적용되지 않을 수 있으므로
 * Service 레이어에서 @Transactional 을 명시한다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SettlementGenerationService {

    private final PaymentRepository paymentRepository;
    private final SettlementRepository settlementRepository;
    private final AsAssignmentRepository asAssignmentRepository;


    /**
     * 정산 일괄 생성 진입점 — SettlementGenerationJob 에서 호출
     *
     * @param targetMonth 정산 대상 월 (통상 전월, 테스트 시 임의 지정 가능)
     * @return 처리 결과 요약
     */
    @Transactional
    public Result generateForMonth(YearMonth targetMonth) {
        LocalDateTime from = targetMonth.atDay(1).atStartOfDay();
        LocalDateTime to   = targetMonth.plusMonths(1).atDay(1).atStartOfDay();

        log.info("[월별 정산] {}년 {}월 정산 생성 시작 (대상 기간: {} ~ {})",
                targetMonth.getYear(), targetMonth.getMonthValue(), from, to);

        // 정산 미생성 결제 건 조회 (payment.status=SUCCESS + NOT EXISTS settlement)
        List<Payment> targets = paymentRepository.findSettlementTargets(from, to);
        log.info("[월별 정산] 대상 결제 건 조회 완료: 총 {}건", targets.size());

        int created = 0;
        int skipped = 0;
        int failed  = 0;

        for (Payment payment : targets) {
            try {
                boolean saved = createSettlement(payment);
                if (saved) created++;
                else       skipped++;
            } catch (Exception e) {
                failed++;
                log.error("[월별 정산] 정산 생성 실패 — payment_id={}, 원인: {}",
                        payment.getId(), e.getMessage(), e);
            }
        }

        log.info("[월별 정산] 정산 생성 완료: {}건 / 스킵(기사 없음): {}건 / 오류: {}건",
                created, skipped, failed);

        return new Result(created, skipped, failed);
    }

    /**
     * Payment 1건에 대한 Settlement 생성
     *
     * 담당 기사 조회 전략:
     *   as_assignments 테이블에서 request_id 일치 + status='COMPLETED' 중
     *   assignedAt 최신 1건을 기사로 확정한다.
     *   배정 내역이 없으면 스킵한다.
     *
     * @return true: 생성 완료, false: 스킵
     */
    private boolean createSettlement(Payment payment) {
        Long requestId = payment.getAsRequest().getId();

        // COMPLETED 상태 배정 중 가장 최신 1건으로 담당 기사 확정
        List<AsAssignment> assignments = asAssignmentRepository
                .findByAsRequest_Id(requestId)
                .stream()
                .filter(a -> "COMPLETED".equals(a.getStatus()))
                .sorted((a, b) -> b.getAssignedAt().compareTo(a.getAssignedAt()))
                .toList();

        if (assignments.isEmpty()) {
            // 완료 처리된 배정이 없으면 정산 생성 불가 — 경고 로그 후 스킵
            log.warn("[월별 정산] 담당 기사 없음으로 스킵 — payment_id={}, request_id={}",
                    payment.getId(), requestId);
            return false;
        }

        AsAssignment assignment = assignments.get(0);

        int gross        = payment.getAmount();
        // agencies.agency_fee_rate 를 플랫폼 수수료율 및 대행사 수수료율 모두에 적용
        BigDecimal agencyFeeRate = BigDecimal.valueOf(assignment.getAgency().getAgencyFeeRate());

        // 수수료 계산 — 소수점 반올림(HALF_UP), 원 단위 정수로 변환
        int platformFee = agencyFeeRate
                .multiply(BigDecimal.valueOf(gross))
                .divide(BigDecimal.valueOf(100), 0, RoundingMode.HALF_UP)
                .intValue();

        int agencyFee = agencyFeeRate
                .multiply(BigDecimal.valueOf(gross))
                .divide(BigDecimal.valueOf(100), 0, RoundingMode.HALF_UP)
                .intValue();

        int engineerNetAmount = gross - platformFee - agencyFee;

        Settlement settlement = Settlement.create(
                payment,
                payment.getAsRequest(),
                assignment.getEngineer(),
                assignment.getAgency(),
                gross,
                platformFee,
                agencyFeeRate,   // feeRate(플랫폼 수수료율) 에도 agency_fee_rate 적용
                agencyFee,
                agencyFeeRate,
                engineerNetAmount
        );

        settlementRepository.save(settlement);

        log.debug("[월별 정산] Settlement 생성 — payment_id={}, engineer={}, gross={}원, net={}원",
                payment.getId(), assignment.getEngineer().getName(), gross, engineerNetAmount);

        return true;
    }

    /** 처리 결과 요약 레코드 */
    public record Result(int created, int skipped, int failed) {}
}
