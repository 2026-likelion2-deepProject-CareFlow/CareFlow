package com.careflow.agency.dto.request;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;

public record AgencyFeeRateUpdateRequest(

        @NotNull(message = "수수료율은 null일 수 없습니다.")
        @Digits(integer = 3, fraction = 2, message = "수수료율은 소수점 최대 2자리까지 입력 가능합니다.")
        Double agencyFeeRate
) {}
