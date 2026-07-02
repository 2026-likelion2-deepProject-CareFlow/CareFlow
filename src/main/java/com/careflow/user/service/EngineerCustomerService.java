package com.careflow.user.service;

import com.careflow.report.domain.entity.WorkReport;
import com.careflow.report.repository.WorkReportRepository;
import com.careflow.user.controller.EngineerCustomerController.EngineerCustomerDetailResponse;
import com.careflow.user.controller.EngineerCustomerController.EngineerCustomerDetailResponse.AsHistoryDto;
import com.careflow.user.entity.User;
import com.careflow.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.format.DateTimeFormatter;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class EngineerCustomerService {

    private final UserRepository userRepository;
    private final WorkReportRepository workReportRepository;

    public EngineerCustomerDetailResponse getCustomerDetail(Long engineerId, Long customerId) {
        // 1. 고객 정보 조회
        User customer = userRepository.findById(customerId)
                .orElseThrow(() -> new IllegalArgumentException("해당 고객을 찾을 수 없습니다."));

        // 2. 해당 기사가 이 고객에게 처리한 A/S 이력 조회
        List<WorkReport> reports = workReportRepository.findHistoryByEngineerAndCustomer(engineerId, customerId);

        // 3. DTO 매핑
        List<AsHistoryDto> historyList = reports.stream().map(r -> {
            String dateStr = r.getAsRequest().getCreatedAt().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
            String reqIdFormatted = String.format("AS-%s-%04d", dateStr, r.getAsRequest().getId());
            String workDate = r.getSubmittedAt().format(DateTimeFormatter.ofPattern("yyyy.MM.dd"));
            String productName = r.getAsRequest().getAppliance().getBrand() + " " + r.getAsRequest().getAppliance().getModelName();

            return AsHistoryDto.builder()
                    .reportId(r.getReportId())
                    .requestId(reqIdFormatted)
                    .workDate(workDate)
                    .productName(productName)
                    .symptom(r.getAsRequest().getSymptom().getSymptomName())
                    .diagnosisResult(r.getDiagnosisResult().name())
                    .finalAmount(r.getFinalAmount())
                    .build();
        }).toList();

        String regionName = customer.getRegionId() != null ? customer.getRegionId().getName() : "지역 미상";

        return EngineerCustomerDetailResponse.builder()
                .customerId(customer.getId())
                .name(customer.getName())
                .phone(customer.getPhone())
                .region(regionName)
                .addressDetail(customer.getAddressDetail() != null ? customer.getAddressDetail() : "")
                .asHistory(historyList)
                .build();
    }
}