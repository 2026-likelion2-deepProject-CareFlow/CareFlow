package com.careflow.agency.dto.request;

import jakarta.validation.constraints.NotNull;

/**
 * 2단계 인증 / 로그인 알림 토글 요청 DTO
 */
public record SecurityToggleRequest(
        @NotNull(message = "enabled 값은 필수입니다.")
        Boolean enabled
) {
}
