package com.careflow.payment.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/**
 * 고객 결제 요청 DTO
 * amount — 결제 금액(원), 0 이상 필수
 */
public record PaymentRequest(
        @NotNull(message = "결제 금액은 필수입니다.")
        @Min(value = 0, message = "결제 금액은 0원 이상이어야 합니다.")
        Integer amount
) {}
