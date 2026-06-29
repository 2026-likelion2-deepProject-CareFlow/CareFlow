package com.careflow.assignment.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

import java.time.LocalDate;

public record AssignmentScheduleRequest(
        @NotNull(message = "방문 날짜는 필수입니다.")
        LocalDate scheduledDate,

        @NotBlank(message = "방문 시간은 필수입니다.")
        @Pattern(regexp = "^([01]\\d|2[0-3]):[0-5]\\d$", message = "시간 형식은 HH:mm이어야 합니다.")
        String scheduledTime
) {}
