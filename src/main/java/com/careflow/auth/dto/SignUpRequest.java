package com.careflow.auth.dto;

import jakarta.validation.constraints.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class SignUpRequest {
    @NotBlank @Email
    private String email;

    @NotBlank @Size(min = 8)
    private String password;

    @NotBlank
    private String name;

    private String phone;

    @NotNull
    private Integer regionId;

    @NotBlank
    private String addressDetail;
}