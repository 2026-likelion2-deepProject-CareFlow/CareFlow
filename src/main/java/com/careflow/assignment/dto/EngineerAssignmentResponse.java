package com.careflow.assignment.dto;

import com.careflow.assignment.entity.AsAssignment;
import lombok.Builder;
import lombok.Getter;

import java.time.format.DateTimeFormatter;

@Getter
@Builder
public class EngineerAssignmentResponse {
    private Long assignmentId;
    private String requestId;
    private Long asRequestId;
    private String assignStatus;
    private String logStatus;
    private boolean isNew;

    private String productName;
    private String modelNo;
    private String productImageUrl;
    private String purchaseDate;
    private String warrantyEnd;

    private Long customerId;
    private String customerName;
    private String customerPhone;
    private String address;

    private String symptomDesc;
    private String scheduledDate;
    private String scheduledTime;

    private Integer estimatedAvgCost;

    public static EngineerAssignmentResponse of(AsAssignment assignment, String latestLogStatus, Integer avgCost) {
        // 🌟 방어 로직: 연관 엔티티를 안전하게 변수로 추출
        var req = assignment.getAsRequest();
        var app = req != null ? req.getAppliance() : null;
        var cust = req != null ? req.getCustomer() : null;

        // 1. Request ID 포맷팅
        String formattedRequestId = "AS-00000000-0000";
        if (req != null && req.getCreatedAt() != null) {
            String dateStr = req.getCreatedAt().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
            formattedRequestId = String.format("AS-%s-%04d", dateStr, req.getId());
        }

        // 2. 날짜 포맷팅
        String formattedScheduledDate = "일정 미정";
        if (req != null && req.getScheduledDate() != null) {
            String[] days = {"", "월", "화", "수", "목", "금", "토", "일"};
            String dayOfWeek = days[req.getScheduledDate().getDayOfWeek().getValue()];
            formattedScheduledDate = req.getScheduledDate().format(DateTimeFormatter.ofPattern("yyyy.MM.dd")) + " (" + dayOfWeek + ")";
        }

        // 3. 주소 조립
        String regionName = (cust != null && cust.getRegionId() != null) ? cust.getRegionId().getName() + " " : "";
        String detailAddress = (req != null && req.getVisitAddressDetail() != null) ? req.getVisitAddressDetail() : "";
        String fullAddress = (regionName + detailAddress).trim();

        // 4. 제품 정보 조립
        String brand = app != null && app.getBrand() != null ? app.getBrand() : "브랜드 미상";
        String categoryName = app != null && app.getCategory() != null ? app.getCategory().getName() : "";
        String productName = (brand + " " + categoryName).trim();

        return EngineerAssignmentResponse.builder()
                .assignmentId(assignment.getId())
                .requestId(formattedRequestId)
                .asRequestId(req != null ? req.getId() : null)
                .assignStatus(assignment.getStatus() != null ? assignment.getStatus() : "WAITING")
                .logStatus(latestLogStatus != null ? latestLogStatus : "WAITING")
                .isNew("WAITING".equals(assignment.getStatus()))
                .productName(productName)
                .modelNo(app != null && app.getModelName() != null ? app.getModelName() : "모델 미상")
                .productImageUrl(app != null ? app.getImageUrl() : null)
                .purchaseDate(app != null && app.getPurchaseDate() != null ? app.getPurchaseDate().toString() : "모름")
                .warrantyEnd(app != null && app.getWarrantyEndDate() != null ? app.getWarrantyEndDate().toString() : "만료")
                .customerId(cust != null ? cust.getId() : null)
                .customerName(cust != null && cust.getName() != null ? cust.getName() : "고객 미상")
                .customerPhone(cust != null && cust.getPhone() != null ? cust.getPhone() : "연락처 미상")
                .address(fullAddress)
                .symptomDesc(req != null && req.getSymptomDesc() != null ? req.getSymptomDesc() : "증상 미기재")
                .scheduledDate(formattedScheduledDate)
                .scheduledTime(req != null && req.getScheduledTime() != null ? req.getScheduledTime() : "시간 미정")
                .estimatedAvgCost(avgCost != null ? avgCost : 0)
                .build();
    }
}