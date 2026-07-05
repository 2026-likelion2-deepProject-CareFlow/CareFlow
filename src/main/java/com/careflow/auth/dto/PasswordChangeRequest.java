package com.careflow.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

// 로그인된 사용자가 현재 비밀번호를 확인하고 새 비밀번호로 변경할 때 사용하는 요청 DTO
// (모든 역할 공통 — CUSTOMER/AGENCY/ENGINEER/ADMIN)
public record PasswordChangeRequest(
        @NotBlank String currentPassword,

        @NotBlank
        @Size(min = 8, max = 64, message = "비밀번호는 8자 이상 64자 이하로 입력해주세요.")
        @Pattern(regexp = "^(?=.*[A-Za-z])(?=.*\\d).+$", message = "비밀번호는 영문과 숫자를 모두 포함해야 합니다.")
        String newPassword
) {}
