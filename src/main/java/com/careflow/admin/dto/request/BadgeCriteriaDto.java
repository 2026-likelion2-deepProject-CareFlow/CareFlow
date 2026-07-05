package com.careflow.admin.dto.request;

public record BadgeCriteriaDto(
        String minGrade,
        Integer minScore
) {}