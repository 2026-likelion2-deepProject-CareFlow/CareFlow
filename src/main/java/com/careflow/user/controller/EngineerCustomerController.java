package com.careflow.user.controller;

import com.careflow.auth.security.CustomUserDetails;
import com.careflow.user.dto.EngineerCustomerDetailResponse;
import com.careflow.user.dto.EngineerCustomerListResponse;
import com.careflow.user.service.EngineerCustomerService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/engineer/customers")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ENGINEER')")
public class EngineerCustomerController {

    private final EngineerCustomerService engineerCustomerService;

    @GetMapping
    public ResponseEntity<Page<EngineerCustomerListResponse>> getMyCustomers(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @RequestParam(required = false) String search,     // 🌟 검색어 추가
            @RequestParam(required = false) String status,     // 🌟 상태 추가
            @RequestParam(required = false) Integer regionId,  // 🌟 지역 ID 추가
            @RequestParam(required = false) String brand,      // 🌟 브랜드 추가
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {

        Page<EngineerCustomerListResponse> response =
                engineerCustomerService.getMyCustomersList(
                        userDetails.getUserId(), search, status, regionId, brand, page, size);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{customerId}")
    public ResponseEntity<EngineerCustomerDetailResponse> getCustomerDetail(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable Long customerId) {

        EngineerCustomerDetailResponse response =
                engineerCustomerService.getCustomerDetail(userDetails.getUserId(), customerId);
        return ResponseEntity.ok(response);
    }
}