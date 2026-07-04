package com.careflow.agency.dto.request;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;

/**
 * 수수료율은 비율(0~1) 형태로 전달 — 예: 10% -> 0.1, 15.5% -> 0.155
 */
public record AgencyFeeRateUpdateRequest(

        @NotNull(message = "수수료율은 null일 수 없습니다.")
        @Digits(integer = 1, fraction = 4, message = "수수료율은 0~1 사이 비율로, 소수점 최대 4자리까지 입력 가능합니다.")
        Double agencyFeeRate
) {}
