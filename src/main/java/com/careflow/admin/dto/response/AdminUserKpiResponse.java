package com.careflow.admin.dto.response;

// GET /api/admin/users/kpi 응답 DTO — role 기준 회원 현황 요약(총원/상태별/오늘 신규가입)
public record AdminUserKpiResponse(
        long totalCount,
        long activeCount,
        long inactiveCount,
        long suspendedCount,
        long todayNewCount
) {
}
