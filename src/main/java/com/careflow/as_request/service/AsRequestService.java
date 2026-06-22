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

    /**
     * A/S 신청 등록
     */
    @Transactional
    public Long createAsRequest(Long customerId, AsRequestCreateDto dto) {
        AsRequest asRequest = AsRequest.builder()
                .customerId(customerId)
                .applianceId(dto.getApplianceId())
                .title(dto.getTitle())
                .description(dto.getDescription())
                .build();

        return asRequestRepository.save(asRequest).getId();
    }

    /**
     * 본인의 A/S 신청 내역 전체 조회
     */
    public List<AsRequestResponseDto> getMyAsRequests(Long customerId) {
        return asRequestRepository.findByCustomerIdOrderByIdDesc(customerId)
                .stream()
                .map(AsRequestResponseDto::new)
                .collect(Collectors.toList());
    }

    /**
     * A/S 신청 취소
     */
    @Transactional
    public void cancelAsRequest(Long customerId, Long asRequestId) {
        AsRequest asRequest = asRequestRepository.findById(asRequestId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 A/S 요청 항목입니다."));

        // 본인 검증 권한 체크
        if (!asRequest.getCustomerId().equals(customerId)) {
            throw new SecurityException("본인의 A/S 요청 것만 취소할 수 있습니다.");
        }

        // 엔티티 내부 상태 변경 로직 수행
        asRequest.cancel();
    }
}