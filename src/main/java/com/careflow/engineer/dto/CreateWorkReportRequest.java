package com.careflow.engineer.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CreateWorkReportRequest {

    @NotNull(message = "A/S 신청 ID는 필수입니다.")
    private Long requestId;

    @NotBlank(message = "진단 결과는 필수입니다.")
    private String diagnosisResult;

    @NotNull(message = "작업 시간은 필수입니다.")
    private Integer workDurationMin;

    @NotNull(message = "최종 청구 금액은 필수입니다.")
    private Integer finalAmount;

    private String memo;

    private List<PartDto> parts;

    @Getter
    @NoArgsConstructor(access = AccessLevel.PROTECTED)
    public static class PartDto {
        @NotNull(message = "부품 ID는 필수입니다.")
        private Long repairPartId;

        @NotNull(message = "수량은 필수입니다.")
        private Integer quantity;

        private Integer appliedUnitPrice;
    }
}