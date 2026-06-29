package com.careflow.assignment.dto;

public record AssignmentCancelResponse(
        Long assignmentId,
        Long requestId,
        String cancelledStatus,
        String message
) {}
