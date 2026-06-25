package com.careflow.agency.dto.response;

import com.careflow.agency.entity.Agencies;

public record AgencyFeeRateResponse(
        Long agencyId,
        String agencyName,
        Double agencyFeeRate
) {
    public static AgencyFeeRateResponse from(Agencies agencies) {
        return new AgencyFeeRateResponse(
                agencies.getId(),
                agencies.getAgencyName(),
                agencies.getAgencyFeeRate()
        );
    }
}
