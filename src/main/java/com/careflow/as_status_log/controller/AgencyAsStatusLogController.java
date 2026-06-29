package com.careflow.as_status_log.controller;

import com.careflow.as_status_log.dto.AsStatusLogListResponse;
import com.careflow.as_status_log.service.AgencyAsStatusLogService;
import com.careflow.auth.security.CustomUserDetails;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/as-status-logs")
@RequiredArgsConstructor
public class AgencyAsStatusLogController {

    private final AgencyAsStatusLogService agencyAsStatusLogService;

    /**
     * 소속 대행사 A/S 상태 변경 이력 목록 조회 (최신순)
     * - as_status_logs JOIN as_requests WHERE agency_id = 소속 대행사 id
     */
    @GetMapping("/agency")
    public ResponseEntity<AsStatusLogListResponse> getStatusLogs(
            @AuthenticationPrincipal CustomUserDetails userDetails) throws IllegalAccessException {

        AsStatusLogListResponse response = agencyAsStatusLogService.getStatusLogs(userDetails);
        return ResponseEntity.ok(response);
    }

}
