package com.careflow.admin.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/**
 * PATCH /api/admin/settlements/{settlementId}/status 요청 바디
 * CareFlow→대행사 정산(Settlement) 건별 보류/재검토 처리 — PAID는 platform_settlements 배치 승인 전용이라 여기서 다루지 않음
 */
public record AdminSettlementStatusUpdateRequest(
        @NotBlank(message = "변경할 상태값은 필수입니다.")
        @Pattern(
                regexp = "DISPUTED|PENDING",
                message = "status 는 DISPUTED, PENDING 중 하나여야 합니다."
        )
        String status
) {
}
