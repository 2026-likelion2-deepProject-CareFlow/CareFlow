package com.careflow.as_status_log.controller;

import com.careflow.as_status_log.dto.AsStatusLogListResponse;
import com.careflow.as_status_log.dto.AsStatusLogSummaryResponse;
import com.careflow.as_status_log.service.AgencyAsStatusLogService;
import com.careflow.auth.security.CustomUserDetails;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/as-status-logs")
@RequiredArgsConstructor
public class AgencyAsStatusLogController {

    private final AgencyAsStatusLogService agencyAsStatusLogService;

    /**
     * 소속 대행사 A/S 상태 변경 이력 목록 조회 (최신순)
     * - as_status_logs JOIN as_requests WHERE agency_id = 소속 대행사 id
     * - date·toStatus·engineerId·keyword 모두 선택 파라미터(null/미입력 시 조건 미적용)
     */
    @GetMapping("/agency")
    public ResponseEntity<AsStatusLogListResponse> getStatusLogs(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(required = false) String toStatus,
            @RequestParam(required = false) Long engineerId,
            @RequestParam(required = false) String keyword) throws IllegalAccessException {

        AsStatusLogListResponse response =
                agencyAsStatusLogService.getStatusLogs(userDetails, date, toStatus, engineerId, keyword);
        return ResponseEntity.ok(response);
    }

    /**
     * 소속 대행사 A/S to_status 기준 상태별 집계
     * - 집계 대상: WAITING / ENGINEER_DEPARTED / ENGINEER_ARRIVED / IN_PROGRESS / COMPLETED
     */
    @GetMapping("/agency/status-summary")
    public ResponseEntity<AsStatusLogSummaryResponse> getStatusSummary(
            @AuthenticationPrincipal CustomUserDetails userDetails) throws IllegalAccessException {

        AsStatusLogSummaryResponse response = agencyAsStatusLogService.getStatusSummary(userDetails);
        return ResponseEntity.ok(response);
    }

    /**
     * 고객용: 본인 A/S 요청의 상태 변경 이력 조회 (시간 오름차순)
     * - 본인 요청이 아닌 경우 401 반환
     * - 존재하지 않는 requestId → 404 반환
     */
    @GetMapping("/{requestId}")
    public ResponseEntity<AsStatusLogListResponse> getCustomerStatusLogs(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable Long requestId) throws IllegalAccessException {

        AsStatusLogListResponse response =
                agencyAsStatusLogService.getCustomerStatusLogs(userDetails.getUserId(), requestId);
        return ResponseEntity.ok(response);
    }
}
