package com.careflow.notification.controller;

import com.careflow.auth.security.CustomUserDetails;
import com.careflow.notification.dto.AgencyNoticeDetailRequest;
import com.careflow.notification.dto.AgencyNoticeDetailResponse;
import com.careflow.notification.service.AgencyNoticeDetailService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/agency/notices")
@RequiredArgsConstructor
public class AgencyNoticeDetailController {

    private final AgencyNoticeDetailService agencyNoticeDetailService;

    /**
     * [GET] /api/agency/notices
     * 알림 목록 조회 API(GET /api/agency/notifications)에서 받은 notificationId로 알림 단건 상세 조회.
     * - notificationId는 요청 명세에 따라 GET이지만 @RequestBody로 전달받는다.
     * - role != AGENCY → 401 Unauthorized
     * - notificationId 미존재 → 404 Not Found
     * - 타 대행사 소속 알림 조회 시도 → 401 Unauthorized
     */
    @GetMapping
    public ResponseEntity<List<AgencyNoticeDetailResponse>> getNoticeDetail(
            @Valid @RequestBody AgencyNoticeDetailRequest request,
            @AuthenticationPrincipal CustomUserDetails userDetails) throws IllegalAccessException {

        List<AgencyNoticeDetailResponse> response =
                agencyNoticeDetailService.getNoticeDetail(request.notificationId(), userDetails);
        return ResponseEntity.ok(response);
    }
}
