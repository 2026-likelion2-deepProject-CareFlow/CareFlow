package com.careflow.appliance.dto;

import com.careflow.common.enums.RegisterMethod;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Getter
@NoArgsConstructor
public class ApplianceCreateRequest {

    @NotNull(message = "가전제품 카테고리를 선택해주세요.")
    private Integer categoryId;

    @NotBlank(message = "브랜드명을 입력해주세요.")
    @Size(max = 50, message = "브랜드명은 50자 이내로 입력해주세요.")
    private String brand;

    @Size(max = 100, message = "모델명은 100자 이내로 입력해주세요.")
    private String modelName;

    @Size(max = 100, message = "시리얼 번호는 100자 이내로 입력해주세요.")
    private String serialNumber;

    private LocalDate purchaseDate;

    private LocalDate warrantyEndDate;

    // 등록 방식(MANUAL/OCR) — 미입력 시 서비스에서 MANUAL 기본 처리
    private RegisterMethod registerMethod;
}
