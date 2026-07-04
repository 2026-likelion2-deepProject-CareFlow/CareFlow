package com.careflow.agency.scheduler;

import com.careflow.agency.entity.Agencies;
import com.careflow.assignment.entity.AsAssignment;
import com.careflow.assignment.repository.AsAssignmentRepository;
import com.careflow.payment.entity.Payment;
import com.careflow.payment.repository.PaymentRepository;
import com.careflow.settlement.entity.PlatformSettlement;
import com.careflow.settlement.entity.Settlement;
import com.careflow.settlement.repository.PlatformSettlementRepository;
import com.careflow.settlement.repository.SettlementRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

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
    private final PlatformSettlementRepository platformSettlementRepository;

    // CareFlow 플랫폼 수수료율 — 전 대행사 공통 고정값(10%). 대행사별 agencyFeeRate와는 별개
    private static final BigDecimal PLATFORM_FEE_RATE = BigDecimal.valueOf(0.10);


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
        List<Settlement> createdSettlements = new ArrayList<>();

        for (Payment payment : targets) {
            try {
                Settlement settlement = createSettlement(payment);
                if (settlement != null) {
                    created++;
                    createdSettlements.add(settlement);
                } else {
                    skipped++;
                }
            } catch (Exception e) {
                failed++;
                log.error("[월별 정산] 정산 생성 실패 — payment_id={}, 원인: {}",
                        payment.getId(), e.getMessage(), e);
            }
        }

        log.info("[월별 정산] 정산 생성 완료: {}건 / 스킵(기사 없음): {}건 / 오류: {}건",
                created, skipped, failed);

        // 이어서 생성된 Settlement를 대행사·연·월 단위로 집계해 platform_settlements 생성
        int platformSettlementsCreated = generatePlatformSettlements(targetMonth, createdSettlements);

        return new Result(created, skipped, failed, platformSettlementsCreated);
    }

    /**
     * 이번 배치에서 생성된 Settlement 목록을 대행사 단위로 GROUP BY 집계하여
     * platform_settlements(대행사→플랫폼 월별 정산) 레코드를 생성한다.
     *
     * 집계 기준(settlement_year/settlement_month)은 settlements.paid_at이 아니라
     * 이 배치 호출에 전달된 targetMonth(정산 대상 월)를 그대로 사용한다.
     * (settlements.paid_at은 기사 지급 완료 시점에만 채워지는 컬럼이라 배치 시점과 무관하게 늦어질 수 있음 —
     *  자세한 설계 배경은 docs/api/agency/settlement/platform-settlement-aggregation.md 참고)
     *
     * @return 신규 생성된 PlatformSettlement 건수 (기존 레코드에 누적만 한 경우는 미포함)
     */
    private int generatePlatformSettlements(YearMonth targetMonth, List<Settlement> createdSettlements) {
        if (createdSettlements.isEmpty()) {
            return 0;
        }

        int year  = targetMonth.getYear();
        int month = targetMonth.getMonthValue();

        Map<Long, List<Settlement>> byAgencyId = createdSettlements.stream()
                .collect(Collectors.groupingBy(s -> s.getAgency().getId()));

        log.info("[플랫폼 정산 집계] 대행사 {}곳 대상 집계 시작 — 대상 Settlement {}건",
                byAgencyId.size(), createdSettlements.size());

        int newlyCreated = 0;

        for (List<Settlement> settlements : byAgencyId.values()) {
            Agencies agency = settlements.get(0).getAgency();

            int totalGross = settlements.stream().mapToInt(Settlement::getGrossAmount).sum();
            int totalFee   = settlements.stream().mapToInt(Settlement::getPlatformFee).sum();
            int count      = settlements.size();

            PlatformSettlement platformSettlement = platformSettlementRepository
                    .findByAgency_IdAndSettlementYearAndSettlementMonth(agency.getId(), year, month)
                    .orElse(null);

            if (platformSettlement == null) {
                platformSettlement = PlatformSettlement.create(
                        agency, year, month, totalGross, totalFee, count);
                platformSettlementRepository.save(platformSettlement);
                newlyCreated++;
            } else if ("PENDING".equals(platformSettlement.getStatus())) {
                // 스케줄러 재실행(Misfire 복구) 등으로 동일 기간에 대해 추가 집계된 경우 — 기존 합계에 누적
                platformSettlement.accumulate(totalGross, totalFee, count);
            } else {
                // 이미 PAID/DISPUTED로 상태가 바뀐 뒤라면 재무 데이터 무결성을 위해 합계를 임의로 변경하지 않음
                log.warn("[플랫폼 정산 집계] 이미 {} 상태인 platform_settlement에 대해 신규 집계 스킵 — agency={}, {}년 {}월. "
                                + "해당 {}건의 settlement는 platform_settlement 미할당 상태로 남습니다.",
                        platformSettlement.getStatus(), agency.getId(), year, month, count);
                continue;
            }

            for (Settlement settlement : settlements) {
                settlement.assignPlatformSettlement(platformSettlement);
            }

            log.debug("[플랫폼 정산 집계] agency={}, {}년 {}월, 건수={}, gross={}원, fee={}원",
                    agency.getAgencyName(), year, month, count, totalGross, totalFee);
        }

        log.info("[플랫폼 정산 집계] 생성 완료: {}건", newlyCreated);

        return newlyCreated;
    }

    /**
     * Payment 1건에 대한 Settlement 생성
     *
     * 담당 기사 조회 전략:
     *   as_assignments 테이블에서 request_id 일치 + status='COMPLETED' 중
     *   assignedAt 최신 1건을 기사로 확정한다.
     *   배정 내역이 없으면 스킵한다.
     *
     * @return 생성된 Settlement, 스킵된 경우 null
     */
    private Settlement createSettlement(Payment payment) {
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
            return null;
        }

        AsAssignment assignment = assignments.get(0);

        int gross = payment.getAmount();
        // agencies.agency_fee_rate 는 대행사별 기사 수수료율(비율, 예: 0.46 = 46%) — 플랫폼 수수료율(고정 10%)과는 별개
        BigDecimal agencyFeeRate = BigDecimal.valueOf(assignment.getAgency().getAgencyFeeRate());

        // 수수료 계산 — 이미 비율(0~1)로 저장된 값이므로 100으로 나누지 않고 gross_amount에 바로 곱함
        int platformFee = PLATFORM_FEE_RATE
                .multiply(BigDecimal.valueOf(gross))
                .setScale(0, RoundingMode.HALF_UP)
                .intValue();

        int agencyFee = agencyFeeRate
                .multiply(BigDecimal.valueOf(gross))
                .setScale(0, RoundingMode.HALF_UP)
                .intValue();

        int engineerNetAmount = gross - platformFee - agencyFee;

        Settlement settlement = Settlement.create(
                payment,
                payment.getAsRequest(),
                assignment.getEngineer(),
                assignment.getAgency(),
                gross,
                platformFee,
                PLATFORM_FEE_RATE,   // feeRate — 플랫폼 고정 수수료율(10%) 스냅샷
                agencyFee,
                agencyFeeRate,
                engineerNetAmount
        );

        Settlement saved = settlementRepository.save(settlement);

        log.debug("[월별 정산] Settlement 생성 — payment_id={}, engineer={}, gross={}원, net={}원",
                payment.getId(), assignment.getEngineer().getName(), gross, engineerNetAmount);

        return saved;
    }

    /** 처리 결과 요약 레코드 */
    public record Result(int created, int skipped, int failed, int platformSettlementsCreated) {}
}
