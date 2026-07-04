package com.careflow.agency.dto.response;

import com.careflow.agency.entity.Agencies;
import com.careflow.agency_bank_account.entity.AgencyBankAccount;

public record AgencyProfileResponse(
        Long agencyId,
        String agencyName,
        String agencyAddress,
        String bankName,
        String accountNumber
) {
    // bankAccount 미등록 시 null — 계좌 등록 전까지는 bankName/accountNumber가 null로 내려감
    public static AgencyProfileResponse from(Agencies agencies, AgencyBankAccount bankAccount) {
        return new AgencyProfileResponse(
                agencies.getId(),
                agencies.getAgencyName(),
                agencies.getAgencyAddress(),
                bankAccount != null ? bankAccount.getBankName() : null,
                bankAccount != null ? bankAccount.getAccountNumber() : null
        );
    }
}
