package com.careflow.agency.dto.request;

// GET /api/agency/customers 요청 바디 — 고객 관리 목록 검색/필터 조건
// grade, joinPath는 users 테이블에 대응 컬럼이 없어(DB 미지원) 현재 서비스 로직에서는 사용하지 않는다.
// 추후 DB 마이그레이션으로 컬럼이 추가되면 AgencyCustomerService 의 검색 쿼리에 조건을 추가해야 한다.
public record AgencyCustomerSearchRequest(
        String keyword,
        String status,
        String grade,
        String joinPath,
        String joinedFrom,
        String joinedTo
) {
}
