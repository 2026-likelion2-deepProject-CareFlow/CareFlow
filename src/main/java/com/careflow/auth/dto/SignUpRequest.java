package com.careflow.auth.dto;

import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@AllArgsConstructor // 테스트를 위한 설정
public class SignUpRequest {
    @NotBlank @Email
    private String email;

    @NotBlank
    @Size(min = 8, max = 64, message = "비밀번호는 8자 이상 64자 이하로 입력해주세요.")
    @Pattern(regexp = "^(?=.*[A-Za-z])(?=.*\\d).+$", message = "비밀번호는 영문과 숫자를 모두 포함해야 합니다.")
    private String password;

    @NotBlank
    private String name;

    private String phone;

    @NotNull
    private Integer regionId;

    @NotBlank
    private String addressDetail;
}