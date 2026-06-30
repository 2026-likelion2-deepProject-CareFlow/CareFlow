package com.careflow.agency.dto.response;

import com.careflow.user.entity.User;
import org.springframework.data.domain.Page;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

// GET /api/agency/customers 응답 DTO
public record AgencyCustomerListResponse(
        Stats stats,
        List<CustomerSummary> content,
        long totalElements,
        int totalPages,
        int currentPage,
        int size
) {

    // stats는 COMPLETED 서비스 모수(customerIds) 기준 전체 집계 — content의 검색 필터와 무관
    public record Stats(
            long totalCount,
            long activeCount,
            long inactiveCount,
            long newThisMonth,
            long prevMonthDiff,
            long newThisMonthDiff
    ) {
    }

    public record CustomerSummary(
            Long userId,
            String name,
            String phone,
            String email,
            String address,
            LocalDateTime joinedAt,
            String joinPath,
            String status,
            int applianceCount,
            LocalDateTime lastLoginAt
    ) {
        // regions.name + users.address_detail 조합 — 둘 중 하나만 있어도 처리, 둘 다 없으면 빈 문자열
        // joinPath: users 테이블에 대응 컬럼이 없어(DB 미지원) 현재는 항상 null — 추후 컬럼 추가 시 매핑 예정
        public static CustomerSummary from(User user, int applianceCount) {
            String regionName = user.getRegionId() != null ? user.getRegionId().getName() : null;
            String addressDetail = user.getAddressDetail();
            String address = java.util.stream.Stream.of(regionName, addressDetail)
                    .filter(s -> s != null && !s.isBlank())
                    .reduce((a, b) -> a + " " + b)
                    .orElse("");

            return new CustomerSummary(
                    user.getId(),
                    user.getName(),
                    user.getPhone(),
                    user.getEmail(),
                    address,
                    user.getCreatedAt(),
                    null,
                    user.getStatus(),
                    applianceCount,
                    user.getLastLoginAt());
        }
    }

    public static AgencyCustomerListResponse of(Stats stats, Page<User> page, Map<Long, Long> applianceCountMap) {
        List<CustomerSummary> content = page.getContent().stream()
                .map(u -> CustomerSummary.from(u, applianceCountMap.getOrDefault(u.getId(), 0L).intValue()))
                .toList();

        return new AgencyCustomerListResponse(
                stats,
                content,
                page.getTotalElements(),
                page.getTotalPages(),
                page.getNumber(),
                page.getSize());
    }

    public static AgencyCustomerListResponse empty(int page, int size) {
        return new AgencyCustomerListResponse(
                new Stats(0, 0, 0, 0, 0, 0),
                List.of(),
                0, 0, page, size);
    }
}
