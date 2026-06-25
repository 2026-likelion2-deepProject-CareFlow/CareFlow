package com.careflow.lms.dto;

public record LmsEngineerStatusDto(Long userId,
                                   String name,
                                   int totalCount,
                                   int completedCount,
                                   boolean isCompleted) {
}
