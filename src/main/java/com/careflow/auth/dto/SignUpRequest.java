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

    @NotBlank @Size(min = 8)
    private String password;

    @NotBlank
    private String name;

    private String phone;

    @NotBlank
    private String regionName;

    @NotBlank
    private String addressDetail;
}