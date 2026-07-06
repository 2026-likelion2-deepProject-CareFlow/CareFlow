package com.careflow.agency.dto.response;

import com.careflow.auth.entity.TrustedDevice;

import java.time.LocalDateTime;

/**
 * 신뢰 기기 목록 조회 응답 DTO
 */
public record TrustedDeviceResponse(
        Long deviceId,
        String deviceName,
        LocalDateTime lastUsedAt,
        LocalDateTime createdAt
) {
    public static TrustedDeviceResponse from(TrustedDevice device) {
        return new TrustedDeviceResponse(
                device.getId(), device.getDeviceName(), device.getLastUsedAt(), device.getCreatedAt());
    }
}
