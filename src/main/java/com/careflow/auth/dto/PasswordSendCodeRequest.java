package com.careflow.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record PasswordSendCodeRequest(
        @NotBlank @Email String email
) {}
