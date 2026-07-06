package com.careflow.agency.dto.request;

/**
 * 대행사 관리자의 소속 고객 정보 수정 요청 DTO
 * 모든 필드 선택적 — null이면 해당 필드는 변경하지 않음(PATCH 의미론)
 */
public record AgencyCustomerUpdateRequest(
        String name,
        String phone,
        String addressDetail
) {
}
