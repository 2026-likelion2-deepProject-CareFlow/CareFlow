package com.careflow.agency.dto.response;

import com.careflow.as_request.entity.AsRequest;

import java.time.LocalDate;
import java.time.LocalDateTime;

// GET /api/agency/customers/{userId}/as-requests 응답 DTO
public record AgencyCustomerAsRequestResponse(
        Long requestId,
        String applianceBrand,
        String applianceModelName,
        String symptomName,
        String symptomDesc,
        String visitAddress,
        LocalDate scheduledDate,
        String scheduledTime,
        String status,
        LocalDateTime createdAt
) {
    // regions.name + as_requests.visit_address_detail 조합 — agency-customer-list.md의 address 조합 방식과 동일
    public static AgencyCustomerAsRequestResponse from(AsRequest request) {
        String regionName = request.getVisitRegion() != null ? request.getVisitRegion().getName() : null;
        String detail = request.getVisitAddressDetail();
        String visitAddress = java.util.stream.Stream.of(regionName, detail)
                .filter(s -> s != null && !s.isBlank())
                .reduce((a, b) -> a + " " + b)
                .orElse("");

        return new AgencyCustomerAsRequestResponse(
                request.getId(),
                request.getAppliance().getBrand(),
                request.getAppliance().getModelName(),
                request.getSymptom().getSymptomName(),
                request.getSymptomDesc(),
                visitAddress,
                request.getScheduledDate(),
                request.getScheduledTime(),
                request.getStatus().name(),
                request.getCreatedAt());
    }
}
