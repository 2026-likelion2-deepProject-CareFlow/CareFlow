package com.careflow.assignment.dto;

import jakarta.validation.constraints.NotNull;

public record AssignmentChangeEngineerRequest(
        @NotNull(message = "기존 배정 ID는 필수입니다.")
        Long assignmentId,

        @NotNull(message = "새 기사 ID는 필수입니다.")
        Long newEngineerId
) {}
