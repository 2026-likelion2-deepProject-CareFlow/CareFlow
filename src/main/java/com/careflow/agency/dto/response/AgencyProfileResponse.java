package com.careflow.agency.dto.response;

import com.careflow.agency.entity.Agencies;
import com.careflow.agency_bank_account.entity.AgencyBankAccount;
import com.careflow.user.entity.User;

import java.time.LocalDateTime;

public record AgencyProfileResponse(
        Long agencyId,
        String agencyName,
        String agencyAddress,
        String bankName,
        String accountNumber,
        String name,
        String email,
        LocalDateTime lastLoginAt
) {
    // bankAccount 미등록 시 null — 계좌 등록 전까지는 bankName/accountNumber가 null로 내려감
    // name/email/lastLoginAt은 로그인한 계정(user) 기준 — 대행사 정보와 별개로 설정 페이지의 "계정 정보" 카드에 사용됨
    public static AgencyProfileResponse from(Agencies agencies, AgencyBankAccount bankAccount, User user) {
        return new AgencyProfileResponse(
                agencies.getId(),
                agencies.getAgencyName(),
                agencies.getAgencyAddress(),
                bankAccount != null ? bankAccount.getBankName() : null,
                bankAccount != null ? bankAccount.getAccountNumber() : null,
                user.getName(),
                user.getEmail(),
                user.getLastLoginAt()
        );
    }
}
