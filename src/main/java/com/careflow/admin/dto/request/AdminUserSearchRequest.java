package com.careflow.admin.dto.request;

/**
 * GET /api/admin/users 요청 파라미터 — 회원 목록 검색/필터 조건
 * role 미지정 시 CUSTOMER 기준으로 조회(관리자 화면 기본값은 고객 목록)
 */
public record AdminUserSearchRequest(
        String role,
        String status,
        String keyword
) {
}
