package com.careflow.appliance.controller;

import com.careflow.appliance.dto.ApplianceCreateRequest;
import com.careflow.appliance.dto.ApplianceResponse;
import com.careflow.appliance.service.ApplianceService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/appliances")
@RequiredArgsConstructor
public class ApplianceController {

    private final ApplianceService applianceService;

    /**
     * 가전제품 등록 API
     * TODO: @RequestParam Long userId → 추후 @AuthenticationPrincipal로 대체 예정
     */
    @PostMapping
    public ResponseEntity<ApplianceResponse> registerAppliance(
            @RequestParam Long userId,
            @Valid @RequestBody ApplianceCreateRequest request) {

        ApplianceResponse response = applianceService.registerAppliance(userId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * 내 가전제품 목록 조회 API (논리 삭제 제외)
     * TODO: @RequestParam Long userId → 추후 @AuthenticationPrincipal로 대체 예정
     */
    @GetMapping("/me")
    public ResponseEntity<List<ApplianceResponse>> getMyAppliances(@RequestParam Long userId) {
        return ResponseEntity.ok(applianceService.getMyAppliances(userId));
    }

    /**
     * 가전제품 상세 조회 API
     * TODO: @RequestParam Long userId → 추후 @AuthenticationPrincipal로 대체 예정
     */
    @GetMapping("/{applianceId}")
    public ResponseEntity<ApplianceResponse> getApplianceDetail(
            @RequestParam Long userId,
            @PathVariable Long applianceId) {

        return ResponseEntity.ok(applianceService.getApplianceDetail(userId, applianceId));
    }

    /**
     * 가전제품 논리 삭제 API
     * TODO: @RequestParam Long userId → 추후 @AuthenticationPrincipal로 대체 예정
     */
    @DeleteMapping("/{applianceId}")
    public ResponseEntity<Void> deleteAppliance(
            @RequestParam Long userId,
            @PathVariable Long applianceId) {

        applianceService.deleteAppliance(userId, applianceId);
        return ResponseEntity.noContent().build();
    }
}
