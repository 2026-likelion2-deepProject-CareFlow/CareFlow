package com.careflow.user.service;

import com.careflow.appliance.entity.Appliance;
import com.careflow.appliance.repository.ApplianceRepository;
import com.careflow.as_request.repository.AsRequestRepository;
import com.careflow.assignment.repository.AsAssignmentRepository;
import com.careflow.report.domain.entity.WorkReport;
import com.careflow.report.repository.WorkReportRepository;
// 🌟 수정: 분리된 DTO 경로로 정확하게 Import
import com.careflow.user.dto.EngineerCustomerDetailResponse;
import com.careflow.user.dto.EngineerCustomerDetailResponse.ApplianceDto;
import com.careflow.user.dto.EngineerCustomerDetailResponse.AsHistoryDto;
import com.careflow.user.dto.EngineerCustomerListResponse;
import com.careflow.user.entity.User;
import com.careflow.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class EngineerCustomerService {

    private final UserRepository userRepository;
    private final WorkReportRepository workReportRepository;
    private final AsAssignmentRepository asAssignmentRepository;
    private final ApplianceRepository applianceRepository;
    private final AsRequestRepository asRequestRepository;

    /**
     * 1. 내 고객 목록 페이징 조회 (가전 개수, A/S 건수, 최근 작업일 N+1 방지 집계 포함)
     */
    public Page<EngineerCustomerListResponse> getMyCustomersList(Long engineerId, int page, int size) {
        PageRequest pageRequest = PageRequest.of(page, size);
        Page<User> customers = asAssignmentRepository.findCustomersByEngineerId(engineerId, pageRequest);

        if (customers.isEmpty()) return Page.empty();

        List<Long> customerIds = customers.getContent().stream().map(User::getId).toList();

        // 1) 가전 개수 맵
        Map<Long, Long> applianceCountMap = applianceRepository.countActiveByUserIds(customerIds).stream()
                .collect(Collectors.toMap(r -> (Long) r[0], r -> (Long) r[1]));

        // 2) A/S 건수 맵
        Map<Long, Long> asCountMap = asRequestRepository.countAsRequestsByCustomerIds(customerIds).stream()
                .collect(Collectors.toMap(r -> (Long) r[0], r -> (Long) r[1]));

        // 3) 최근 작업일 맵
        Map<Long, LocalDateTime> lastWorkMap = asRequestRepository.findLatestAsDateByCustomerIds(customerIds).stream()
                .collect(Collectors.toMap(r -> (Long) r[0], r -> (LocalDateTime) r[1]));

        return customers.map(c -> {
            LocalDateTime lastDate = lastWorkMap.get(c.getId());
            return EngineerCustomerListResponse.builder()
                    .customerId(c.getId())
                    .name(c.getName())
                    .phone(c.getPhone())
                    .region(c.getRegionId() != null ? c.getRegionId().getName() : "미등록")
                    .status(c.getStatus())
                    .appliancesCount(applianceCountMap.getOrDefault(c.getId(), 0L).intValue())
                    .totalAsCount(asCountMap.getOrDefault(c.getId(), 0L).intValue())
                    .lastWorkDate(lastDate != null ? lastDate.format(DateTimeFormatter.ofPattern("yyyy-MM-dd")) : "-")
                    .build();
        });
    }

    /**
     * 2. 고객 단건 상세 조회 (프론트 요청 필드: email, joinedAt, appliances, inProgressRequestId 포함)
     */
    public EngineerCustomerDetailResponse getCustomerDetail(Long engineerId, Long customerId) {
        // 1. 고객 정보 조회
        User customer = userRepository.findById(customerId)
                .orElseThrow(() -> new IllegalArgumentException("해당 고객을 찾을 수 없습니다."));

        // 2. 고객의 활성 가전 목록 조회
        List<Appliance> applianceList = applianceRepository.findByUser_IdAndDeletedAtIsNullOrderByCreatedAtDesc(customerId);
        List<ApplianceDto> applianceDtos = applianceList.stream()
                .map(a -> ApplianceDto.builder()
                        .applianceId(a.getId())
                        .brand(a.getBrand())
                        .modelName(a.getModelName())
                        .categoryName(a.getCategory().getName())
                        .build()).toList();

        // 3. 해당 기사가 이 고객에게 처리한 완료 이력 조회
        List<WorkReport> reports = workReportRepository.findHistoryByEngineerAndCustomer(engineerId, customerId);
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

        // 4. 현재 기사가 해당 고객에게 진행 중인(WAITING/ACCEPTED ~ IN_PROGRESS) 최신 A/S 건 확인
        Long inProgressId = asAssignmentRepository.findActiveByEngineerId(engineerId).stream()
                .filter(a -> a.getAsRequest().getCustomer().getId().equals(customerId))
                .findFirst()
                .map(a -> a.getAsRequest().getId())
                .orElse(null);

        // 5. DTO 조립
        String regionName = customer.getRegionId() != null ? customer.getRegionId().getName() : "지역 미상";

        return EngineerCustomerDetailResponse.builder()
                .customerId(customer.getId())
                .email(customer.getEmail()) // 🌟 프론트 요청 추가
                .name(customer.getName())
                .phone(customer.getPhone())
                .region(regionName)
                .addressDetail(customer.getAddressDetail() != null ? customer.getAddressDetail() : "")
                .joinedAt(customer.getCreatedAt().format(DateTimeFormatter.ofPattern("yyyy-MM-dd"))) // 🌟 프론트 요청 추가
                .appliances(applianceDtos) // 🌟 프론트 요청 추가
                .inProgressRequestId(inProgressId) // 🌟 프론트 요청 추가
                .asHistory(historyList)
                .build();
    }
}