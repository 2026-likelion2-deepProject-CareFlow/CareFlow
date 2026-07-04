package com.careflow.agency.dto.response;

import com.careflow.agency.entity.Agencies;

public record AgencyFeeRateResponse(
        Long agencyId,
        String agencyName,
        Double agencyFeeRate
) {
    public static AgencyFeeRateResponse from(Agencies agencies) {
        // agencies.agency_fee_rate는 이미 비율(0~1, 예: 0.46 = 46%)로 저장되어 있음(v14 스키마) — 변환 없이 그대로 반환
        return new AgencyFeeRateResponse(
                agencies.getId(),
                agencies.getAgencyName(),
                agencies.getAgencyFeeRate()
        );
    }
}
