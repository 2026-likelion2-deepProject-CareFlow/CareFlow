package com.careflow.engineer.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CreateProfileRequest {
    @NotNull(message = "전문 가전 카테고리를 선택해주세요.")
    private Integer categoryId;

    @NotNull(message = "경력 시작 연도를 입력해주세요.")
    @Min(value = 1950, message = "유효하지 않은 연도입니다.")
    private Integer careerStartedYear;

    private String introduction;

    @NotEmpty(message = "전문 브랜드를 1개 이상 선택해주세요.")
    private List<String> expertBrands;

    @NotEmpty(message = "서비스 가능 지역을 1개 이상 선택해주세요.")
    private List<Integer> serviceRegionIds;
}
