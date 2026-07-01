package com.careflow.admin.dto.request;

import jakarta.validation.constraints.NotBlank;

/**
 * PATCH /api/admin/users/{userId}/status 요청 바디
 * status 값 자체의 유효성(ACTIVE/INACTIVE/SUSPENDED)은 서비스 계층에서 UserStatus.valueOf로 검증
 */
public record AdminUserStatusUpdateRequest(
        @NotBlank(message = "변경할 상태값은 필수입니다.")
        String status
) {
}
