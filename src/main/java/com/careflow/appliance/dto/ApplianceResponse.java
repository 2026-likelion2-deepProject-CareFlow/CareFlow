package com.careflow.appliance.dto;

import com.careflow.appliance.entity.Appliance;
import com.careflow.common.enums.ApplianceStatus;
import com.careflow.common.enums.RegisterMethod;
import lombok.Getter;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Getter
public class ApplianceResponse {

    private final Long applianceId;
    private final Long userId;
    private final Integer categoryId;
    private final String brand;
    private final String modelName;
    private final String serialNumber;
    private final LocalDate purchaseDate;
    private final LocalDate warrantyEndDate;
    private final RegisterMethod registerMethod;
    private final ApplianceStatus status;
    private final LocalDateTime createdAt;

    private ApplianceResponse(Appliance appliance) {
        this.applianceId = appliance.getId();
        this.userId = appliance.getUser().getId();
        this.categoryId = appliance.getCategory().getCategoryId();
        this.brand = appliance.getBrand();
        this.modelName = appliance.getModelName();
        this.serialNumber = appliance.getSerialNumber();
        this.purchaseDate = appliance.getPurchaseDate();
        this.warrantyEndDate = appliance.getWarrantyEndDate();
        this.registerMethod = appliance.getRegisterMethod();
        this.status = appliance.getStatus();
        this.createdAt = appliance.getCreatedAt();
    }

    public static ApplianceResponse from(Appliance appliance) {
        return new ApplianceResponse(appliance);
    }
}
