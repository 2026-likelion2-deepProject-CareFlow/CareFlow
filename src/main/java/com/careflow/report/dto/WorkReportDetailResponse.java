package com.careflow.report.dto;

import com.careflow.report.domain.entity.WorkReport;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Getter
@Builder
public class WorkReportDetailResponse {
    private final Long reportId;
    private final Long requestId;
    private final String engineerName;
    private final String diagnosisResult;
    private final Integer workDurationMin;
    private final Integer finalAmount;
    private final String memo;
    private final String imageUrls;
    private final boolean customerApproved;
    private final LocalDateTime approvedAt;
    private final LocalDateTime submittedAt;

    // 교체된 부품 목록
    private final List<PartDetailDto> parts;

    @Getter
    @Builder
    public static class PartDetailDto {
        private final String partName;
        private final Integer quantity;
        private final Integer appliedUnitPrice;
    }

    public static WorkReportDetailResponse from(WorkReport report) {
        List<PartDetailDto> partDtos = report.getParts().stream()
                .map(part -> PartDetailDto.builder()
                        .partName(part.getRepairPart().getPartName())
                        .quantity(part.getQuantity())
                        .appliedUnitPrice(part.getAppliedUnitPrice())
                        .build())
                .collect(Collectors.toList());

        return WorkReportDetailResponse.builder()
                .reportId(report.getReportId())
                .requestId(report.getAsRequest().getId())
                .engineerName(report.getEngineer().getName())
                .diagnosisResult(report.getDiagnosisResult().name())
                .workDurationMin(report.getWorkDurationMin())
                .finalAmount(report.getFinalAmount())
                .memo(report.getMemo())
                .imageUrls(report.getImageUrls())
                .customerApproved(report.isCustomerApproved())
                .approvedAt(report.getApprovedAt())
                .submittedAt(report.getSubmittedAt())
                .parts(partDtos)
                .build();
    }
}