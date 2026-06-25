package com.careflow.as_request.dto;

/**
 * 대행사 대시보드 요약 통계 응답 DTO
 * - totalCount        : 대행사 소속 A/S 요청 전체 누적 건수
 * - todayNewCount     : 오늘 신규 접수된 A/S 요청 건수
 * - todayAssignedCount: 오늘 접수 중 기사 배정 대기(ASSIGNED) 건수
 * - todayAcceptedCount: 오늘 접수 중 기사 배정 승인(ACCEPTED) 건수
 * - todayCancelledCount: 오늘 접수 중 고객 취소(CANCELLED) 건수
 */
public record AgencyDashboardSummaryResponse(
        long totalCount,
        long todayNewCount,
        long todayAssignedCount,
        long todayAcceptedCount,
        long todayCancelledCount
) {
}
