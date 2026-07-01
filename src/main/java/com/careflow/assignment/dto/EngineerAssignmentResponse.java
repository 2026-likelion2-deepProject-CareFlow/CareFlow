// 파일 경로: src/main/java/com/careflow/assignment/dto/EngineerAssignmentResponse.java
package com.careflow.assignment.dto;

import com.careflow.assignment.entity.AsAssignment;
import lombok.Builder;
import lombok.Getter;

import java.time.format.DateTimeFormatter;

@Getter
@Builder
public class EngineerAssignmentResponse {
    private Long assignmentId;
    private String requestId;      // AS-YYYYMMDD-0001 포맷
    private String assignStatus;   // WAITING, ACCEPTED, REJECTED, COMPLETED
    private String logStatus;      // 최신 상태 (as_status_logs 기반, 기본은 WAITING)
    private boolean isNew;         // 신규 여부

    private String productName;
    private String modelNo;
    private String productImageUrl;
    private String purchaseDate;
    private String warrantyEnd;

    private String customerName;
    private String customerPhone;
    private String address;

    private String symptomDesc;
    private String scheduledDate;
    private String scheduledTime;

    // 예상 비용 (프론트에서 min/max를 원하지만 DB 구조상 avgCost만 있으므로 이를 활용)
    private Integer estimatedAvgCost;

    public static EngineerAssignmentResponse of(AsAssignment assignment, String latestLogStatus, Integer avgCost) {
        var req = assignment.getAsRequest();
        var app = req.getAppliance();
        var cust = req.getCustomer();

        // 1. Request ID 포맷팅 (AS-20240601-0001)
        String dateStr = req.getCreatedAt().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        String formattedRequestId = String.format("AS-%s-%04d", dateStr, req.getId());

        // 2. 날짜 포맷팅
        String[] days = {"", "월", "화", "수", "목", "금", "토", "일"};
        String dayOfWeek = days[req.getScheduledDate().getDayOfWeek().getValue()];
        String formattedScheduledDate = req.getScheduledDate().format(DateTimeFormatter.ofPattern("yyyy.MM.dd")) + " (" + dayOfWeek + ")";

        // 3. 주소 조립
        String fullAddress = (cust.getRegionId() != null ? cust.getRegionId().getName() + " " : "")
                + (req.getVisitAddressDetail() != null ? req.getVisitAddressDetail() : "");

        return EngineerAssignmentResponse.builder()
                .assignmentId(assignment.getId())
                .requestId(formattedRequestId)
                .assignStatus(assignment.getStatus())
                .logStatus(latestLogStatus != null ? latestLogStatus : "WAITING")
                .isNew(assignment.getStatus().equals("WAITING"))
                .productName(app.getBrand() + " " + app.getCategory().getName())
                .modelNo(app.getModelName())
                .productImageUrl(app.getImageUrl())
                .purchaseDate(app.getPurchaseDate() != null ? app.getPurchaseDate().toString() : "모름")
                .warrantyEnd(app.getWarrantyEndDate() != null ? app.getWarrantyEndDate().toString() : "만료")
                .customerName(cust.getName())
                .customerPhone(cust.getPhone())
                .address(fullAddress.trim())
                .symptomDesc(req.getSymptomDesc())
                .scheduledDate(formattedScheduledDate)
                .scheduledTime(req.getScheduledTime())
                .estimatedAvgCost(avgCost != null ? avgCost : 0)
                .build();
    }
}