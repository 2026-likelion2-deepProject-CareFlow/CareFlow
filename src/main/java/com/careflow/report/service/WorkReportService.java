package com.careflow.report.service;

import com.careflow.admin.dto.request.BadgeCriteriaDto;
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
import com.careflow.common.enums.DiagnosisResult;
import com.careflow.common.enums.PartImportance;
import com.careflow.report.dto.EngineerReportListResponse;
import com.careflow.report.dto.RepairHistoryResponse;
import com.careflow.report.dto.WorkReportDetailResponse;
import com.careflow.report.repository.WorkReportRepository;
import com.careflow.user.entity.User;
import com.careflow.user.repository.UserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.context.ApplicationEventPublisher;
import com.careflow.notification.event.AsStatusNotificationEvent;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.stream.Collectors;

@Slf4j
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
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    // 출장비(기본) — 할증(평일 18시 이후·주말·공휴일)·성수기 구분 미적용, 단일 고정값.
    // final_amount = 부품 합계 + VISIT_FEE 로 서버가 확정한다. 추후 정책 확장 시 이 상수를 정책 객체로 승격.
    private static final int VISIT_FEE = 28_000;

    @Transactional
    public Long submitWorkReport(Long engineerId, CreateWorkReportRequest request) {
        User engineer = userRepository.findById(engineerId)
                .orElseThrow(() -> new IllegalArgumentException("기사 정보를 찾을 수 없습니다."));

        AsRequest asRequest = asRequestRepository.findById(request.getRequestId())
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 A/S 신청 건입니다."));

        List<AsAssignment> assignments = asAssignmentRepository.findByAsRequest_Id(asRequest.getId());
        AsAssignment myAssignment = assignments.stream()
                .filter(a -> a.getEngineer().getId().equals(engineerId)
                        && ("ACCEPTED".equals(a.getStatus()) || "COMPLETED".equals(a.getStatus())))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("본인에게 배정된 A/S 건만 보고서를 작성할 수 있습니다."));

        if (workReportRepository.existsByAsRequest_Id(asRequest.getId())) {
            throw new IllegalStateException("해당 A/S 건에 대해 이미 제출된 보고서가 존재합니다.");
        }

        String oldStatusStr = asRequest.getStatus().name();

        asRequest.completeWork();
        // 배정도 함께 완료 처리 — 결제 단계(PaymentService)에서 정산 대상 배정을
        // status='COMPLETED' 기준으로 조회하므로, 여기서 전이해두지 않으면
        // 결제 시 "완료된 배차 내역 없음" 오류가 발생한다.
        if ("ACCEPTED".equals(myAssignment.getStatus())) {
            myAssignment.complete();
        }

        // 최종 금액은 클라이언트 입력값을 신뢰하지 않고 서버에서 확정한다.
        // final_amount = 부품 합계 + 출장비 (결제/정산의 단일 기준값이므로 정합성 보장 목적)
        int partsTotal = 0;
        List<WorkReportPart> reportParts = new java.util.ArrayList<>();
        if (request.getParts() != null && !request.getParts().isEmpty()) {
            for (CreateWorkReportRequest.PartDto partDto : request.getParts()) {
                RepairPart repairPart = repairPartRepository.findById(partDto.getRepairPartId())
                        .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 부품입니다."));

                int appliedPrice = partDto.getAppliedUnitPrice() != null ?
                        partDto.getAppliedUnitPrice() : repairPart.getBaseUnitPrice();

                partsTotal += appliedPrice * partDto.getQuantity();

                reportParts.add(WorkReportPart.builder()
                        .repairPart(repairPart)
                        .quantity(partDto.getQuantity())
                        .appliedUnitPrice(appliedPrice)
                        .build());
            }
        }
        int finalAmount = partsTotal + VISIT_FEE;

        WorkReport report = WorkReport.builder()
                .asRequest(asRequest)
                .engineer(engineer)
                .diagnosisResult(DiagnosisResult.valueOf(request.getDiagnosisResult()))
                .workDurationMin(request.getWorkDurationMin())
                .finalAmount(finalAmount)
                .memo(request.getMemo())
                .imageUrls(request.getImageUrls())
                .build();

        for (WorkReportPart reportPart : reportParts) {
            report.addPart(reportPart);
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

        // submitWorkReport에서 COMPLETED로 전이했던 배정을 다시 ACCEPTED로 되돌려
        // 재제출 시 정상적으로 다시 완료 처리될 수 있도록 한다.
        asAssignmentRepository.findByAsRequest_Id(request.getId()).stream()
                .filter(a -> a.getEngineer().getId().equals(engineerId)
                        && "COMPLETED".equals(a.getStatus()))
                .findFirst()
                .ifPresent(AsAssignment::revertToAccepted);

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

    // 🌟 수정: 메서드 선언부에 throws IllegalAccessException 명시!
    @Transactional
    public void updateWorkReport(Long engineerId, Long reportId, CreateWorkReportRequest request) throws IllegalAccessException {
        WorkReport report = workReportRepository.findById(reportId)
                .orElseThrow(() -> new NoSuchElementException("존재하지 않는 보고서입니다.")); // 404

        if (!report.getEngineer().getId().equals(engineerId)) {
            throw new IllegalAccessException("본인이 작성한 보고서만 수정할 수 있습니다."); // 403 (권한 없음)
        }

        if (report.isCustomerApproved()) {
            throw new IllegalStateException("고객이 이미 승인한 보고서는 수정할 수 없습니다."); // 403
        }

        // 부품 먼저 해석하여 합계 계산 → final_amount = 부품 합계 + 출장비 (서버 확정, 클라이언트 값 무시)
        int partsTotal = 0;
        List<WorkReportPart> newParts = new java.util.ArrayList<>();
        if (request.getParts() != null && !request.getParts().isEmpty()) {
            for (CreateWorkReportRequest.PartDto partDto : request.getParts()) {
                RepairPart repairPart = repairPartRepository.findById(partDto.getRepairPartId()).orElseThrow();
                int appliedPrice = partDto.getAppliedUnitPrice() != null ? partDto.getAppliedUnitPrice() : repairPart.getBaseUnitPrice();
                partsTotal += appliedPrice * partDto.getQuantity();
                newParts.add(WorkReportPart.builder()
                        .repairPart(repairPart).quantity(partDto.getQuantity()).appliedUnitPrice(appliedPrice).build());
            }
        }
        int finalAmount = partsTotal + VISIT_FEE;

        // 1. 기본 정보 갱신 (최종 금액은 서버 계산값 사용)
        report.updateReport(
                DiagnosisResult.valueOf(request.getDiagnosisResult()),
                request.getWorkDurationMin(),
                finalAmount,
                request.getMemo(),
                request.getImageUrls()
        );

        // 2. 부품 리스트 갈아끼우기
        report.clearParts();
        for (WorkReportPart p : newParts) {
            report.addPart(p);
        }

        workReportRepository.flush();
        // 3. 진단서 재계산 연동
        syncHealthCertificate(report.getAsRequest().getAppliance());
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

        String minGrade = "B";
        int minScore = 75;
        try {
            String json = redisTemplate.opsForValue().get("admin:badge:criteria");
            if (json != null) {
                BadgeCriteriaDto dto =
                        objectMapper.readValue(json, BadgeCriteriaDto.class);
                minGrade = dto.minGrade();
                minScore = dto.minScore();
            }
        } catch (Exception e) {
            log.error("Redis에서 인증 뱃지 기준을 읽어오지 못했습니다. 기본값을 사용합니다.", e);
        }

        certificate.recalculate(totalRepairCount, totalCriticalParts, worstImportance, lastRepaired, appliance.getPurchaseDate(), minGrade, minScore);
    }

    // src/main/java/com/careflow/report/service/WorkReportService.java 맨 아래에 추가

    /**
     * 🌟 신규: 보고서 신규 작성용 폼 초기 데이터 세팅
     */
    @Transactional(readOnly = true)
    public WorkReportDetailResponse getReportFormData(Long engineerId, Long requestId) throws IllegalAccessException {

        // 1. 해당 A/S 요청 조회
        AsRequest asRequest = asRequestRepository.findById(requestId)
                .orElseThrow(() -> new NoSuchElementException("존재하지 않는 A/S 요청입니다."));

        // 2. 기사 본인에게 배정된 건인지 권한 검증
        boolean isMyTask = asAssignmentRepository.findByAsRequest_Id(requestId).stream()
                .anyMatch(a -> a.getEngineer().getId().equals(engineerId));
        if (!isMyTask) {
            throw new IllegalAccessException("본인에게 배정된 작업에 대해서만 보고서를 작성할 수 있습니다.");
        }

        // 3. 이미 제출된 보고서가 있는지 확인
        if (workReportRepository.existsByAsRequest_Id(requestId)) {
            throw new IllegalStateException("이미 제출된 보고서가 있습니다. 수정 기능을 이용해 주세요.");
        }

        // 4. 화면 전시용 접수번호 포맷팅
        String dateStr = asRequest.getCreatedAt().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        String formattedRequestCode = String.format("AS-%s-%04d", dateStr, asRequest.getId());

        // 5. 프론트엔드가 렌더링하기 편하도록, 아직 빈 값이지만 DTO 규격에 맞춰서 반환
        return WorkReportDetailResponse.builder()
                .reportId(null) // 신규이므로 null
                .requestId(asRequest.getId())
                .requestCode(formattedRequestCode)
                .engineerName(null)
                .diagnosisResult(null)
                .workDurationMin(null)
                .finalAmount(null)
                .memo("")
                .imageUrls(null)
                .customerApproved(false)
                .modelNo(asRequest.getAppliance().getModelName())
                .serialNo(asRequest.getAppliance().getSerialNumber())
                .customerPhone(asRequest.getCustomer().getPhone())
                // 주소 조립
                .customerAddress((asRequest.getCustomer().getRegionId() != null
                        ? asRequest.getCustomer().getRegionId().getName() + " " : "")
                        + (asRequest.getCustomer().getAddressDetail() != null
                        ? asRequest.getCustomer().getAddressDetail() : ""))
                .parts(List.of()) // 부품 없음
                .build();
    }
}