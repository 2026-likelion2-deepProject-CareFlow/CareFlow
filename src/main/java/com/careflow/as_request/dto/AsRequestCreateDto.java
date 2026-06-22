package com.careflow.as_request.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class AsRequestCreateDto {

    @NotNull(message = "가전제품 선택은 필수입니다.")
    private Long applianceId;

    @NotBlank(message = "제목을 입력해 주세요.")
    private String title;

    @NotBlank(message = "상세 고장 증상을 설명해 주세요.")
    private String description;
}