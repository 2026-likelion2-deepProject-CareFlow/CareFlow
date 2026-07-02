package com.careflow.review.controller;

import com.careflow.auth.security.CustomUserDetails;
import com.careflow.review.dto.EngineerReviewResponse;
import com.careflow.review.dto.EngineerReviewStatsResponse;
import com.careflow.review.service.ReviewService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/engineer/reviews")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ENGINEER')")
public class EngineerReviewController {

    private final ReviewService reviewService;

    @GetMapping
    public ResponseEntity<Page<EngineerReviewResponse>> getMyReviews(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @RequestParam(required = false) Integer rating,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {

        PageRequest pageRequest = PageRequest.of(Math.max(0, page), size);
        Page<EngineerReviewResponse> response = reviewService.getEngineerReviewsPaging(userDetails.getUserId(), rating, pageRequest);

        return ResponseEntity.ok(response);
    }

    /**
     * [기사용] 본인 리뷰 통계 조회 — 평점 분포(1~5점 각 개수) + 탭 필터 카운트
     * GET /api/engineer/reviews/stats
     *
     * <p>기존 목록 API(GET /api/engineer/reviews)와 독립적인 additive 엔드포인트다.
     * 목록은 선택된 탭(rating)으로 필터링된 페이지를 주고, 이 엔드포인트는 필터와 무관하게
     * 항상 전체(공개) 리뷰 기준 분포/카운트를 준다. 프론트는 이 응답으로 탭 카운트와 분포 차트를 그린다.</p>
     */
    @GetMapping("/stats")
    public ResponseEntity<EngineerReviewStatsResponse> getMyReviewStats(
            @AuthenticationPrincipal CustomUserDetails userDetails) {

        EngineerReviewStatsResponse stats = reviewService.getEngineerReviewStats(userDetails.getUserId());
        return ResponseEntity.ok(stats);
    }
}