package com.careflow.agency.dto.response;

/**
 * 대행사 관리자 로그인 보안 설정 조회 응답 DTO
 */
public record AgencySecuritySettingsResponse(
        boolean twoFactorEnabled,
        boolean loginAlertEnabled,
        long trustedDeviceCount
) {
}
