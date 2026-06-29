package com.careflow.notification.event;

import com.careflow.notification.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Slf4j
@Component
@RequiredArgsConstructor
public class NotificationEventListener {

    private final NotificationService notificationService;

    // 🌟 핵심: 본 트랜잭션이 COMMIT 된 후에만 실행되며, 비동기 스레드에서 동작하여 본 작업의 응답 속도에 영향을 주지 않음
    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleNotificationEvent(AsStatusNotificationEvent event) {
        try {
            notificationService.send(event.receiver(), "AS_STATUS", event.title(), event.body());
        } catch (Exception e) {
            // 알림 발송에 실패하더라도 이미 DB 커밋은 완료되었으며, 본 작업에 영향을 주지 않음
            log.error("SSE 알림 발송 중 에러 발생 (수신자 ID: {})", event.receiver().getId(), e);
        }
    }
}