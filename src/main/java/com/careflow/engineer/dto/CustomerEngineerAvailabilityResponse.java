package com.careflow.engineer.dto;

import lombok.Builder;
import lombok.Getter;

import java.util.List;
import java.util.Map;

/**
 * 고객용 수동 배정 - 선택 기사 가능 일정 응답 DTO
 * GET /api/customers/{customerId}/engineers/{engineerId}/availability
 */
@Getter
@Builder
public class CustomerEngineerAvailabilityResponse {

    private Long engineerId;
    // 날짜("yyyy-MM-dd") -> 가능한 시작 시각("HH:mm") 목록
    private Map<String, List<String>> availableDates;
}
