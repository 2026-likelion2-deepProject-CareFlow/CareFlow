package com.careflow.report.service;

import com.careflow.appliance.entity.Appliance;
import com.careflow.appliance.entity.HealthCertificate;
import com.careflow.appliance.repository.ApplianceRepository;
import com.careflow.appliance.repository.HealthCertificateRepository;
import com.careflow.as_request.entity.AsRequest;
import com.careflow.as_request.repository.AsRequestRepository;
import com.careflow.as_status_log.entity.AsStatusLog;
import com.careflow.as_status_log.repository.AsStatusLogRepository;
import com.careflow.assignment.entity.AsAssignment;
import com.careflow.assignment.repository.AsAssignmentRepository;
import com.careflow.engineer.dto.CreateWorkReportRequest;
import com.careflow.part.domain.entity.RepairPart;
import com.careflow.part.repository.RepairPartRepository;
import com.careflow.report.domain.entity.WorkReport;
import com.careflow.report.domain.entity.WorkReportPart;
import com.careflow.report.domain.enums.DiagnosisResult;
import com.careflow.report.domain.enums.PartImportance;
import com.careflow.report.dto.EngineerReportListResponse;
import com.careflow.report.dto.RepairHistoryResponse;
import com.careflow.report.dto.WorkReportDetailResponse;
import com.careflow.report.repository.WorkReportRepository;
import com.careflow.user.entity.User;
import com.careflow.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.context.ApplicationEventPublisher;
import com.careflow.notification.event.AsStatusNotificationEvent;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class WorkReportService {

    private final WorkReportRepository workReportRepository;
    private final RepairPartRepository repairPartRepository;
    private final HealthCertificateRepository healthCertificateRepository;
    private final AsRequestRepository asRequestRepository;
    private final UserRepository userRepository;
    private final AsAssignmentRepository asAssignmentRepository;
    private final ApplianceRepository applianceRepository;
    private final AsStatusLogRepository asStatusLogRepository;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional
    public Long submitWorkReport(Long engineerId, CreateWorkReportRequest request) {
        User engineer = userRepository.findById(engineerId)
                .orElseThrow(() -> new IllegalArgumentException("기사 정보를 찾을 수 없습니다."));

        AsRequest asRequest = asRequestRepository.findById(request.getRequestId())
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 A/S 신청 건입니다."));

        List<AsAssignment> assignments = asAssignmentRepository.findByAsRequest_Id(asRequest.getId());
        boolean isAssignedToMe = assignments.stream()
                .anyMatch(a -> a.getEngineer().getId().equals(engineerId)
                        && ("ACCEPTED".equals(a.getStatus()) || "COMPLETED".equals(a.getStatus())));

        if (!isAssignedToMe) {
            throw new IllegalStateException("본인에게 배정된 A/S 건만 보고서를 작성할 수 있습니다.");
        }

        if (workReportRepository.existsByAsRequest_Id(asRequest.getId())) {
            throw new IllegalStateException("해당 A/S 건에 대해 이미 제출된 보고서가 존재합니다.");
        }

        String oldStatusStr = asRequest.getStatus().name();

        asRequest.completeWork();

        WorkReport report = WorkReport.builder()
                .asRequest(asRequest)
                .engineer(engineer)
                .diagnosisResult(DiagnosisResult.valueOf(request.getDiagnosisResult()))
                .workDurationMin(request.getWorkDurationMin())
                .finalAmount(request.getFinalAmount())
                .memo(request.getMemo())
                .imageUrls(request.getImageUrls())
                .build();

        if (request.getParts() != null && !request.getParts().isEmpty()) {
            for (CreateWorkReportRequest.PartDto partDto : request.getParts()) {
                RepairPart repairPart = repairPartRepository.findById(partDto.getRepairPartId())
                        .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 부품입니다."));

                int appliedPrice = partDto.getAppliedUnitPrice() != null ?
                        partDto.getAppliedUnitPrice() : repairPart.getBaseUnitPrice();

                WorkReportPart reportPart = WorkReportPart.builder()
                        .repairPart(repairPart)
                        .quantity(partDto.getQuantity())
                        .appliedUnitPrice(appliedPrice)
                        .build();

                report.addPart(reportPart);
            }
        }

        WorkReport savedReport = workReportRepository.save(report);
        workReportRepository.flush();

        syncHealthCertificate(asRequest.getAppliance());

        String actionMemo = engineer.getName() + " 기사님이 작업을 완료하고 보고서를 제출했습니다.";
        AsStatusLog statusLog = AsStatusLog.builder()
                .asRequest(asRequest)
                .changedBy(engineer)
                .fromStatus(oldStatusStr)
                .toStatus("COMPLETED")
                .memo(actionMemo)
                .build();
        asStatusLogRepository.save(statusLog);

        String title = "A/S 수리 완료 및 보고서 도착";
        String applianceInfo = asRequest.getAppliance().getBrand() + " " + asRequest.getAppliance().getModelName();
        String body = "[" + applianceInfo + "] " + actionMemo;

        eventPublisher.publishEvent(new AsStatusNotificationEvent(asRequest.getCustomer(), title, body));

        if (asRequest.getAgency() != null && asRequest.getAgency().getRepresentativeId() != null) {
            eventPublisher.publishEvent(new AsStatusNotificationEvent(asRequest.getAgency().getRepresentativeId(), title, body));
        }

        return savedReport.getReportId();
    }

    @Transactional(readOnly = true)
    public List<RepairHistoryResponse> getApplianceRepairHistory(Long userId, String role, Long applianceId) throws IllegalAccessException {

        Appliance appliance = applianceRepository.findById(applianceId)
                .orElseThrow(() -> new NoSuchElementException("존재하지 않는 가전제품입니다."));

        if ("CUSTOMER".equals(role) && !appliance.getUser().getId().equals(userId)) {
            throw new IllegalAccessException("본인 소유의 가전제품 수리 이력만 조회할 수 있습니다.");
        }

        List<WorkReport> reports = workReportRepository.findByApplianceIdOrderBySubmittedAtDesc(applianceId);

        return reports.stream()
                .map(RepairHistoryResponse::new)
                .collect(Collectors.toList());
    }

    /**
     * 작업 완료 보고서 상세 조회 (고객 및 기사 공용)
     */
    @Transactional(readOnly = true)
    public WorkReportDetailResponse getWorkReportDetail(Long userId, String role, Long reportId) throws IllegalAccessException {
        WorkReport report = workReportRepository.findByIdWithParts(reportId)
                .orElseThrow(() -> new NoSuchElementException("존재하지 않는 보고서입니다."));

        // 권한 분리 방어 로직 (BOLA 방어)
        if ("CUSTOMER".equals(role)) {
            if (!report.getAsRequest().getCustomer().getId().equals(userId)) {
                throw new IllegalAccessException("본인의 A/S 보고서만 조회할 수 있습니다.");
            }
        } else if ("ENGINEER".equals(role)) {
            if (!report.getEngineer().getId().equals(userId)) {
                throw new IllegalAccessException("본인이 작성한 보고서만 조회할 수 있습니다.");
            }
        } else {
            throw new IllegalAccessException("보고서 조회 권한이 없습니다.");
        }

        // 🌟 타임라인 생성을 위한 상태 로그 추출
        List<AsStatusLog> logs = asStatusLogRepository.findByAsRequest_IdOrderByCreatedAtAsc(report.getAsRequest().getId());
        Map<String, LocalDateTime> statusTimeMap = logs.stream()
                .filter(log -> log.getToStatus() != null)
                .collect(Collectors.toMap(
                        AsStatusLog::getToStatus,
                        AsStatusLog::getCreatedAt,
                        (existing, replacement) -> existing // 중복 시 최초값 유지
                ));

        // .from 대신 우리가 만든 .of 사용!
        return WorkReportDetailResponse.of(report, statusTimeMap);
    }

    /**
     * 작업 완료 보고서 고객 승인 처리
     */
    @Transactional
    public void approveWorkReport(Long customerId, Long reportId) throws IllegalAccessException {
        WorkReport report = workReportRepository.findById(reportId)
                .orElseThrow(() -> new NoSuchElementException("존재하지 않는 보고서입니다."));

        // 철저한 권한 검증: 고객 본인인가?
        if (!report.getAsRequest().getCustomer().getId().equals(customerId)) {
            throw new IllegalAccessException("본인의 A/S 보고서만 승인할 수 있습니다.");
        }

        // 엔티티 도메인 메서드를 통한 상태 업데이트 (더티 체킹)
        report.approveByCustomer();
    }

    /**
     * [기사용 API] 기사 본인의 작업 보고서 목록 전체 조회 (페이징)
     * 배차(AsAssignment) 내역을 기준으로 작성 대기(DRAFT), 제출(SUBMITTED), 승인(APPROVED) 상태 매핑
     */
    @Transactional(readOnly = true)
    public Page<EngineerReportListResponse> getEngineerWorkReports(Long engineerId, Pageable pageable) {
        Page<AsAssignment> assignments = asAssignmentRepository.findWorkReportListByEngineerId(engineerId, pageable);

        return assignments.map(EngineerReportListResponse::from);
    }

    @Transactional
    public void cancelApprovalRequest(Long engineerId, Long reportId) {
        WorkReport report = workReportRepository.findById(reportId)
                .orElseThrow(() -> new NoSuchElementException("보고서를 찾을 수 없습니다."));

        if (!report.getEngineer().getId().equals(engineerId)) {
            throw new IllegalStateException("본인이 작성한 보고서만 취소할 수 있습니다.");
        }

        if (report.isCustomerApproved()) {
            throw new IllegalStateException("이미 고객이 승인한 보고서는 취소할 수 없습니다.");
        }

        AsRequest request = report.getAsRequest();
        String oldStatusStr = request.getStatus().name();

        request.revertToInProgress();

        AsStatusLog statusLog = AsStatusLog.builder()
                .asRequest(request)
                .changedBy(report.getEngineer())
                .fromStatus(oldStatusStr)
                .toStatus("IN_PROGRESS")
                .memo("보고서 제출이 취소되어 작업 중 상태로 변경되었습니다.")
                .build();
        asStatusLogRepository.save(statusLog);

        workReportRepository.delete(report);
        workReportRepository.flush();

        syncHealthCertificate(request.getAppliance());
    }

    // 🌟 신규 추가: 해당 가전의 모든 보고서를 모아 진단서를 완벽하게 재계산하는 핵심 헬퍼 메서드
    private void syncHealthCertificate(Appliance appliance) {
        List<WorkReport> allReports = workReportRepository.findByApplianceIdOrderBySubmittedAtDesc(appliance.getId());

        int totalRepairCount = allReports.size();
        int totalCriticalParts = 0;
        PartImportance worstImportance = null;
        LocalDateTime lastRepaired = allReports.isEmpty() ? null : allReports.get(0).getSubmittedAt();

        for (WorkReport r : allReports) {
            for (com.careflow.report.domain.entity.WorkReportPart p : r.getParts()) {
                PartImportance imp = p.getRepairPart().getImportance();
                if (imp == PartImportance.CRITICAL) {
                    totalCriticalParts++;
                }
                if (worstImportance == null || imp.getSeverity() < worstImportance.getSeverity()) {
                    worstImportance = imp;
                }
            }
        }

        HealthCertificate certificate = healthCertificateRepository.findByAppliance_Id(appliance.getId())
                .orElseGet(() -> healthCertificateRepository.save(
                        HealthCertificate.builder().appliance(appliance).build()
                ));

        certificate.recalculate(totalRepairCount, totalCriticalParts, worstImportance, lastRepaired, appliance.getPurchaseDate());
    }
}