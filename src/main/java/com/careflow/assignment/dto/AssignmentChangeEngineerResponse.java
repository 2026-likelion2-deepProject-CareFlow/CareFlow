package com.careflow.assignment.dto;

import java.time.LocalDateTime;

public record AssignmentChangeEngineerResponse(
        Long newAssignmentId,
        Long requestId,
        Long newEngineerId,
        String newEngineerName,
        String assignmentStatus,
        LocalDateTime assignedAt
) {}
