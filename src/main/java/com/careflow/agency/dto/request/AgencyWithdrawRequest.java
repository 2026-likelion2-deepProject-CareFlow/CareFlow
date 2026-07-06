package com.careflow.agency.dto.request;

import jakarta.validation.constraints.NotBlank;

/**
 * 대행사 관리자 계정 탈퇴 요청 DTO — 본인 확인용 현재 비밀번호
 */
public record AgencyWithdrawRequest(
        @NotBlank(message = "현재 비밀번호를 입력해주세요.")
        String password
) {
}
