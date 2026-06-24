package com.careflow.as_request.controller;

import com.careflow.as_request.dto.AsRequestCreateDto;
import com.careflow.as_request.dto.AsRequestCreateResponseDto;
import com.careflow.as_request.dto.AsRequestResponseDto;
import com.careflow.as_request.service.AsRequestService;
import com.careflow.auth.security.CustomUserDetails;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/as-requests")
@RequiredArgsConstructor
public class AsRequestController {

    private final AsRequestService asRequestService;

    /**
     * 고객용: A/S 접수 및 수리 기사 배정 API
     * - assignType = AUTO  : 조건 기반 자동 배정 (4단계 fallback 포함)
     * - assignType = MANUAL: 고객이 직접 지정한 기사(preferredEngineerId)에게 배정
     */
    @PostMapping
    public ResponseEntity<AsRequestCreateResponseDto> createAsRequest(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @Valid @RequestBody AsRequestCreateDto dto) {

        AsRequestCreateResponseDto response =
                asRequestService.createAsRequest(userDetails.getUserId(), dto);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * 고객용: 본인의 A/S 목록 조회 API
     */
    @GetMapping("/me")
    public ResponseEntity<List<AsRequestResponseDto>> getMyAsRequests(
            @AuthenticationPrincipal CustomUserDetails userDetails) {

        List<AsRequestResponseDto> myRequests =
                asRequestService.getMyAsRequests(userDetails.getUserId());
        return ResponseEntity.ok(myRequests);
    }

    /**
     * 고객용: A/S 취소 API
     * PENDING 또는 AGENCY_RECEIVED 상태일 때만 취소 가능
     */
    @PatchMapping("/{asRequestId}/cancel")
    public ResponseEntity<Void> cancelAsRequest(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable Long asRequestId,
            @RequestParam(required = false) String cancelReason) {

        asRequestService.cancelAsRequest(userDetails.getUserId(), asRequestId, cancelReason);
        return ResponseEntity.ok().build();
    }
}