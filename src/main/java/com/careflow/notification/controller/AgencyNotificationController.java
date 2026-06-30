package com.careflow.notification.controller;

import com.careflow.auth.security.CustomUserDetails;
import com.careflow.notification.dto.AgencyNotificationResponse;
import com.careflow.notification.service.AgencyNotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/agency/notifications")
@RequiredArgsConstructor
public class AgencyNotificationController {

    private final AgencyNotificationService agencyNotificationService;

    /**
     * [GET] /api/agency/notifications
     * 현재 로그인한 대행사 관리자 소속 알림센터 목록 조회.
     * - 대행사 소속 수리기사, 또는 그 수리기사에게 A/S를 받은 고객에게 발송된 알림을 페이징 조회한다.
     * - type 파라미터(AS_STATUS/CONSUMABLE/WARRANTY/LMS)로 목록만 필터링, stats는 항상 전체 범위 기준
     * - role != AGENCY → 401 Unauthorized
     * - type 값이 허용된 ENUM이 아니면 → 400 Bad Request
     */
    @GetMapping
    public ResponseEntity<AgencyNotificationResponse> getNotifications(
            @PageableDefault(page = 0, size = 10) Pageable pageable,
            @RequestParam(required = false) String type,
            @AuthenticationPrincipal CustomUserDetails userDetails) throws IllegalAccessException {

        AgencyNotificationResponse response = agencyNotificationService.getNotifications(userDetails, pageable, type);
        return ResponseEntity.ok(response);
    }
}
