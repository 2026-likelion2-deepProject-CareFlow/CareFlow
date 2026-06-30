package com.careflow.agency.dto.response;

import com.careflow.appliance.entity.Appliance;

import java.time.LocalDate;
import java.time.LocalDateTime;

// GET /api/agency/customers/{userId}/appliances 응답 DTO
public record AgencyCustomerApplianceResponse(
        Long applianceId,
        String categoryName,
        String brand,
        String modelName,
        String serialNumber,
        LocalDate purchaseDate,
        LocalDate warrantyEndDate,
        String status,
        String registerMethod,
        String imageUrl,
        LocalDateTime createdAt
) {
    public static AgencyCustomerApplianceResponse from(Appliance appliance) {
        return new AgencyCustomerApplianceResponse(
                appliance.getId(),
                appliance.getCategory().getName(),
                appliance.getBrand(),
                appliance.getModelName(),
                appliance.getSerialNumber(),
                appliance.getPurchaseDate(),
                appliance.getWarrantyEndDate(),
                appliance.getStatus().name(),
                appliance.getRegisterMethod().name(),
                appliance.getImageUrl(),
                appliance.getCreatedAt());
    }
}
