package com.careflow.agency.dto.response;

import org.springframework.data.domain.Page;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public record AgencyReviewListResponse(
        Stats stats,
        List<ReviewSummary> content,
        long totalElements,
        int totalPages,
        int currentPage,
        int size
) {

    public record Stats(
            double avgRating,
            long totalCount,
            double fiveStarRate,
            long newThisMonth,
            double prevMonthAvgRatingDiff,
            long prevMonthTotalDiff,
            long prevMonthNewDiff
    ) {}

    public record ReviewSummary(
            Long reviewId,
            Long requestId,
            String customerName,
            Long engineerId,
            String engineerName,
            String agencyName,
            String productName,
            String modelNo,
            String visitDate,
            String visitTime,
            int rating,
            String content,
            boolean isVisible,
            LocalDateTime createdAt
    ) {}

    public static AgencyReviewListResponse of(Stats stats, Page<ReviewSummary> page) {
        return new AgencyReviewListResponse(
                stats,
                page.getContent(),
                page.getTotalElements(),
                page.getTotalPages(),
                page.getNumber(),
                page.getSize()
        );
    }
}
