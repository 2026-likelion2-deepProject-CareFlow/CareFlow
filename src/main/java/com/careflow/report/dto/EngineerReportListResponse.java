package com.careflow.report.dto;

import com.careflow.assignment.entity.AsAssignment;
import com.careflow.report.domain.entity.WorkReport;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

@Getter
@Builder
public class EngineerReportListResponse {
    private final Long reportId;
    private final Long requestId;        // API 통신용 숫자 PK (as_requests.id)
    private final String requestCode;    // 화면 표시용 포맷팅 문자열 (예: AS-20240618-0001)
    private final String customerName;
    private final String productName;
    private final String modelNo;
    private final String workDate;       // 포맷팅 됨 (예: 2024.06.02 (일))
    private final String workTimeStart;
    private final String workTimeEnd;
    private final String status;         // DRAFT, SUBMITTED, APPROVED
    private final String requestStatus;  // 실제 A/S 진행 상태 (WAITING, ACCEPTED, IN_PROGRESS, COMPLETED 등)
    private final String diagnosisResult;
    private final Integer finalAmount;
    private final String submittedAt;
    private final String approvedAt;

    public static EngineerReportListResponse from(AsAssignment assignment) {
        var req = assignment.getAsRequest();
        var app = req.getAppliance();
        WorkReport report = req.getWorkReport();

        // 1. 요청 번호 포맷팅 (예: AS-20240618-0001)
        String dateStr = req.getCreatedAt().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        String formattedRequestCode = String.format("AS-%s-%04d", dateStr, req.getId());

        // 2. 날짜 및 요일 포맷팅 (예: 2024.06.02 (일))
        String[] days = {"", "월", "화", "수", "목", "금", "토", "일"};
        String dayOfWeek = days[req.getScheduledDate().getDayOfWeek().getValue()];
        String formattedWorkDate = req.getScheduledDate().format(DateTimeFormatter.ofPattern("yyyy.MM.dd")) + " (" + dayOfWeek + ")";

        // 3. 작업 종료 시간 계산 (시작 시간 + 2시간으로 산정)
        String endTime = LocalTime.parse(req.getScheduledTime()).plusHours(2).toString();

        // 4. 상태(Status) 논리적 매핑
        String mappedStatus = "DRAFT"; // 기본값 (보고서 없음)
        if (report != null) {
            mappedStatus = report.isCustomerApproved() ? "APPROVED" : "SUBMITTED";
        }

        DateTimeFormatter timeFormatter = DateTimeFormatter.ofPattern("yyyy.MM.dd HH:mm");

        return EngineerReportListResponse.builder()
                .reportId(report != null ? report.getReportId() : null)
                .requestId(req.getId())                   // 🌟 통신용 숫자 PK
                .requestCode(formattedRequestCode)        // 🌟 전시용 포맷 문자열
                .customerName(req.getCustomer().getName())
                .productName(app.getBrand() + " " + app.getModelName())
                .modelNo(app.getModelName())
                .workDate(formattedWorkDate)
                .workTimeStart(req.getScheduledTime())
                .workTimeEnd(endTime)
                .status(mappedStatus)
                .requestStatus(req.getStatus().name()) // 실제 A/S 상태 매핑
                .diagnosisResult(report != null ? report.getDiagnosisResult().name() : null)
                .finalAmount(report != null ? report.getFinalAmount() : null)
                .submittedAt(report != null && report.getSubmittedAt() != null ? report.getSubmittedAt().format(timeFormatter) : null)
                .approvedAt(report != null && report.getApprovedAt() != null ? report.getApprovedAt().format(timeFormatter) : null)
                .build();
    }
}