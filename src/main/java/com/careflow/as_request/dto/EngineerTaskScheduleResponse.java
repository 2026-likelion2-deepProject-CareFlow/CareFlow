package com.careflow.as_request.dto;

import com.careflow.assignment.entity.AsAssignment;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDate;

/**
 * 수리 기사 A/S 작업 일정 응답 DTO
 * - 기사 본인 조회(GET /api/engineer/schedule)와
 *   대행사 관리자 조회(GET /api/agency/engineers/{id}/schedule) 공용
 */
@Getter
@Builder
public class EngineerTaskScheduleResponse {

    private Long requestId;
    private LocalDate scheduledDate;
    private String scheduledTime;

    // 고객 정보
    private String customerName;
    private String customerPhone;

    // 가전 정보
    private String applianceBrand;
    private String applianceModelName;

    // 증상 정보
    private String symptomName;

    // 방문 주소
    private String visitRegionName;
    private String visitAddressDetail;

    // 상태
    private String requestStatus;
    private String assignmentStatus;

    public static EngineerTaskScheduleResponse from(AsAssignment assignment) {
        var req = assignment.getAsRequest();
        return EngineerTaskScheduleResponse.builder()
                .requestId(req.getId())
                .scheduledDate(req.getScheduledDate())
                .scheduledTime(req.getScheduledTime())
                .customerName(req.getCustomer().getName())
                .customerPhone(req.getCustomer().getPhone())
                .applianceBrand(req.getAppliance().getBrand())
                .applianceModelName(req.getAppliance().getModelName())
                .symptomName(req.getSymptom().getSymptomName())
                .visitRegionName(req.getVisitRegion().getName())
                .visitAddressDetail(req.getVisitAddressDetail())
                .requestStatus(req.getStatus().name())
                .assignmentStatus(assignment.getStatus())
                .build();
    }
}
