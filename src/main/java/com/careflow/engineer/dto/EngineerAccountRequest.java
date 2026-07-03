package com.careflow.engineer.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

public record EngineerAccountRequest(
        @NotBlank
        String name, // 가입자 이름
        @NotBlank
        @Email
        String email, // 가입자 이메일
        String password, // 비밀번호 추후 기본 입력 최소,최대길이 지정 필요
        @Pattern(regexp = "^010[- ]?\\d{3,4}[- ]?\\d{4}$")
        String phoneNumber,
        @NotNull
        Integer regionId,
        @NotBlank
        String addressDetail, // 가입자 거주지
        @NotBlank
        String businessNumber
) {
}
