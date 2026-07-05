package com.careflow.agency.dto.response;

import com.careflow.agency.entity.Agencies;

/**
 * 대행사 회원가입 절차 중 기존 등록된 대행사 조회(GET /api/agencies/agency) 응답
 * - Agencies 엔티티를 그대로 반환하면 representativeId(User) <-> User.agency 양방향 연관관계 때문에
 *   Jackson이 무한 재귀 직렬화를 시도하다 StreamWriteConstraints 예외로 실패하는 문제가 있어 DTO로 분리함
 */
public record AgencyLookupResponse(
        String agencyName,
        String businessNumber,
        String agencyAddress,
        Double agencyFeeRate
) {
    public static AgencyLookupResponse from(Agencies agencies) {
        return new AgencyLookupResponse(
                agencies.getAgencyName(),
                agencies.getBusinessNumber(),
                agencies.getAgencyAddress(),
                agencies.getAgencyFeeRate()
        );
    }
}
