package com.careflow.agency.controller;

import com.careflow.agency.dto.request.AgencyReviewSearchRequest;
import com.careflow.agency.dto.response.AgencyReviewListResponse;
import com.careflow.agency.service.AgencyReviewService;
import com.careflow.auth.security.CustomUserDetails;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/agency/reviews")
@RequiredArgsConstructor
public class AgencyReviewController {

    private final AgencyReviewService agencyReviewService;

    /**
     * 대행사 리뷰 목록 조회
     * GET /api/agency/reviews?page=0&size=10
     *
     * - 필터 조건(rating/engineerId/isVisible/dateFrom/dateTo/keyword)은 RequestBody 로 수신
     * - 바디 생략 시 전체 조회로 처리
     */
    @GetMapping
    public ResponseEntity<AgencyReviewListResponse> getReviews(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @RequestBody(required = false) AgencyReviewSearchRequest filter,
            @PageableDefault(size = 10) Pageable pageable) throws IllegalAccessException {

        // 바디 미전송 시 빈 필터로 처리
        if (filter == null) {
            filter = new AgencyReviewSearchRequest();
        }

        return ResponseEntity.ok(agencyReviewService.getReviews(userDetails, filter, pageable));
    }
}
