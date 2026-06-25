package com.careflow.account_requests.dto;

import com.careflow.account_requests.entity.AccountRequests;
import com.careflow.common.enums.AccountRequestsRole;
import com.careflow.common.enums.AccountRequestsStatus;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 계정 생성 요청 목록 조회 응답 DTO.
 * - password, reviewedBy, createdUserId, region, rejectReason, updatedAt, reviewedAt 은 목록 조회 시 불필요하므로 제외
 * - agency 는 JOIN FETCH 된 필드에서 id·name 만 추출
 */
public record AccountRequestListResponse(
        int totalCount,
        List<AccountRequestItem> requests
) {
    public static AccountRequestListResponse of(List<AccountRequests> entities) {
        List<AccountRequestItem> items = entities.stream()
                .map(AccountRequestItem::from)
                .toList();
        return new AccountRequestListResponse(items.size(), items);
    }

    public record AccountRequestItem(
            Long accountRequestId,
            Long agencyId,
            String agencyName,
            String email,
            String name,
            String phone,
            String addressDetail,
            AccountRequestsRole requestsRole,
            AccountRequestsStatus status,
            LocalDateTime createdAt
    ) {
        public static AccountRequestItem from(AccountRequests req) {
            return new AccountRequestItem(
                    req.getId(),
                    req.getAgency() != null ? req.getAgency().getId() : null,
                    req.getAgency() != null ? req.getAgency().getAgencyName() : null,
                    req.getEmail(),
                    req.getName(),
                    req.getPhone(),
                    req.getAddressDetail(),
                    req.getRequestsRole(),
                    req.getStatus(),
                    req.getCreatedAt()
            );
        }
    }
}
