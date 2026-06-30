package com.careflow.notification.scheduler;

import com.careflow.appliance.entity.ConsumableAlert;
import com.careflow.appliance.repository.ConsumableAlertRepository;
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
 * [Quartz Job] 소모품 교체 주기 도래 알림 발송
 * - 매일 오전 9시에 실행되어, 교체 주기가 지났거나 도래한 소모품 알림 발송
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ConsumableAlertJob implements Job {

    private final ConsumableAlertRepository consumableAlertRepository;
    private final NotificationService notificationService;

    @Override
    @Transactional // 🌟 쓰기 트랜잭션: 알림 발송 후 next_alert_date 를 갱신(UPDATE)해야 하므로 필수!
    public void execute(JobExecutionContext context) throws JobExecutionException {
        try {
            LocalDate today = LocalDate.now();
            log.info("[소모품 교체 알림] {} 기준 소모품 알림 발송 시작", today);

            // Phase 1에서 만든 <= 조건의 쿼리 호출 (서버 장애로 며칠 밀려도 누락 없이 발송 가능)
            List<ConsumableAlert> alerts = consumableAlertRepository.findAlertsToNotify(today);

            if (alerts.isEmpty()) {
                log.info("[소모품 교체 알림] 발송 대상이 없습니다.");
                return;
            }

            for (ConsumableAlert alert : alerts) {
                String title = "소모품 교체 시기 안내";
                String body = String.format("고객님의 [%s] 가전의 '%s' 교체 주기가 도래했습니다. 쾌적한 사용을 위해 소모품 상태를 확인해 주세요.",
                        alert.getAppliance().getModelName(), alert.getConsumableName());

                // 알림 발송
                notificationService.send(alert.getUser(), "CONSUMABLE", title, body);

                // 🌟 중요: 알림을 발송한 뒤, 다음 알림일(nextAlertDate)을 주기(cycleMonths)만큼 연장
                // (만약 연장하지 않으면, 내일 또 <= today 조건에 걸려서 매일 알림 폭탄이 발송됨)
                alert.updateAfterReplacement();
            }

            log.info("[소모품 교체 알림] 총 {}건 알림 발송 및 다음 교체일 자동 갱신 완료", alerts.size());

        } catch (Exception e) {
            log.error("[소모품 교체 알림] 스케줄러 실행 중 오류 발생: {}", e.getMessage(), e);
            throw new JobExecutionException(e);
        }
    }
}