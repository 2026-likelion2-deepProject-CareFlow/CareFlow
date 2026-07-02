package com.careflow.review.controller;

import com.careflow.auth.security.CustomUserDetails;
import com.careflow.review.dto.EngineerReviewResponse;
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
}