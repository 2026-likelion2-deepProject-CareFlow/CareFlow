package com.careflow.agency.scheduler;

import com.careflow.agency.entity.Agencies;
import com.careflow.assignment.entity.AsAssignment;
import com.careflow.assignment.repository.AsAssignmentRepository;
import com.careflow.payment.entity.Payment;
import com.careflow.payment.repository.PaymentRepository;
import com.careflow.settlement.entity.EngineerPayout;
import com.careflow.settlement.entity.PlatformSettlement;
import com.careflow.settlement.entity.Settlement;
import com.careflow.settlement.repository.EngineerPayoutRepository;
import com.careflow.settlement.repository.PlatformSettlementRepository;
import com.careflow.settlement.repository.SettlementRepository;
import com.careflow.user.entity.User;
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

    private final SettlementRepository settlementRepository;
    private final PlatformSettlementRepository platformSettlementRepository;
    private final EngineerPayoutRepository engineerPayoutRepository;

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

        // 1. 해당 월에 결제되어 생성된 Settlement 중 아직 통계에 집계(바인딩)되지 않은 건 조회
        List<Settlement> unaggregatedSettlements = settlementRepository.findUnaggregatedSettlements(from, to);

        if (unaggregatedSettlements.isEmpty()) {
            log.info("[월별 정산 집계] 집계 대상 정산 내역이 없습니다.");
            return new Result(0, 0, 0, 0, 0);
        }

        // 2. 대행사 단위로 그룹핑하여 PlatformSettlement 통계 묶기
        int platformSettlementsCreated = generatePlatformSettlements(targetMonth, unaggregatedSettlements);

        // 3. 대행사+기사 단위로 그룹핑하여 EngineerPayout 통계 묶기
        int engineerPayoutsCreated = generateEngineerPayouts(targetMonth, unaggregatedSettlements);

        log.info("[월별 정산 집계] 집계 완료: 바인딩된 정산 건수 {}, 신규 대행사 정산 {}, 신규 기사 지급 {}",
                unaggregatedSettlements.size(), platformSettlementsCreated, engineerPayoutsCreated);

        // created 자리에 집계된 총 Settlement 개수를 반환하여 로그에서 추적 가능하게 함
        return new Result(unaggregatedSettlements.size(), 0, 0, platformSettlementsCreated, engineerPayoutsCreated);
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

            int totalGross  = settlements.stream().mapToInt(Settlement::getGrossAmount).sum();
            int totalFee    = settlements.stream().mapToInt(Settlement::getPlatformFee).sum();
            // [v14] CareFlow가 대행사에 실제 지급할 금액 = SUM(agency_fee) + SUM(engineer_net_amount)
            int totalPayout = settlements.stream()
                    .mapToInt(s -> s.getAgencyFee() + s.getEngineerNetAmount())
                    .sum();
            int count       = settlements.size();

            PlatformSettlement platformSettlement = platformSettlementRepository
                    .findByAgency_IdAndSettlementYearAndSettlementMonth(agency.getId(), year, month)
                    .orElse(null);

            if (platformSettlement == null) {
                platformSettlement = PlatformSettlement.create(
                        agency, year, month, totalGross, totalFee, totalPayout, count);
                platformSettlementRepository.save(platformSettlement);
                newlyCreated++;
            } else if ("PENDING".equals(platformSettlement.getStatus())) {
                // 스케줄러 재실행(Misfire 복구) 등으로 동일 기간에 대해 추가 집계된 경우 — 기존 합계에 누적
                platformSettlement.accumulate(totalGross, totalFee, totalPayout, count);
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
     * 이번 배치에서 생성된 Settlement 목록을 (대행사, 기사) 단위로 GROUP BY 집계하여
     * engineer_payouts(대행사→기사 월별 지급 배치) 레코드를 생성한다.
     *
     * platform_settlements 집계와 완전히 독립적으로 수행된다 — 같은 Settlement가 두 배치에
     * 동시에 속할 수 있으며(platform_settlement_id, engineer_payout_id 각각 별도 FK), 한쪽의
     * 생성/누적 실패가 다른 쪽에 영향을 주지 않는다.
     *
     * @return 신규 생성된 EngineerPayout 건수 (기존 레코드에 누적만 한 경우는 미포함)
     */
    private int generateEngineerPayouts(YearMonth targetMonth, List<Settlement> createdSettlements) {
        if (createdSettlements.isEmpty()) {
            return 0;
        }

        int year  = targetMonth.getYear();
        int month = targetMonth.getMonthValue();

        Map<AgencyEngineerKey, List<Settlement>> byAgencyAndEngineer = createdSettlements.stream()
                .collect(Collectors.groupingBy(s -> new AgencyEngineerKey(s.getAgency().getId(), s.getEngineer().getId())));

        log.info("[기사 지급 집계] 대행사·기사 조합 {}건 대상 집계 시작 — 대상 Settlement {}건",
                byAgencyAndEngineer.size(), createdSettlements.size());

        int newlyCreated = 0;

        for (List<Settlement> settlements : byAgencyAndEngineer.values()) {
            Agencies agency = settlements.get(0).getAgency();
            User engineer = settlements.get(0).getEngineer();

            int totalNet = settlements.stream().mapToInt(Settlement::getEngineerNetAmount).sum();
            int count    = settlements.size();

            EngineerPayout engineerPayout = engineerPayoutRepository
                    .findByAgency_IdAndEngineer_IdAndPayoutYearAndPayoutMonth(agency.getId(), engineer.getId(), year, month)
                    .orElse(null);

            if (engineerPayout == null) {
                engineerPayout = EngineerPayout.create(agency, engineer, year, month, totalNet, count);
                engineerPayoutRepository.save(engineerPayout);
                newlyCreated++;
            } else if ("PENDING".equals(engineerPayout.getStatus())) {
                // 스케줄러 재실행(Misfire 복구) 등으로 동일 기간에 대해 추가 집계된 경우 — 기존 합계에 누적
                engineerPayout.accumulate(totalNet, count);
            } else {
                // 이미 PAID/DISPUTED로 상태가 바뀐 뒤라면 재무 데이터 무결성을 위해 합계를 임의로 변경하지 않음
                log.warn("[기사 지급 집계] 이미 {} 상태인 engineer_payout에 대해 신규 집계 스킵 — agency={}, engineer={}, {}년 {}월. "
                                + "해당 {}건의 settlement는 engineer_payout 미할당 상태로 남습니다.",
                        engineerPayout.getStatus(), agency.getId(), engineer.getId(), year, month, count);
                continue;
            }

            for (Settlement settlement : settlements) {
                settlement.assignEngineerPayout(engineerPayout);
            }

            log.debug("[기사 지급 집계] agency={}, engineer={}, {}년 {}월, 건수={}, net={}원",
                    agency.getAgencyName(), engineer.getName(), year, month, count, totalNet);
        }

        log.info("[기사 지급 집계] 생성 완료: {}건", newlyCreated);

        return newlyCreated;
    }

    /** (agency_id, engineer_id) 그룹핑 키 */
    private record AgencyEngineerKey(Long agencyId, Long engineerId) {}

    /** 처리 결과 요약 레코드 */
    public record Result(int created, int skipped, int failed, int platformSettlementsCreated, int engineerPayoutsCreated) {}
}
