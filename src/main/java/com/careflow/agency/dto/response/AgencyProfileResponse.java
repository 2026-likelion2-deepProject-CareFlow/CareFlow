package com.careflow.agency.dto.response;

import com.careflow.agency.entity.Agencies;

public record AgencyProfileResponse(
        Long agencyId,
        String agencyName,
        String agencyAddress
) {
    public static AgencyProfileResponse from(Agencies agencies) {
        return new AgencyProfileResponse(
                agencies.getId(),
                agencies.getAgencyName(),
                agencies.getAgencyAddress()
        );
    }
}
