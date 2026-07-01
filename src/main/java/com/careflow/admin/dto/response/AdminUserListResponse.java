package com.careflow.admin.dto.response;

import com.careflow.user.entity.User;
import org.springframework.data.domain.Page;

import java.time.LocalDateTime;
import java.util.List;

// GET /api/admin/users 응답 DTO
public record AdminUserListResponse(
        List<UserSummary> content,
        long totalElements,
        int totalPages,
        int currentPage,
        int size
) {

    public record UserSummary(
            Long userId,
            String name,
            String phone,
            String email,
            String role,
            String status,
            String address,
            LocalDateTime joinedAt,
            LocalDateTime lastLoginAt
    ) {
        // regions.name + users.address_detail 조합 — 둘 중 하나만 있어도 처리, 둘 다 없으면 빈 문자열
        public static UserSummary from(User user) {
            String regionName = user.getRegionId() != null ? user.getRegionId().getName() : null;
            String addressDetail = user.getAddressDetail();
            String address = java.util.stream.Stream.of(regionName, addressDetail)
                    .filter(s -> s != null && !s.isBlank())
                    .reduce((a, b) -> a + " " + b)
                    .orElse("");

            return new UserSummary(
                    user.getId(),
                    user.getName(),
                    user.getPhone(),
                    user.getEmail(),
                    user.getRole().name(),
                    user.getStatus(),
                    address,
                    user.getCreatedAt(),
                    user.getLastLoginAt());
        }
    }

    public static AdminUserListResponse of(Page<User> page) {
        List<UserSummary> content = page.getContent().stream()
                .map(UserSummary::from)
                .toList();

        return new AdminUserListResponse(
                content,
                page.getTotalElements(),
                page.getTotalPages(),
                page.getNumber(),
                page.getSize());
    }
}
