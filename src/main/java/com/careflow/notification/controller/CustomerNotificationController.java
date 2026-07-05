package com.careflow.notification.controller;

import com.careflow.auth.security.CustomUserDetails;
import com.careflow.notification.dto.NotificationCategoryStat;
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
@RequestMapping("/api/customer/notifications")
@RequiredArgsConstructor
public class CustomerNotificationController {

    private final NotificationService notificationService;

    /**
     * 고객 본인의 알림 목록 조회 (페이징 + 타입 필터 + "안읽음만 보기" 필터)
     * GET /api/customer/notifications?type=&unreadOnly=&page=0&size=10
     */
    @GetMapping
    public ResponseEntity<Page<NotificationResponse>> getNotifications(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @RequestParam(required = false) String type,
            @RequestParam(defaultValue = "false") boolean unreadOnly,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {

        PageRequest pageRequest = PageRequest.of(Math.max(0, page), size);
        Page<NotificationResponse> response =
                notificationService.getNotifications(userDetails.getUserId(), type, unreadOnly, pageRequest);

        return ResponseEntity.ok(response);
    }

    /**
     * 고객 본인의 알림 카테고리별 요약 (AS_STATUS/CONSUMABLE/WARRANTY + total, 각 count·unreadCount)
     * GET /api/customer/notifications/summary
     */
    @GetMapping("/summary")
    public ResponseEntity<Map<String, NotificationCategoryStat>> getNotificationSummary(
            @AuthenticationPrincipal CustomUserDetails userDetails) {

        Map<String, NotificationCategoryStat> summary =
                notificationService.getNotificationSummaryWithUnread(userDetails.getUserId());
        return ResponseEntity.ok(summary);
    }

    /**
     * 고객 본인의 알림 단건 읽음 처리
     * PATCH /api/customer/notifications/{notificationId}/read
     */
    @PatchMapping("/{notificationId}/read")
    public ResponseEntity<Void> markAsRead(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable Long notificationId) {

        notificationService.markAsRead(userDetails.getUserId(), notificationId);
        return ResponseEntity.noContent().build();
    }

    /**
     * 고객 본인의 알림 전체 일괄 읽음 처리
     * PATCH /api/customer/notifications/read-all
     */
    @PatchMapping("/read-all")
    public ResponseEntity<Void> markAllAsRead(
            @AuthenticationPrincipal CustomUserDetails userDetails) {

        notificationService.markAllAsRead(userDetails.getUserId());
        return ResponseEntity.noContent().build();
    }
}
