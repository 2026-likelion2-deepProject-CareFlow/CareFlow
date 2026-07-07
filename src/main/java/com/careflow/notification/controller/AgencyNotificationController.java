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
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
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
     * - 대행사 소속 수리기사, 그 수리기사에게 A/S를 받은 고객, 그리고 로그인한 본인 계정에게
     *   발송된 알림(예: 로그인 알림)을 페이징 조회한다.
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

    /**
     * [PATCH] /api/agency/notifications/{notificationId}/read
     * 알림 클릭 시 해당 알림을 읽음 처리한다.
     * - unreadCount는 이 API가 내려주지 않음 — 프론트엔드는 처리 후 GET /api/agency/notifications를 재호출해 반영한다.
     * - role != AGENCY, 또는 본인 대행사 수신 범위 밖의 알림 → 401 Unauthorized
     * - 존재하지 않는 notificationId → 404 Not Found
     */
    @PatchMapping("/{notificationId}/read")
    public ResponseEntity<Void> markAsRead(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable Long notificationId) throws IllegalAccessException {

        agencyNotificationService.markAsRead(userDetails, notificationId);
        return ResponseEntity.noContent().build();
    }

    /**
     * [PATCH] /api/agency/notifications/read-all
     * "모두 읽음 처리" 버튼 클릭 시 이 대행사의 알림 수신 대상 범위 전체를 일괄 읽음 처리한다.
     * - 결과가 결정적(전체 읽음 → unreadCount=0)이므로 프론트엔드는 GET 재호출 없이 로컬 상태를 즉시 갱신한다.
     * - role != AGENCY → 401 Unauthorized
     */
    @PatchMapping("/read-all")
    public ResponseEntity<Void> markAllAsRead(
            @AuthenticationPrincipal CustomUserDetails userDetails) throws IllegalAccessException {

        agencyNotificationService.markAllAsRead(userDetails);
        return ResponseEntity.noContent().build();
    }
}
