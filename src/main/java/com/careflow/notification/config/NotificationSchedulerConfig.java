package com.careflow.notification.config;

import com.careflow.notification.scheduler.ConsumableAlertJob;
import com.careflow.notification.scheduler.WarrantyAlertJob;
import org.quartz.*;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class NotificationSchedulerConfig {

    // ─────────────────────────────────────────────
    // 1. 무상 A/S 만료 임박 알림 (매일 오전 9시)
    // ─────────────────────────────────────────────
    @Bean
    public JobDetail warrantyAlertDetail() {
        return JobBuilder.newJob(WarrantyAlertJob.class)
                .withIdentity("warrantyAlertJob", "notification")
                .withDescription("무상 A/S 만료 임박 알림 발송")
                .storeDurably() // 트리거가 없어도 Job을 삭제하지 않고 유지
                .build();
    }

    @Bean
    public Trigger warrantyAlertTrigger(@Qualifier("warrantyAlertDetail") JobDetail warrantyAlertDetail) {
        return TriggerBuilder.newTrigger()
                .forJob(warrantyAlertDetail)
                .withIdentity("warrantyAlertTrigger", "notification")
                .withDescription("매일 오전 9시 정각 실행")
                // Cron 식: 초 분 시 일 월 요일 (0 0 9 * * ? = 매일 09시 00분 00초)
                .withSchedule(CronScheduleBuilder.cronSchedule("0 0 9 * * ?")
                        // 🌟 서버 점검 등으로 오전 9시에 서버가 꺼져 있었다면, 켜지자마자 즉시 1회 실행하여 누락 방지!
                        .withMisfireHandlingInstructionFireAndProceed())
                .build();
    }

    // ─────────────────────────────────────────────
    // 2. 소모품 교체 주기 도래 알림 (매일 오전 9시)
    // ─────────────────────────────────────────────
    @Bean
    public JobDetail consumableAlertDetail() {
        return JobBuilder.newJob(ConsumableAlertJob.class)
                .withIdentity("consumableAlertJob", "notification")
                .withDescription("소모품 교체 주기 도래 알림 발송")
                .storeDurably()
                .build();
    }

    @Bean
    public Trigger consumableAlertTrigger(@Qualifier("consumableAlertDetail") JobDetail consumableAlertDetail) {
        return TriggerBuilder.newTrigger()
                .forJob(consumableAlertDetail)
                .withIdentity("consumableAlertTrigger", "notification")
                .withDescription("매일 오전 9시 정각 실행")
                .withSchedule(CronScheduleBuilder.cronSchedule("0 0 9 * * ?")
                        .withMisfireHandlingInstructionFireAndProceed())
                .build();
    }
}