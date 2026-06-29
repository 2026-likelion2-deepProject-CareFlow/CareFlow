package com.careflow.assignment.dto;

import com.careflow.assignment.entity.AsAssignment;
import com.careflow.as_status_log.entity.AsStatusLog;
import com.careflow.engineer.domain.entity.EngineerProfile;
import com.careflow.report.domain.entity.WorkReport;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public record AssignmentDetailResponse(

        // ── 배차 정보 ──────────────────────────────────
        Long assignmentId,
        String assignmentStatus,
        LocalDateTime assignedAt,

        // ── A/S 요청 정보 ───────────────────────────────
        Long requestId,
        LocalDate scheduledDate,
        String scheduledTime,
        String symptomDesc,
        String visitAddressDetail,

        // ── 증상 정보 ───────────────────────────────────
        String symptomName,

        // ── 고객 정보 ───────────────────────────────────
        String customerName,
        String customerPhone,

        // ── 가전 정보 ───────────────────────────────────
        String modelName,
        LocalDate purchaseDate,
        LocalDate warrantyEndDate,

        // ── 예상 수리 비용 (Quartz 미집계 시 null) ──────
        Integer avgRepairCost,

        // ── 처리 이력 ───────────────────────────────────
        List<StatusLogInfo> statusLogs,

        // ── 이전 수리 이력 (동일 가전 기준) ─────────────
        List<WorkReportInfo> previousWorkReports,

        // ── 배정 기사 프로필 (미등록 시 null) ───────────
        EngineerProfileInfo engineerProfile

) {
    /** as_status_logs 행 1건 */
    public record StatusLogInfo(
            Long logId,
            String fromStatus,
            String toStatus,
            String memo,
            LocalDateTime createdAt
    ) {
        public static StatusLogInfo from(AsStatusLog log) {
            return new StatusLogInfo(
                    log.getId(),
                    log.getFromStatus(),
                    log.getToStatus(),
                    log.getMemo(),
                    log.getCreatedAt()
            );
        }
    }

    /** work_reports 행 1건 */
    public record WorkReportInfo(
            Long reportId,
            Long requestId,
            String diagnosisResult,
            Integer workDurationMin,
            Integer finalAmount,
            String memo,
            boolean customerApproved,
            LocalDateTime submittedAt
    ) {
        public static WorkReportInfo from(WorkReport report) {
            return new WorkReportInfo(
                    report.getReportId(),
                    report.getAsRequest().getId(),
                    report.getDiagnosisResult().name(),
                    report.getWorkDurationMin(),
                    report.getFinalAmount(),
                    report.getMemo(),
                    report.isCustomerApproved(),
                    report.getSubmittedAt()
            );
        }
    }

    /** engineer_profiles 정보 */
    public record EngineerProfileInfo(
            Long profileId,
            String skillLevel,
            BigDecimal avgRating,
            int totalReviews,
            String introduction
    ) {
        public static EngineerProfileInfo from(EngineerProfile profile) {
            return new EngineerProfileInfo(
                    profile.getProfileId(),
                    profile.getSkillLevel().name(),
                    profile.getAvgRating(),
                    profile.getTotalReviews(),
                    profile.getIntroduction()
            );
        }
    }

    /** AsAssignment + 조회된 부가 정보를 조합해 최종 DTO 생성 */
    public static AssignmentDetailResponse of(
            AsAssignment assignment,
            Integer avgRepairCost,
            List<StatusLogInfo> statusLogs,
            List<WorkReportInfo> previousWorkReports,
            EngineerProfileInfo engineerProfile
    ) {
        var req = assignment.getAsRequest();
        var appliance = req.getAppliance();
        var customer = req.getCustomer();

        return new AssignmentDetailResponse(
                assignment.getId(),
                assignment.getStatus(),
                assignment.getAssignedAt(),

                req.getId(),
                req.getScheduledDate(),
                req.getScheduledTime(),
                req.getSymptomDesc(),
                req.getVisitAddressDetail(),

                req.getSymptom().getSymptomName(),

                customer.getName(),
                customer.getPhone(),

                appliance.getModelName(),
                appliance.getPurchaseDate(),
                appliance.getWarrantyEndDate(),

                avgRepairCost,
                statusLogs,
                previousWorkReports,
                engineerProfile
        );
    }
}
