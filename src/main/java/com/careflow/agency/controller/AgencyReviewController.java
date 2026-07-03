package com.careflow.agency.controller;

import com.careflow.agency.dto.request.AgencyReviewSearchRequest;
import com.careflow.agency.dto.response.AgencyReviewListResponse;
import com.careflow.agency.service.AgencyReviewService;
import com.careflow.auth.security.CustomUserDetails;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 모든 엔드포인트는 AGENCY 역할만 접근 가능하다.
 */
@RestController
@RequestMapping("/api/agency/reviews")
@RequiredArgsConstructor
@PreAuthorize("hasRole('AGENCY')")
public class AgencyReviewController {

    private final AgencyReviewService agencyReviewService;

    /**
     * 대행사 리뷰 목록 조회
     * GET /api/agency/reviews?rating=&engineerId=&isVisible=&dateFrom=&dateTo=&keyword=&page=0&size=10
     *
     * - 필터 조건(rating/engineerId/isVisible/dateFrom/dateTo/keyword)은 쿼리 파라미터로 수신
     * - 파라미터 생략 시 전체 조회로 처리
     */
    @GetMapping
    public ResponseEntity<AgencyReviewListResponse> getReviews(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @RequestParam(required = false) Integer rating,
            @RequestParam(required = false) Long engineerId,
            @RequestParam(required = false) Boolean isVisible,
            @RequestParam(required = false) String dateFrom,
            @RequestParam(required = false) String dateTo,
            @RequestParam(required = false) String keyword,
            @PageableDefault(size = 10) Pageable pageable) throws IllegalAccessException {

        AgencyReviewSearchRequest filter =
                new AgencyReviewSearchRequest(rating, engineerId, isVisible, dateFrom, dateTo, keyword);

        return ResponseEntity.ok(agencyReviewService.getReviews(userDetails, filter, pageable));
    }
}
