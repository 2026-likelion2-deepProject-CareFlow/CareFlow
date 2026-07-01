package com.careflow.assignment.dto;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

public record AssignmentInProgressResponse(
        Long assignmentId,
        Long requestId,
        LocalDateTime createdAt,
        String assignMethod,

        Long customerId,
        String customerName,
        String customerPhone,

        String productName,
        String modelNo,

        Long engineerId,
        String engineerName,
        String engineerPhone,
        Double engineerRating,
        String engineerImg,

        String assignmentStatus,
        String latestLogStatus,
        LocalDateTime updatedAt,

        String visitDate,
        String visitTime,
        String visitAddress,

        List<StatusLogEntry> logs,
        Map<String, String> stepTimes
) {
    public record StatusLogEntry(
            String toStatus,
            String memo,
            LocalDateTime createdAt
    ) {}
}
