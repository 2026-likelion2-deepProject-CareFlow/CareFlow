package com.careflow.settlement.dto;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
public class EngineerPerformanceResponse {
    private int year;
    private int month;
    private List<EngineerPerformanceItem> engineers;
}
