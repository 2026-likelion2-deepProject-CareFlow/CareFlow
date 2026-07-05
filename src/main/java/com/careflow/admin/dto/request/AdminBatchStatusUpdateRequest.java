package com.careflow.admin.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/**
 * PATCH /api/admin/settlements/{agencyId}/batch-status?year=&month= 요청 바디
 * platform_settlements 배치 단위 보류/재검토 처리 — 집계 오류 등으로 CareFlow가 자체적으로 배치를 보류시킬 때 사용.
 * PAID는 approveAgency/approveAll 전용이라 여기서 다루지 않으며, 이미 PAID인 배치는 어떤 변경도 불가(불변 기록).
 */
public record AdminBatchStatusUpdateRequest(
        @NotBlank(message = "변경할 상태값은 필수입니다.")
        @Pattern(
                regexp = "DISPUTED|PENDING",
                message = "status 는 DISPUTED, PENDING 중 하나여야 합니다."
        )
        String status
) {
}
