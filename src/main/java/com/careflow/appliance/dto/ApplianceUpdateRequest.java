package com.careflow.appliance.dto;

import jakarta.validation.constraints.Size;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

// 가전 정보 수정 요청 — null 필드는 기존 값 유지(PATCH 의미론), 프론트 "가전 정보 수정" 모달에서 사용
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ApplianceUpdateRequest {

    @Size(max = 50, message = "브랜드명은 50자 이내로 입력해주세요.")
    private String brand;

    @Size(max = 100, message = "모델명은 100자 이내로 입력해주세요.")
    private String modelName;

    @Size(max = 100, message = "시리얼 번호는 100자 이내로 입력해주세요.")
    private String serialNumber;

    private LocalDate purchaseDate;

    private LocalDate warrantyEndDate;
}
