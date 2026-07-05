package com.careflow.notification.dto;

import com.careflow.notification.entity.Notification;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;

import java.time.format.DateTimeFormatter;

@Getter
@Builder
public class NotificationResponse {
    private final Long id;
    private final String notificationId; // 예: NOT-20240618-001
    private final String type;
    private final String title;
    private final String body;
    private final String channel;
    private final String createdAt;
    // Lombok이 생성하는 게터 isRead()를 Jackson이 빈 프로퍼티 규칙으로 "is" 접두사를 제거해
    // JSON 키를 "read"로 내보내던 버그가 있었음(프론트는 "isRead" 키를 기대 — 항상 안읽음으로 보임).
    // Lombok 자동 게터를 끄고 @JsonProperty로 키를 고정한 수동 게터로 대체한다.
    @Getter(AccessLevel.NONE)
    private final boolean isRead;

    @JsonProperty("isRead")
    public boolean isRead() {
        return isRead;
    }

    public static NotificationResponse from(Notification notification) {
        // 1. 날짜 포맷팅 (프론트 요구사항: 2024.06.18 09:30)
        DateTimeFormatter timeFormatter = DateTimeFormatter.ofPattern("yyyy.MM.dd HH:mm");
        String formattedDate = notification.getCreatedAt().format(timeFormatter);

        // 2. 표시용 알림 ID 포맷팅 (예: NOT-20240618-001)
        String datePart = notification.getCreatedAt().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        String notiIdStr = String.format("NOT-%s-%03d", datePart, notification.getId());

        return NotificationResponse.builder()
                .id(notification.getId())
                .notificationId(notiIdStr)
                .type(notification.getType())
                .title(notification.getTitle())
                .body(notification.getBody())
                .channel(notification.getChannel())
                .createdAt(formattedDate)
                .isRead(notification.isRead())
                .build();
    }
}