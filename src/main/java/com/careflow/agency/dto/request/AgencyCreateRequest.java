package com.careflow.agency.dto.request;

import jakarta.validation.constraints.*;
import lombok.Builder;

public record AgencyCreateRequest(

        @NotBlank
        String name, // 가입자 이름
        @NotBlank
        @Email
        String email, // 가입자 이메일
        String password, // 비밀번호 추후 기본 입력 최소,최대길이 지정 필요
        @Pattern(regexp = "^010[- ]?\\d{3,4}[- ]?\\d{4}$")
        String phoneNumber,
        @NotBlank
        String agencyName,
        @NotBlank
        String businessNumber,
        @Size(max = 255)
        String agencyAddress,
        @NotNull
        @Digits(integer = 5, fraction = 2)
        Double agencyFeeRate,
        @NotNull
        boolean flag
) {
}
