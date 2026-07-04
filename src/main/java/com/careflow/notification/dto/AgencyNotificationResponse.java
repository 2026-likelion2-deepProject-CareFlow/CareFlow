package com.careflow.notification.dto;

import java.time.LocalDateTime;
import java.util.List;

// 대행사 알림센터 목록 조회(GET /api/agency/notifications) 응답 DTO
public record AgencyNotificationResponse(
        Stats stats,
        List<NotificationItem> content,
        long totalElements,
        int totalPages,
        int currentPage,
        int size
) {
    public record Stats(
            long totalCount,
            long unreadCount,
            long todayCount
    ) {
    }

    public record NotificationItem(
            Long notificationId,
            String type,
            String title,
            String body,
            String channel,
            boolean isRead,
            LocalDateTime createdAt
    ) {
    }
}
