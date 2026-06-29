package com.careflow.assignment.dto;

import java.time.LocalDate;

public record AssignmentScheduleResponse(
        Long assignmentId,
        Long requestId,
        LocalDate scheduledDate,
        String scheduledTime
) {}
