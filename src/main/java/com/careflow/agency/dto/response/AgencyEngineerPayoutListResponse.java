package com.careflow.agency.dto.response;

import org.springframework.data.domain.Page;

import java.time.LocalDateTime;
import java.util.List;

// 대행사 기사 지급 배치 목록 조회(GET /api/agency/engineer-payouts) 응답 DTO
public record AgencyEngineerPayoutListResponse(
        List<Item> content,
        long totalElements,
        int totalPages,
        int currentPage,
        int size
) {
    public static AgencyEngineerPayoutListResponse of(Page<Item> page) {
        return new AgencyEngineerPayoutListResponse(
                page.getContent(),
                page.getTotalElements(),
                page.getTotalPages(),
                page.getNumber(),
                page.getSize());
    }

    public record Item(
            Long engineerPayoutId,
            Long engineerId,
            String engineerName,
            String engineerPhone,
            int netAmountSum,
            int caseCount,
            String status,
            LocalDateTime paidAt,
            String payMethod,
            String bankAccount
    ) {
    }
}
