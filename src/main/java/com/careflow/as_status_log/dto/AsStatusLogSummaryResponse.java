package com.careflow.as_status_log.dto;

/**
 * 소속 대행사 A/S 상태별 집계 응답 DTO
 * - to_status 기준 5개 상태의 이력 건수 집계
 */
public record AsStatusLogSummaryResponse(
        long waitingCount,
        long engineerDepartedCount,
        long engineerArrivedCount,
        long inProgressCount,
        long completedCount
) {}
