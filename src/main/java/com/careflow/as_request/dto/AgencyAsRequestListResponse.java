package com.careflow.as_request.dto;

import com.careflow.as_request.entity.AsRequest;
import com.careflow.common.enums.AsStatus;
import com.careflow.common.enums.AssignType;

import java.time.LocalDate;
import java.time.LocalDateTime;

public record AgencyAsRequestListResponse(
        Long requestId,
        AsStatus status,
        String customerName,
        String customerPhone,
        String symptomName,
        String symptomDesc,
        String visitRegionName,
        String visitAddressDetail,
        LocalDate scheduledDate,
        String scheduledTime,
        AssignType assignType,
        LocalDateTime createdAt
) {
    public static AgencyAsRequestListResponse from(AsRequest r) {
        return new AgencyAsRequestListResponse(
                r.getId(),
                r.getStatus(),
                r.getCustomer().getName(),
                r.getCustomer().getPhone(),
                r.getSymptom().getSymptomName(),
                r.getSymptomDesc(),
                r.getVisitRegion().getName(),
                r.getVisitAddressDetail(),
                r.getScheduledDate(),
                r.getScheduledTime(),
                r.getAssignType(),
                r.getCreatedAt()
        );
    }
}
