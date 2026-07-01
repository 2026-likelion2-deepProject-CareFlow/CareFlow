package com.careflow.admin.dto.response;

// GET /api/admin/users/member-trend 응답 DTO — 날짜별 신규 가입자 수(라인차트)
public record AdminMemberTrendResponse(
        String date,
        long newCount
) {
}
