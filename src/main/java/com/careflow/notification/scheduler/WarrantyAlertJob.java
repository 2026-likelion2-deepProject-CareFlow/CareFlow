package com.careflow.notification.scheduler;

import com.careflow.appliance.entity.Appliance;
import com.careflow.appliance.repository.ApplianceRepository;
import com.careflow.notification.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.quartz.Job;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

/**
 * [Quartz Job] 무상 A/S 만료 임박 알림 발송
 * - 매일 오전 9시에 실행되어, 만료일이 정확히 30일/7일 남은 고객에게 각각 알림을 발송합니다.
 * - 두 시점 모두 findByWarrantyEndDateWithUser가 등호(=) 조건이라 매일 한 번씩만 걸리며 중복 발송되지 않습니다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WarrantyAlertJob implements Job {

    private final ApplianceRepository applianceRepository;
    private final NotificationService notificationService;

    // 알림 발송 시점 — 만료 30일 전, 7일 전 2회
    private static final int[] ALERT_DAYS_BEFORE = { 30, 7 };

    @Override
    @Transactional
    public void execute(JobExecutionContext context) throws JobExecutionException {
        try {
            int totalSent = 0;

            for (int daysBefore : ALERT_DAYS_BEFORE) {
                LocalDate targetDate = LocalDate.now().plusDays(daysBefore);
                log.info("[무상 A/S 알림] {}일 전 기준({}) 만료 예정 가전 조회 시작", daysBefore, targetDate);

                // Phase 2에서 만든 N+1 방지 쿼리 호출
                List<Appliance> appliances = applianceRepository.findByWarrantyEndDateWithUser(targetDate);

                if (appliances.isEmpty()) {
                    log.info("[무상 A/S 알림] {}일 전 기준 발송 대상이 없습니다.", daysBefore);
                    continue;
                }

                for (Appliance appliance : appliances) {
                    String title = "무상 A/S 기간 만료 임박 안내";
                    String periodLabel = (daysBefore >= 30) ? "한 달 뒤" : daysBefore + "일 뒤";
                    String body = String.format("고객님의 [%s %s] 무상 A/S 기간이 %s(%s) 만료됩니다. 점검이 필요하시면 미리 A/S를 신청해 주세요.",
                            appliance.getBrand(), appliance.getModelName(), periodLabel, targetDate);

                    // 기존 로직 재사용 (알림 DB 저장 + SSE 실시간 전송)
                    notificationService.send(appliance.getUser(), "WARRANTY", title, body);
                }

                totalSent += appliances.size();
                log.info("[무상 A/S 알림] {}일 전 기준 {}건 알림 발송 완료", daysBefore, appliances.size());
            }

            log.info("[무상 A/S 알림] 총 {}건 알림 발송 완료", totalSent);

        } catch (Exception e) {
            log.error("[무상 A/S 알림] 스케줄러 실행 중 오류 발생: {}", e.getMessage(), e);
            throw new JobExecutionException(e);
        }
    }
}
