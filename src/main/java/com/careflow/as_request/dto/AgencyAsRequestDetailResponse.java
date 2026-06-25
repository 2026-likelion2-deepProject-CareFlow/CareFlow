package com.careflow.as_request.dto;

import com.careflow.as_request.entity.AsRequest;
import com.careflow.common.enums.AsStatus;
import com.careflow.common.enums.AssignType;

import java.time.LocalDate;
import java.time.LocalDateTime;

public record AgencyAsRequestDetailResponse(
        Long requestId,
        AsStatus status,
        // 고객 정보
        String customerName,
        String customerPhone,
        // 증상 정보
        String symptomCode,
        String symptomName,
        String symptomDesc,
        String imageUrls,
        // 가전 정보
        String brand,
        String modelName,
        String serialNumber,
        LocalDate purchaseDate,
        LocalDate warrantyEndDate,
        // 방문 정보
        AssignType assignType,
        String visitRegionName,
        String visitAddressDetail,
        LocalDate scheduledDate,
        String scheduledTime,
        // 기타
        String cancelReason,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
    public static AgencyAsRequestDetailResponse from(AsRequest r) {
        return new AgencyAsRequestDetailResponse(
                r.getId(),
                r.getStatus(),
                r.getCustomer().getName(),
                r.getCustomer().getPhone(),
                r.getSymptom().getSymptomCode(),
                r.getSymptom().getSymptomName(),
                r.getSymptomDesc(),
                r.getImageUrls(),
                r.getAppliance().getBrand(),
                r.getAppliance().getModelName(),
                r.getAppliance().getSerialNumber(),
                r.getAppliance().getPurchaseDate(),
                r.getAppliance().getWarrantyEndDate(),
                r.getAssignType(),
                r.getVisitRegion().getName(),
                r.getVisitAddressDetail(),
                r.getScheduledDate(),
                r.getScheduledTime(),
                r.getCancelReason(),
                r.getCreatedAt(),
                r.getUpdatedAt()
        );
    }
}
