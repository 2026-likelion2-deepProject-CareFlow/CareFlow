package com.careflow.engineer.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
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
    @PositiveOrZero(message = "작업 시간은 0 이상이어야 합니다.")
    private Integer workDurationMin;

    // 최종 청구 금액은 서버에서 (부품 합계 + 출장비)로 계산·확정한다. 클라이언트 값은 무시되므로 선택값.
    private Integer finalAmount;

    private String memo;

    private String imageUrls;

    private List<PartDto> parts;

    @Getter
    @NoArgsConstructor(access = AccessLevel.PROTECTED)
    public static class PartDto {
        @NotNull(message = "부품 ID는 필수입니다.")
        private Long repairPartId;

        @NotNull(message = "수량은 필수입니다.")
        @Positive(message = "수량은 1 이상이어야 합니다.")
        private Integer quantity;

        private Integer appliedUnitPrice;
    }
}