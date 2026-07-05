package com.careflow.admin.dto.response;

public record RepairCostDto(
        Long id,
        Integer categoryId,
        String categoryName,
        String symptom,
        Integer avgCost
) {}