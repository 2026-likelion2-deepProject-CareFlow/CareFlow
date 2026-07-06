package com.careflow.as_request.dto;

public record ExpectedRepairCostResponse(
        Long symptomId,
        Integer avgCost,   // Quartz 배치로 집계 전이면 null — 프론트에서 "데이터 수집 중" 표시
        Integer sampleCount
) {}
