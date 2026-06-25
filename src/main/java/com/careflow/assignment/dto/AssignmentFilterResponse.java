package com.careflow.assignment.dto;

import com.careflow.assignment.entity.AsAssignment;
import com.careflow.common.enums.AssignType;

import java.time.LocalDate;
import java.time.LocalDateTime;

// 배차 현황 모니터링용 응답 DTO
// AssignmentResponse(기본 목록)보다 방문 일정·증상·주소 정보를 추가로 포함
public record AssignmentFilterResponse(
        Long assignmentId,
        String assignmentStatus,
        AssignType assignMethod,
        LocalDateTime assignedAt,
        LocalDateTime acceptedAt,

        Long requestId,
        LocalDate scheduledDate,
        String scheduledTime,
        String visitAddressDetail,

        String symptomName,

        Long engineerId,
        String engineerName
) {
    public static AssignmentFilterResponse from(AsAssignment a) {
        var req     = a.getAsRequest();
        var engineer = a.getEngineer();

        return new AssignmentFilterResponse(
                a.getId(),
                a.getStatus(),
                a.getAssignMethod(),
                a.getAssignedAt(),
                a.getAcceptedAt(),

                req.getId(),
                req.getScheduledDate(),
                req.getScheduledTime(),
                req.getVisitAddressDetail(),

                req.getSymptom().getSymptomName(),

                engineer.getId(),
                engineer.getName()
        );
    }
}
