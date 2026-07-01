package com.careflow.agency.scheduler;

import org.quartz.*;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 월별 정산 스케줄러 Quartz 설정
 *
 * - Cron: 0 0 1 1 * ?  →  매월 1일 01시 00분 00초
 * - Misfire 정책: FireAndProceed — 서버 점검 등으로 1일 01시를 놓쳤을 경우
 *   서버 재기동 시 즉시 1회 실행하여 전월 정산 누락을 방지한다.
 */
@Configuration
public class SettlementSchedulerConfig {

    @Bean
    public JobDetail settlementGenerationDetail() {
        return JobBuilder.newJob(SettlementGenerationJob.class)
                .withIdentity("settlementGenerationJob", "settlement")
                .withDescription("월별 정산 자동 생성 — 전월 결제 완료 건 기준 PENDING 정산 일괄 INSERT")
                .storeDurably()
                .build();
    }

    @Bean
    public Trigger settlementGenerationTrigger(
            @Qualifier("settlementGenerationDetail") JobDetail jobDetail) {

        return TriggerBuilder.newTrigger()
                .forJob(jobDetail)
                .withIdentity("settlementGenerationTrigger", "settlement")
                .withDescription("매월 1일 01:00 실행")
                // Cron: 초(0) 분(0) 시(1) 일(1) 월(*) 요일(?) — 매월 1일 새벽 1시
                .withSchedule(CronScheduleBuilder.cronSchedule("0 0 1 1 * ?")
                        .withMisfireHandlingInstructionFireAndProceed())
                .build();
    }
}
