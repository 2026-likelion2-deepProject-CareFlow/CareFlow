package com.careflow.assignment.dto;

import java.util.List;

public record AssignmentInProgressPageResponse(
        AssignmentInProgressStats stats,
        List<AssignmentInProgressResponse> content,
        long totalElements,
        int totalPages,
        int currentPage,
        int size
) {}
