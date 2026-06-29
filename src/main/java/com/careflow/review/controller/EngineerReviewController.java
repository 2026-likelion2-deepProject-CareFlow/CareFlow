package com.careflow.review.controller;

import com.careflow.auth.security.CustomUserDetails;
import com.careflow.review.dto.EngineerReviewResponse;
import com.careflow.review.service.ReviewService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/engineer/reviews")
@RequiredArgsConstructor
public class EngineerReviewController {

    private final ReviewService reviewService;

    /**
     * 기사용: 본인에게 작성된 고객 리뷰 목록 조회
     */
    @GetMapping
    public ResponseEntity<List<EngineerReviewResponse>> getMyReviews(
            @AuthenticationPrincipal CustomUserDetails userDetails) {

        List<EngineerReviewResponse> response = reviewService.getEngineerReviews(userDetails.getUserId());
        return ResponseEntity.ok(response);
    }
}