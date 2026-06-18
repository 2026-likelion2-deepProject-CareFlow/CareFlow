package com.careflow.engineer.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CreateProfileRequest {
    @NotNull(message = "전문 가전 카테고리를 선택해주세요.")
    private Long categoryId;

    @NotNull(message = "경력 시작 연도를 입력해주세요.")
    @Min(value = 1950, message = "유효하지 않은 연도입니다.")
    @Max(value = 2026, message = "미래의 연도는 입력할 수 없습니다.")
    private Integer careerStartedYear;

    private String introduction;
}
