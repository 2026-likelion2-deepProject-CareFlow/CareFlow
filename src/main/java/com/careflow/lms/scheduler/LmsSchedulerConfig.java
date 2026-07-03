package com.careflow.lms.scheduler;


import com.careflow.quiz.scheduler.QuizYearRolloverJob;
import lombok.RequiredArgsConstructor;
import org.quartz.*;
import org.springframework.beans.factory.annotation.Qualifier;
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
    public Trigger lmsResetTrigger(@Qualifier("lmsResetDetail") JobDetail lmsResetJobDetail) {
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


    @Bean
    public JobDetail quizYearRolloverDetail() {
        return JobBuilder.newJob(QuizYearRolloverJob.class)
                .withIdentity("quizYearRollover", "quiz")
                .withDescription("연간 OX퀴즈 문항 연도 전환 (전년도 아카이브 + 신년도 활성화)")
                .storeDurably()
                .build();
    }

    @Bean
    public Trigger quizYearRolloverTrigger(
            @Qualifier("quizYearRolloverDetail") JobDetail quizYearRolloverDetail) {
        return TriggerBuilder.newTrigger()
                .forJob(quizYearRolloverDetail)
                .withIdentity("quizYearRolloverTrigger", "quiz")
                .withDescription("매년 1월 1일 00:00 OX퀴즈 연도 전환")
                .withSchedule(
                        CronScheduleBuilder
                                .cronSchedule("0 0 0 1 1 ? *")
                                .withMisfireHandlingInstructionFireAndProceed()
                )
                .build();
    }
}