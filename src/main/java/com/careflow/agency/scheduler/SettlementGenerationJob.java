package com.careflow.agency.scheduler;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.quartz.Job;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.springframework.stereotype.Component;

import java.time.YearMonth;

/**
 * [Quartz Job] 월별 정산 자동 생성
 *
 * 매월 1일 새벽 1시에 실행되어, 전월 결제 완료(Payment.SUCCESS) 건을
 * 기준으로 Settlement(PENDING) 레코드를 일괄 생성한다.
 *
 * 비즈니스 로직은 SettlementGenerationService 에 위임한다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SettlementGenerationJob implements Job {

    private final SettlementGenerationService settlementGenerationService;

    @Override
    public void execute(JobExecutionContext context) throws JobExecutionException {
        // 실행 시점 기준 전월이 정산 대상
        YearMonth targetMonth = YearMonth.now().minusMonths(1);

        long startMs = System.currentTimeMillis();
        log.info("[월별 정산] Job 시작 — 대상 월: {}년 {}월",
                targetMonth.getYear(), targetMonth.getMonthValue());

        try {
            SettlementGenerationService.Result result =
                    settlementGenerationService.generateForMonth(targetMonth);

            long elapsedMs = System.currentTimeMillis() - startMs;
            log.info("[월별 정산] Job 완료 — 생성: {}건, 스킵: {}건, 오류: {}건, 플랫폼정산집계: {}건, 기사지급집계: {}건, 소요시간: {}ms",
                    result.created(), result.skipped(), result.failed(),
                    result.platformSettlementsCreated(), result.engineerPayoutsCreated(), elapsedMs);

        } catch (Exception e) {
            log.error("[월별 정산] Job 실행 중 예상치 못한 오류 발생: {}", e.getMessage(), e);
            // JobExecutionException 으로 래핑하여 Quartz 에 실패 전파
            throw new JobExecutionException(e);
        }
    }
}
