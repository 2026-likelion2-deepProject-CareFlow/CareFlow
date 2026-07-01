package com.careflow.agency.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record SettlementStatusUpdateRequest(

        @NotBlank(message = "변경할 상태값은 필수입니다.")
        @Pattern(
                regexp = "APPROVED|DISPUTED|PENDING|PAID",
                message = "status 는 APPROVED, DISPUTED, PENDING, PAID 중 하나여야 합니다."
        )
        String status
) {}
