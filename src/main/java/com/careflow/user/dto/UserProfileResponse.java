package com.careflow.user.dto;

import com.careflow.user.entity.User;

/**
 * 사용자 본인 프로필 응답 DTO
 * GET /api/users/me 응답에 사용
 */
public record UserProfileResponse(
        Long userId,
        String email,
        String name,
        String phone,
        String role,
        Integer regionId,
        String regionName,
        String addressDetail
) {
    public static UserProfileResponse from(User user) {
        return new UserProfileResponse(
                user.getId(),
                user.getEmail(),
                user.getName(),
                user.getPhone(),
                user.getRole().name(),
                user.getRegionId() != null ? user.getRegionId().getId() : null,
                user.getRegionId() != null ? user.getRegionId().getName() : null,
                user.getAddressDetail()
        );
    }
}
