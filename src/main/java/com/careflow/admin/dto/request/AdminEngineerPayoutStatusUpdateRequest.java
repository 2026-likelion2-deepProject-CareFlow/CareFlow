package com.careflow.admin.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/**
 * PATCH /api/admin/engineer-payouts/{engineerPayoutId}/status 요청 바디
 * 대행사→기사 지급 건에 대해 기사가 이의를 제기했을 때 CareFlow가 조정자로서 보류/재검토 처리한다.
 * PAID는 대행사의 자체 지급 신고 전용({@link com.careflow.agency.service.AgencyEngineerPayoutService#payEngineerPayout})이라 여기서 다루지 않음
 */
public record AdminEngineerPayoutStatusUpdateRequest(
        @NotBlank(message = "변경할 상태값은 필수입니다.")
        @Pattern(
                regexp = "DISPUTED|PENDING",
                message = "status 는 DISPUTED, PENDING 중 하나여야 합니다."
        )
        String status
) {
}
