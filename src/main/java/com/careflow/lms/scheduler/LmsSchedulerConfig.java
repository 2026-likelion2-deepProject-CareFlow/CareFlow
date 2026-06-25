package com.careflow.lms.scheduler;


import lombok.RequiredArgsConstructor;
import org.quartz.*;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@RequiredArgsConstructor
public class LmsSchedulerConfig {

    /**
     * JobDetail — LmsReset 등록
     */
    @Bean
    public JobDetail lmsResetDetail() {
        return JobBuilder.newJob(LmsReset.class)
                .withIdentity("lmsReset", "lms")
                .withDescription("연간 LMS 이수 상태 초기화")
                .storeDurably()
                .build();
    }

    /**
     * Trigger — 매년 1월 1일 00:00 실행
     * Cron: 0 0 0 1 1 ? *
     *       초 분 시 일 월 요일 연도
     */
    @Bean
    public Trigger lmsResetTrigger(JobDetail lmsResetJobDetail) {
        return TriggerBuilder.newTrigger()
                .forJob(lmsResetJobDetail)
                .withIdentity("lmsResetTrigger", "lms")
                .withDescription("매년 1월 1일 00:00 LMS 초기화")
                .withSchedule(
                        CronScheduleBuilder
                                .cronSchedule("0 0 0 1 1 ? *")
                                .withMisfireHandlingInstructionFireAndProceed() // 서버 다운 후 재기동 시 누락된 실행 즉시 처리
                )
                .build();
    }
}