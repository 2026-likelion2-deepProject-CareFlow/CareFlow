package com.careflow.agency.dto.response;

import com.careflow.review.entity.Review;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 기사 수신 리뷰 목록 응답 DTO
 * GET /api/agency/engineers/{id}/reviews
 */
@Getter
@Builder
public class EngineerReviewListResponse {

    private Integer totalReviews;
    private Double avgRating;
    private List<ReviewItem> reviews;

    @Getter
    @Builder
    public static class ReviewItem {
        private Long reviewId;
        private String customerName;
        private Integer rating;
        private String content;
        private LocalDateTime createdAt;

        public static ReviewItem from(Review review) {
            return ReviewItem.builder()
                    .reviewId(review.getId())
                    .customerName(review.getCustomer().getName())
                    .rating(review.getRating())
                    .content(review.getContent())
                    .createdAt(review.getCreatedAt())
                    .build();
        }
    }

    public static EngineerReviewListResponse of(List<Review> reviews) {
        List<ReviewItem> items = reviews.stream()
                .map(ReviewItem::from)
                .toList();

        double avg = reviews.stream()
                .mapToInt(Review::getRating)
                .average()
                .orElse(0.0);

        return EngineerReviewListResponse.builder()
                .totalReviews(reviews.size())
                .avgRating(Math.round(avg * 10.0) / 10.0)
                .reviews(items)
                .build();
    }
}
