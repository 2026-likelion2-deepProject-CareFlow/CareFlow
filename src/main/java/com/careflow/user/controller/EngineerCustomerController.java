// 파일 경로: src/main/java/com/careflow/user/controller/EngineerCustomerController.java
package com.careflow.user.controller;

import com.careflow.assignment.repository.AsAssignmentRepository;
import com.careflow.auth.security.CustomUserDetails;
import com.careflow.user.entity.User;
import lombok.Builder;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/engineer/customers")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ENGINEER')")
public class EngineerCustomerController {

    private final AsAssignmentRepository asAssignmentRepository;

    @GetMapping
    @Transactional(readOnly = true)
    public ResponseEntity<Page<EngineerCustomerResponse>> getMyCustomers(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {

        PageRequest pageRequest = PageRequest.of(Math.max(0, page), size);
        Page<User> customers = asAssignmentRepository.findCustomersByEngineerId(userDetails.getUserId(), pageRequest);

        Page<EngineerCustomerResponse> response = customers.map(c -> {
            String regionName = c.getRegionId() != null ? c.getRegionId().getName() : "미등록";
            return EngineerCustomerResponse.builder()
                    .customerId(c.getId())
                    .name(c.getName())
                    .phone(c.getPhone())
                    .region(regionName)
                    .status(c.getStatus())
                    .build();
        });

        return ResponseEntity.ok(response);
    }

    @Getter
    @Builder
    public static class EngineerCustomerResponse {
        private Long customerId;
        private String name;
        private String phone;
        private String region;
        private String status;
    }
}