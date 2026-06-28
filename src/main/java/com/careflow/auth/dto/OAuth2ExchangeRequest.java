package com.careflow.auth.dto;

import jakarta.validation.constraints.NotBlank;

public record OAuth2ExchangeRequest(
        @NotBlank(message = "코드는 필수입니다.")
        String code
) {}
