package com.careflow.notification.dto;

import jakarta.validation.constraints.NotNull;

public record AgencyNoticeDetailRequest(
        @NotNull(message = "notificationId는 필수입니다.")
        Long notificationId
) {}
