package com.careflow.review.dto;

import java.time.LocalDateTime;

public record ReviewResponse(
        Long reviewId,
        Long requestId,
        Long engineerId,
        Integer rating,
        String content,
        LocalDateTime createdAt
) {}
