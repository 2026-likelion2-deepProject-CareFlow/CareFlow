package com.careflow.report.dto;

import com.careflow.report.domain.entity.WorkReport;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
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

    // 🌟 프론트 요구 추가 필드 (고객, 가전 정보)
    private final String modelNo;
    private final String serialNo;
    private final String customerPhone;
    private final String customerAddress;

    // 🌟 타임라인 (AsStatusLog 추출)
    private final LocalDateTime arrivedAt;
    private final LocalDateTime workStartAt;
    private final LocalDateTime workEndAt;

    private final List<PartDetailDto> parts;

    @Getter
    @Builder
    public static class PartDetailDto {
        private final String partName;
        private final String partCode; // 🌟 추가
        private final Integer quantity;
        private final Integer appliedUnitPrice;
    }

    public static WorkReportDetailResponse of(WorkReport report, Map<String, LocalDateTime> statusTimeMap) {
        List<PartDetailDto> partDtos = report.getParts().stream()
                .map(part -> PartDetailDto.builder()
                        .partName(part.getRepairPart().getPartName())
                        .partCode(part.getRepairPart().getPartCode()) // 🌟 추가
                        .quantity(part.getQuantity())
                        .appliedUnitPrice(part.getAppliedUnitPrice())
                        .build())
                .collect(Collectors.toList());

        // 주소 조립
        String address = (report.getAsRequest().getCustomer().getRegionId() != null
                ? report.getAsRequest().getCustomer().getRegionId().getName() + " " : "")
                + (report.getAsRequest().getCustomer().getAddressDetail() != null
                ? report.getAsRequest().getCustomer().getAddressDetail() : "");

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
                // 🌟 추가된 필드 매핑
                .modelNo(report.getAsRequest().getAppliance().getModelName())
                .serialNo(report.getAsRequest().getAppliance().getSerialNumber())
                .customerPhone(report.getAsRequest().getCustomer().getPhone())
                .customerAddress(address.trim())
                .arrivedAt(statusTimeMap.get("ENGINEER_ARRIVED"))
                .workStartAt(statusTimeMap.get("IN_PROGRESS"))
                .workEndAt(statusTimeMap.get("COMPLETED"))
                .parts(partDtos)
                .build();
    }
}