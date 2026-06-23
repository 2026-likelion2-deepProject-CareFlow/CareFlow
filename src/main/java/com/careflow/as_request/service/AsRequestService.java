package com.careflow.as_request.service;

import com.careflow.as_request.dto.AsRequestCreateDto;
import com.careflow.as_request.dto.AsRequestResponseDto;
import com.careflow.as_request.entity.AsRequest;
import com.careflow.as_request.repository.AsRequestRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AsRequestService {

    private final AsRequestRepository asRequestRepository;

    @Transactional
    public Long createAsRequest(Long customerId, AsRequestCreateDto dto) {
        AsRequest asRequest = AsRequest.builder()
                .customerId(customerId)
                .applianceId(dto.getApplianceId())
                .symptomCode(dto.getSymptomCode())
                .symptomDesc(dto.getSymptomDesc())
                .imageUrls(dto.getImageUrls())
                .visitRegionId(dto.getVisitRegionId())
                .visitAddressDetail(dto.getVisitAddressDetail())
                .scheduledDate(dto.getScheduledDate())
                .scheduledTime(dto.getScheduledTime())
                .preferredEngineerId(dto.getPreferredEngineerId())
                .build();

        return asRequestRepository.save(asRequest).getId();
    }

    public List<AsRequestResponseDto> getMyAsRequests(Long customerId) {
        return asRequestRepository.findByCustomerIdOrderByIdDesc(customerId)
                .stream()
                .map(AsRequestResponseDto::new)
                .collect(Collectors.toList());
    }

    @Transactional
    public void cancelAsRequest(Long customerId, Long asRequestId, String cancelReason) {
        AsRequest asRequest = asRequestRepository.findById(asRequestId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 A/S 요청입니다."));

        if (!asRequest.getCustomerId().equals(customerId)) {
            throw new SecurityException("본인의 A/S 요청만 취소할 수 있습니다.");
        }

        asRequest.cancel(cancelReason);
    }
}