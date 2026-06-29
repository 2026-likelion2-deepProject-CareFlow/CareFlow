package com.careflow.part.dto;

import com.careflow.part.domain.entity.RepairPart;
import lombok.Getter;

@Getter
public class RepairPartResponse {
    private final Long repairPartId;
    private final String partCode;
    private final String partName;
    private final String spec;
    private final Integer baseUnitPrice;

    public RepairPartResponse(RepairPart part) {
        this.repairPartId = part.getRepairPartId();
        this.partCode = part.getPartCode();
        this.partName = part.getPartName();
        this.spec = part.getSpec();
        this.baseUnitPrice = part.getBaseUnitPrice();
    }
}