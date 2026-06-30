package com.careflow.lms.dto;

public record LmsEngineerStatusDto(
        Long userId,
        String name,
        Integer categoryId,
        String skillLevel,
        int totalCount,
        int completedCount,
        boolean isCompleted
) {}
