package com.careflow.report.service;

import com.careflow.appliance.entity.HealthCertificate;
import com.careflow.appliance.repository.HealthCertificateRepository;
import com.careflow.as_request.entity.AsRequest;
import com.careflow.as_request.repository.AsRequestRepository;
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

@Service
@RequiredArgsConstructor
public class WorkReportService {

    private final WorkReportRepository workReportRepository;
    private final RepairPartRepository repairPartRepository;
    private final HealthCertificateRepository healthCertificateRepository;
    private final AsRequestRepository asRequestRepository;
    private final UserRepository userRepository;

    @Transactional
    public Long submitWorkReport(Long engineerId, CreateWorkReportRequest request) {
        User engineer = userRepository.findById(engineerId)
                .orElseThrow(() -> new IllegalArgumentException("기사 정보를 찾을 수 없습니다."));

        AsRequest asRequest = asRequestRepository.findById(request.getRequestId())
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 A/S 신청 건입니다."));

        asRequest.completeWork();

        WorkReport report = WorkReport.builder()
                .asRequest(asRequest)
                .engineer(engineer)
                .diagnosisResult(DiagnosisResult.valueOf(request.getDiagnosisResult()))
                .workDurationMin(request.getWorkDurationMin())
                .finalAmount(request.getFinalAmount())
                .memo(request.getMemo())
                .build();

        boolean isCriticalReplaced = false;
        PartImportance maxImportance = PartImportance.MINOR;

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

                if (repairPart.getImportance().ordinal() < maxImportance.ordinal()) {
                    maxImportance = repairPart.getImportance();
                }
                if (repairPart.getImportance() == PartImportance.CRITICAL) {
                    isCriticalReplaced = true;
                }
            }
        }

        // save() 반환값을 사용해야 DB 생성 ID를 정확히 얻을 수 있음
        WorkReport savedReport = workReportRepository.save(report);

        HealthCertificate certificate = healthCertificateRepository.findByAppliance_Id(asRequest.getAppliance().getId())
                .orElseGet(() -> healthCertificateRepository.save(
                        HealthCertificate.builder()
                                .appliance(asRequest.getAppliance())
                                .build()
                ));

        String newGrade = "A";
        int newScore = 100;

        if (request.getParts() == null || request.getParts().isEmpty()) {
            newGrade = "A";
            newScore = 95;
        } else {
            switch (maxImportance) {
                case CRITICAL -> { newGrade = "E"; newScore = 30; }
                case MAJOR -> { newGrade = "D"; newScore = 50; }
                case NORMAL -> { newGrade = "C"; newScore = 70; }
                case MINOR -> { newGrade = "B"; newScore = 85; }
            }
        }

        certificate.updateHealthGrade(newGrade, newScore, isCriticalReplaced);

        return savedReport.getReportId();
    }
}