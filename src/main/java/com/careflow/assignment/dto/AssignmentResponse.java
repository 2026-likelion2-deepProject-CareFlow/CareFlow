package com.careflow.assignment.dto;

import com.careflow.assignment.entity.AsAssignment;
import com.careflow.common.enums.AssignType;

import java.time.LocalDateTime;

public record AssignmentResponse(
        Long assignmentId,
        Long requestId,
        Long engineerId,
        String engineerName,
        Long agencyId,
        AssignType assignMethod,
        String status,
        LocalDateTime assignedAt,
        LocalDateTime acceptedAt,
        LocalDateTime rejectedAt,
        String rejectReason
) {
    public static AssignmentResponse from(AsAssignment a) {
        return new AssignmentResponse(
                a.getId(),
                a.getAsRequest().getId(),
                a.getEngineer().getId(),
                a.getEngineer().getName(),
                a.getAgency().getId(),
                a.getAssignMethod(),
                a.getStatus(),
                a.getAssignedAt(),
                a.getAcceptedAt(),
                a.getRejectedAt(),
                a.getRejectReason()
        );
    }
}
