package com.careflow.as_request.dto;

import com.careflow.as_request.entity.AsRequest;
import com.careflow.common.enums.AsStatus;
import com.careflow.report.domain.entity.WorkReport;
import lombok.Getter;

import java.math.BigDecimal;
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
    private final String brand;
    private final String modelName;
    // 가전 카테고리명(예: 냉장고, 세탁기) — 목록 아이콘 표시용
    private final String categoryName;
    private final Long reportId;
    // 배정된 기사 정보 — 배정 전 상태이거나 배정이 거절된 경우 null
    private final String engineerName;
    private final BigDecimal engineerRating;

    public AsRequestResponseDto(AsRequest entity) {
        this(entity, null, null);
    }

    public AsRequestResponseDto(AsRequest entity, String engineerName, BigDecimal engineerRating) {
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
        this.brand = entity.getAppliance().getBrand();
        this.modelName = entity.getAppliance().getModelName();
        this.categoryName = entity.getAppliance().getCategory().getName();
        WorkReport report = entity.getWorkReport();
        this.reportId = report != null ? report.getReportId() : null;
        this.engineerName = engineerName;
        this.engineerRating = engineerRating;
    }
}
