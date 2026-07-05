package com.careflow.notification.controller;

import com.careflow.auth.security.CustomUserDetails;
import com.careflow.notification.dto.NotificationResponse;
import com.careflow.notification.service.NotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/engineer/notifications")
@RequiredArgsConstructor
public class EngineerNotificationController {

    private final NotificationService notificationService;

    /**
     * 기사 본인의 알림 목록 조회 (페이징)
     * GET /api/engineer/notifications?page=0&size=10
     */
    @GetMapping
    public ResponseEntity<Page<NotificationResponse>> getNotifications(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @RequestParam(required = false) String type, // 🌟 type 파라미터 추가
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {

        PageRequest pageRequest = PageRequest.of(Math.max(0, page), size);
        Page<NotificationResponse> response = notificationService.getNotifications(userDetails.getUserId(), type, pageRequest);

        return ResponseEntity.ok(response);
    }

    /**
     * 기사 본인의 알림 유형별 통계 요약 조회
     * GET /api/engineer/notifications/summary
     */
    @GetMapping("/summary")
    public ResponseEntity<Map<String, Long>> getNotificationSummary(
            @AuthenticationPrincipal CustomUserDetails userDetails) {

        Map<String, Long> summary = notificationService.getNotificationSummary(userDetails.getUserId());
        return ResponseEntity.ok(summary);
    }

    /**
     * 기사 본인의 알림 단건 읽음 처리
     * PATCH /api/engineer/notifications/{notificationId}/read
     */
    @PatchMapping("/{notificationId}/read")
    public ResponseEntity<Void> markAsRead(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable Long notificationId) {

        notificationService.markAsRead(userDetails.getUserId(), notificationId);
        return ResponseEntity.noContent().build();
    }

    /**
     * 기사 본인의 알림 전체 일괄 읽음 처리
     * PATCH /api/engineer/notifications/read-all
     */
    @PatchMapping("/read-all")
    public ResponseEntity<Void> markAllAsRead(
            @AuthenticationPrincipal CustomUserDetails userDetails) {

        notificationService.markAllAsRead(userDetails.getUserId());
        return ResponseEntity.noContent().build();
    }
}