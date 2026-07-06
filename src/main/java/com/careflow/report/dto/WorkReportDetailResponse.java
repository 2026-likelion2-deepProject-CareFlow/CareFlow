package com.careflow.report.dto;

import com.careflow.report.domain.entity.WorkReport;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Getter
@Builder
public class WorkReportDetailResponse {
    private final Long reportId;
    private final Long requestId;
    private final String requestCode; // 화면 전시용 포맷팅 접수번호 (예: AS-20260620-0040)
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
        private final String partCode;
        private final Integer quantity;
        private final Integer appliedUnitPrice;
        private final String importance; // 부품 중요도(MINOR/NORMAL/MAJOR/CRITICAL)
    }

    public static WorkReportDetailResponse of(WorkReport report, Map<String, LocalDateTime> statusTimeMap) {
        List<PartDetailDto> partDtos = report.getParts().stream()
                .map(part -> PartDetailDto.builder()
                        .partName(part.getRepairPart().getPartName())
                        .partCode(part.getRepairPart().getPartCode())
                        .quantity(part.getQuantity())
                        .appliedUnitPrice(part.getAppliedUnitPrice())
                        .importance(part.getRepairPart().getImportance().name())
                        .build())
                .collect(Collectors.toList());

        // 주소 조립
        String address = (report.getAsRequest().getCustomer().getRegionId() != null
                ? report.getAsRequest().getCustomer().getRegionId().getName() + " " : "")
                + (report.getAsRequest().getCustomer().getAddressDetail() != null
                ? report.getAsRequest().getCustomer().getAddressDetail() : "");

        // 접수번호 포맷팅 (AS-YYYYMMDD-0000)
        String dateStr = report.getAsRequest().getCreatedAt().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        String formattedRequestCode = String.format("AS-%s-%04d", dateStr, report.getAsRequest().getId());

        return WorkReportDetailResponse.builder()
                .reportId(report.getReportId())
                .requestId(report.getAsRequest().getId())
                .requestCode(formattedRequestCode) // 🌟 추가된 필드 매핑
                .engineerName(report.getEngineer().getName())
                .diagnosisResult(report.getDiagnosisResult().name())
                .workDurationMin(report.getWorkDurationMin())
                .finalAmount(report.getFinalAmount())
                .memo(report.getMemo())
                .imageUrls(report.getImageUrls())
                .customerApproved(report.isCustomerApproved())
                .approvedAt(report.getApprovedAt())
                .submittedAt(report.getSubmittedAt())
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