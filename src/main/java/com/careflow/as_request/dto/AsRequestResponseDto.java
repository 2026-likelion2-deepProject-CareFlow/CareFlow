package com.careflow.as_request.dto;

import com.careflow.as_request.entity.AsRequest;
import com.careflow.common.enums.AsStatus;
import lombok.Getter;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Getter
public class AsRequestResponseDto {
    private final Long requestId;
    private final Long applianceId;
    // v5 스키마: symptom_code VARCHAR → symptoms FK 로 변경, 응답에는 id + code 모두 반환
    private final Long symptomId;
    private final String symptomCode;
    private final String symptomDesc;
    private final Integer visitRegionId;
    private final String visitAddressDetail;
    private final LocalDate scheduledDate;
    private final String scheduledTime;
    private final AsStatus status;
    private final String cancelReason;
    private final LocalDateTime createdAt;

    public AsRequestResponseDto(AsRequest entity) {
        this.requestId = entity.getId();
        // 연관관계 객체에서 ID 값 추출
        this.applianceId = entity.getAppliance().getId();
        this.symptomId = entity.getSymptom().getId();
        this.symptomCode = entity.getSymptom().getSymptomCode();
        this.symptomDesc = entity.getSymptomDesc();
        this.visitRegionId = entity.getVisitRegion().getId();
        this.visitAddressDetail = entity.getVisitAddressDetail();
        this.scheduledDate = entity.getScheduledDate();
        this.scheduledTime = entity.getScheduledTime();
        this.status = entity.getStatus();
        this.cancelReason = entity.getCancelReason();
        this.createdAt = entity.getCreatedAt();
    }
}
