package com.careflow.as_request.dto;

import com.careflow.as_request.entity.AsRequest;
import com.careflow.common.enums.AsStatus;
import lombok.Getter;
import java.time.LocalDateTime;

@Getter
public class AsRequestResponseDto {
    private final Long asRequestId;
    private final Long applianceId;
    private final String title;
    private final String description;
    private final AsStatus asStatus;
    private final LocalDateTime createdAt;

    public AsRequestResponseDto(AsRequest entity) {
        this.asRequestId = entity.getId();
        this.applianceId = entity.getApplianceId();
        this.title = entity.getTitle();
        this.description = entity.getDescription();
        this.asStatus = entity.getAsStatus();
        this.createdAt = entity.getCreatedAt();
    }
}