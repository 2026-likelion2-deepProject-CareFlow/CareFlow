package com.careflow.review.controller;

import com.careflow.auth.security.CustomUserDetails;
import com.careflow.review.dto.ReviewCreateRequest;
import com.careflow.review.dto.ReviewResponse;
import com.careflow.review.service.ReviewService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/customer/as-requests")
@RequiredArgsConstructor
public class ReviewController {

    private final ReviewService reviewService;

    /**
     * 고객용: 작업 완료 후 기사 평점/리뷰 작성 API (C-23)
     * - PAID 상태인 A/S 요청에 대해서만 리뷰 가능
     * - 본인 요청이 아닌 경우 401 반환
     * - 이미 리뷰를 작성한 경우 409(IllegalStateException → 403) 반환
     * - 리뷰 저장 성공 시 기사 프로필 avgRating, totalReviews 즉시 갱신, 201 반환
     */
    @PostMapping("/{requestId}/reviews")
    public ResponseEntity<ReviewResponse> createReview(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable Long requestId,
            @Valid @RequestBody ReviewCreateRequest dto) throws IllegalAccessException {

        ReviewResponse response = reviewService.createReview(
                userDetails.getUserId(), requestId, dto);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }
}
