package com.careflow.as_request.dto;

import com.careflow.common.enums.AssignType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Getter
@NoArgsConstructor
public class AsRequestCreateDto {

    @NotNull(message = "가전제품 선택은 필수입니다.")
    private Long applianceId;

    // v5 스키마: symptom_code VARCHAR → symptoms 테이블 FK(symptom_id)로 변경
    @NotNull(message = "증상 선택은 필수입니다.")
    private Long symptomId;

    private String symptomDesc;

    private String imageUrls; // JSON 문자열 (사진 URL 목록)

    // AUTO: 시스템 자동 배정, MANUAL: 고객이 수리 기사 직접 지정
    @NotNull(message = "배정 방식 선택은 필수입니다.")
    private AssignType assignType;

    @NotNull(message = "방문 지역 선택은 필수입니다.")
    private Integer visitRegionId;

    @NotBlank(message = "방문 상세 주소를 입력해 주세요.")
    private String visitAddressDetail;

    @NotNull(message = "방문 예약 날짜는 필수입니다.")
    private LocalDate scheduledDate;

    @NotBlank(message = "방문 예약 시간은 필수입니다.")
    private String scheduledTime; // "HH:MM"

    private Long preferredEngineerId; // 선택: 고객이 선호 기사 지정 시
}