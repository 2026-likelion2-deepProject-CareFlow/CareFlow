package com.careflow.as_request.dto;

import com.careflow.as_request.entity.AsRequest;
import com.careflow.common.enums.AsStatus;
import com.careflow.common.enums.AssignType;
import com.careflow.user.entity.User;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 고객용 A/S 요청 단건 상세 응답 DTO
 * - 대행사용(AgencyAsRequestDetailResponse)과 달리 고객 개인정보 대신 배정 기사 정보 포함
 * - 배정 전 상태(PENDING 등)이면 engineerName, engineerPhone은 null
 */
public record CustomerAsRequestDetailResponse(
        Long requestId,
        AsStatus status,
        AssignType assignType,
        // 가전 정보
        String brand,
        String modelName,
        String serialNumber,
        LocalDate purchaseDate,
        LocalDate warrantyEndDate,
        // 증상 정보
        String symptomCode,
        String symptomName,
        String symptomDesc,
        String imageUrls,
        // 방문 정보
        String visitRegionName,
        String visitAddressDetail,
        LocalDate scheduledDate,
        String scheduledTime,
        // 배정 기사 정보 (배정 전 null)
        String engineerName,
        String engineerPhone,
        // 기타
        String cancelReason,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
    public static CustomerAsRequestDetailResponse from(AsRequest r, User engineer) {
        return new CustomerAsRequestDetailResponse(
                r.getId(),
                r.getStatus(),
                r.getAssignType(),
                r.getAppliance().getBrand(),
                r.getAppliance().getModelName(),
                r.getAppliance().getSerialNumber(),
                r.getAppliance().getPurchaseDate(),
                r.getAppliance().getWarrantyEndDate(),
                r.getSymptom().getSymptomCode(),
                r.getSymptom().getSymptomName(),
                r.getSymptomDesc(),
                r.getImageUrls(),
                r.getVisitRegion().getName(),
                r.getVisitAddressDetail(),
                r.getScheduledDate(),
                r.getScheduledTime(),
                engineer != null ? engineer.getName() : null,
                engineer != null ? engineer.getPhone() : null,
                r.getCancelReason(),
                r.getCreatedAt(),
                r.getUpdatedAt()
        );
    }
}
