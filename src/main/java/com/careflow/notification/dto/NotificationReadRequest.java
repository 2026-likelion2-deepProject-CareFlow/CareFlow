package com.careflow.notification.dto;

import java.util.List;

/**
 * [기사용] 선택 알림 일괄 읽음 처리 요청 바디.
 *
 * 프론트: PATCH /api/engineer/notifications/read
 *         { "notificationIds": [1, 2, 3] }
 */
public record NotificationReadRequest(List<Long> notificationIds) {
}
