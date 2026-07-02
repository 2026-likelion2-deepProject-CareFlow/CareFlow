package com.careflow.user.controller;

import com.careflow.assignment.repository.AsAssignmentRepository;
import com.careflow.auth.security.CustomUserDetails;
import com.careflow.user.entity.User;
import com.careflow.user.service.EngineerCustomerService;
import lombok.Builder;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/engineer/customers")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ENGINEER')") // 🌟 조장님 공지 반영! (hasRole)
public class EngineerCustomerController {

    private final AsAssignmentRepository asAssignmentRepository;
    private final EngineerCustomerService engineerCustomerService;

    // 1. 고객 목록 조회
    @GetMapping
    @Transactional(readOnly = true)
    public ResponseEntity<Page<EngineerCustomerListResponse>> getMyCustomers(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {

        PageRequest pageRequest = PageRequest.of(Math.max(0, page), size);
        Page<User> customers = asAssignmentRepository.findCustomersByEngineerId(userDetails.getUserId(), pageRequest);

        Page<EngineerCustomerListResponse> response = customers.map(c -> {
            String regionName = c.getRegionId() != null ? c.getRegionId().getName() : "미등록";
            return EngineerCustomerListResponse.builder()
                    .customerId(c.getId())
                    .name(c.getName())
                    .phone(c.getPhone())
                    .region(regionName)
                    .status(c.getStatus())
                    .build();
        });

        return ResponseEntity.ok(response);
    }

    // 🌟 2. 신규 추가: 고객 상세 및 A/S 이력 조회
    @GetMapping("/{customerId}")
    public ResponseEntity<EngineerCustomerDetailResponse> getCustomerDetail(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable Long customerId) {

        EngineerCustomerDetailResponse response = engineerCustomerService.getCustomerDetail(userDetails.getUserId(), customerId);
        return ResponseEntity.ok(response);
    }

    // --- DTO Classes ---

    @Getter @Builder
    public static class EngineerCustomerListResponse {
        private Long customerId;
        private String name;
        private String phone;
        private String region;
        private String status;
    }

    @Getter @Builder
    public static class EngineerCustomerDetailResponse {
        private Long customerId;
        private String name;
        private String phone;
        private String region;
        private String addressDetail;
        private List<AsHistoryDto> asHistory;

        @Getter @Builder
        public static class AsHistoryDto {
            private Long reportId;
            private String requestId;
            private String workDate;
            private String productName;
            private String symptom;
            private String diagnosisResult;
            private Integer finalAmount;
        }
    }
}