package com.careflow.agency.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AgencyProfileUpdateRequest(

        @NotBlank(message = "대행사 상호명은 공백일 수 없습니다.")
        @Size(max = 100, message = "대행사 상호명은 최대 100자까지 입력 가능합니다.")
        String agencyName,

        @NotBlank(message = "주소는 공백일 수 없습니다.")
        @Size(max = 255, message = "주소는 최대 255자까지 입력 가능합니다.")
        String agencyAddress
) {}
