package com.careflow.user.controller;

import com.careflow.as_request.dto.AsRequestResponseDto;
import com.careflow.as_request.service.AsRequestService;
import com.careflow.auth.security.CustomUserDetails;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 고객(CUSTOMER) 전용 컨트롤러 — /api/customers
 *
 * 프론트엔드 URI 규약(/api/customers/{customerId}/*)에 맞춰 고객 관련 API를 제공한다.
 * customerId 경로 파라미터는 URL 구조 일치를 위해 선언하며, 실제 인증은 JWT로 처리한다.
 */
@RestController
@RequestMapping("/api/customers/{customerId}")
@RequiredArgsConstructor
public class CustomerController {

    private final AsRequestService asRequestService;

    /**
     * 고객 본인의 A/S 접수 목록 조회
     * GET /api/customers/{customerId}/as-requests
     */
    @GetMapping("/as-requests")
    public ResponseEntity<List<AsRequestResponseDto>> getMyAsRequests(
            @PathVariable Long customerId,
            @AuthenticationPrincipal CustomUserDetails userDetails) {

        List<AsRequestResponseDto> myRequests =
                asRequestService.getMyAsRequests(userDetails.getUserId());
        return ResponseEntity.ok(myRequests);
    }
}
