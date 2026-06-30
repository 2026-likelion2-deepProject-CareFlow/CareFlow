package com.careflow.notification.dto;

import java.time.LocalDateTime;

// 대행사 알림 단건 상세 조회(GET /api/agency/notices) 응답 DTO
public record AgencyNoticeDetailResponse(
        Long notificationId,
        String type,
        String title,
        String body,
        String channel,
        LocalDateTime createdAt
) {}
