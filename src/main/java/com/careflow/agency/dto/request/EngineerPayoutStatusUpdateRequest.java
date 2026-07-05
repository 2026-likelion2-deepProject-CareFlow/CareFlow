package com.careflow.agency.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/**
 * PATCH /api/agency/engineer-payouts/{engineerPayoutId}/status 요청 바디
 * 대행사→기사 지급 건별 보류/재검토 처리 — PAID는 별도의 지급 완료 API(/pay) 전용이라 여기서 다루지 않음
 */
public record EngineerPayoutStatusUpdateRequest(
        @NotBlank(message = "변경할 상태값은 필수입니다.")
        @Pattern(
                regexp = "DISPUTED|PENDING",
                message = "status 는 DISPUTED, PENDING 중 하나여야 합니다."
        )
        String status
) {
}
