package com.careflow.assignment.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AssignmentRejectRequest(
        @NotBlank(message = "거절 사유를 입력해주세요.")
        @Size(max = 255, message = "거절 사유는 255자 이내로 작성해주세요.")
        String rejectReason
) {}