package com.careflow.admin.controller;

import com.careflow.admin.dto.response.AdminAsRequestListResponse;
import com.careflow.admin.service.AdminAsRequestService;
import com.careflow.auth.security.CustomUserDetails;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 관리자용 A/S 현황 관리 API 컨트롤러
 * 모든 엔드포인트는 ROLE_ADMIN 전제
 */
@RestController
@RequestMapping("/api/admin/as-requests")
@RequiredArgsConstructor
public class AdminAsRequestController {

    private final AdminAsRequestService adminAsRequestService;

    /**
     * [GET] /api/admin/as-requests/stats
     * 실시간 A/S 현황 통계 조회 (대시보드 상단 표출용)
     */
    @GetMapping("/stats")
    public ResponseEntity<Map<String, Long>> getRealTimeStats(
            @AuthenticationPrincipal CustomUserDetails userDetails) throws IllegalAccessException {

        checkAdminRole(userDetails);
        Map<String, Long> stats = adminAsRequestService.getRealTimeStats();
        return ResponseEntity.ok(stats);
    }

    /**
     * [GET] /api/admin/as-requests
     * 전체 A/S 처리 내역 조회 (페이징 + 다중 동적 필터)
     */
    @GetMapping
    public ResponseEntity<AdminAsRequestListResponse> getAsRequests(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String region,
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to,
            @PageableDefault(page = 0, size = 10) Pageable pageable,
            @AuthenticationPrincipal CustomUserDetails userDetails) throws IllegalAccessException {

        checkAdminRole(userDetails);
        AdminAsRequestListResponse response = adminAsRequestService.searchAsRequests(
                status, region, from, to, pageable);

        return ResponseEntity.ok(response);
    }

    // ─────────────────────────────────────────────
    // 공통: ADMIN 역할 검증 (SecurityConfig와 이중 방어)
    // ─────────────────────────────────────────────
    private void checkAdminRole(CustomUserDetails userDetails) throws IllegalAccessException {
        if (!"ADMIN".equals(userDetails.getRole())) {
            throw new IllegalAccessException("관리자만 접근할 수 있습니다.");
        }
    }
}