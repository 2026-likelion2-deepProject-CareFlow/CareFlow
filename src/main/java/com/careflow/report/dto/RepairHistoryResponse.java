package com.careflow.report.dto;

import com.careflow.report.domain.entity.WorkReport;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
public class RepairHistoryResponse {

    private final Long reportId;
    private final LocalDateTime submittedAt;
    private final String symptomName;
    private final String engineerName;
    private final String diagnosisResult;
    private final Integer finalAmount;

    public RepairHistoryResponse(WorkReport report) {
        this.reportId = report.getReportId();
        this.submittedAt = report.getSubmittedAt();
        // N+1 문제가 발생하지 않도록 Repository에서 FETCH JOIN으로 한 번에 가져올 예정입니다.
        this.symptomName = report.getAsRequest().getSymptom().getSymptomName();
        this.engineerName = report.getEngineer().getName();
        this.diagnosisResult = report.getDiagnosisResult().name();
        this.finalAmount = report.getFinalAmount();
    }
}