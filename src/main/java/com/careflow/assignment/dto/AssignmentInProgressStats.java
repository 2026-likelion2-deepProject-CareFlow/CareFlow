package com.careflow.assignment.dto;

public record AssignmentInProgressStats(
        int totalCount,
        int movingCount,
        int inProgressCount,
        int completedCount
) {}
