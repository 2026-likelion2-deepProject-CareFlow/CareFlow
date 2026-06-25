package com.careflow.report.service;

import com.careflow.appliance.entity.HealthCertificate;
import com.careflow.appliance.repository.HealthCertificateRepository;
import com.careflow.as_request.entity.AsRequest;
import com.careflow.as_request.repository.AsRequestRepository;
import com.careflow.assignment.entity.AsAssignment;
import com.careflow.assignment.repository.AsAssignmentRepository;
import com.careflow.engineer.dto.CreateWorkReportRequest;
import com.careflow.part.domain.entity.RepairPart;
import com.careflow.part.repository.RepairPartRepository;
import com.careflow.report.domain.entity.WorkReport;
import com.careflow.report.domain.entity.WorkReportPart;
import com.careflow.report.domain.enums.DiagnosisResult;
import com.careflow.report.domain.enums.PartImportance;
import com.careflow.report.repository.WorkReportRepository;
import com.careflow.user.entity.User;
import com.careflow.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class WorkReportService {

    private final WorkReportRepository workReportRepository;
    private final RepairPartRepository repairPartRepository;
    private final HealthCertificateRepository healthCertificateRepository;
    private final AsRequestRepository asRequestRepository;
    private final UserRepository userRepository;
    private final AsAssignmentRepository asAssignmentRepository;

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

        PartImportance maxImportance = null;

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

                if (maxImportance == null || repairPart.getImportance().getSeverity() < maxImportance.getSeverity()) {
                    maxImportance = repairPart.getImportance();
                }

            }
        }

        WorkReport savedReport = workReportRepository.save(report);
        HealthCertificate certificate = healthCertificateRepository.findByAppliance_Id(asRequest.getAppliance().getId())
                .orElseGet(() -> healthCertificateRepository.save(
                        HealthCertificate.builder()
                                .appliance(asRequest.getAppliance())
                                .build()
                ));

        certificate.calculateAndUpdateHealth(maxImportance, asRequest.getAppliance().getPurchaseDate());

        return savedReport.getReportId();
    }
}