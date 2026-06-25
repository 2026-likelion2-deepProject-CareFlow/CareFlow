package com.careflow.lms.dto;

public record LmsAnnualStatusDto(int totalCount,     // 이수 대상 전체 콘텐츠 수
                                 int completedCount, // 당해 연도 이수 완료 수
                                 boolean isCompleted) {
}
