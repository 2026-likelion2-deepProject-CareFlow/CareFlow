package com.careflow.notification.controller;

import com.careflow.auth.security.CustomUserDetails;
import com.careflow.notification.dto.NotificationResponse;
import com.careflow.notification.service.NotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

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
}