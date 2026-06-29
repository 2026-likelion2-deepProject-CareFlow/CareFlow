package com.careflow.review.dto;

import com.careflow.review.entity.Review;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
public class EngineerReviewResponse {
    private Long reviewId;
    private Integer rating;
    private String content;
    private LocalDateTime createdAt;

    // 프론트엔드 화면을 풍성하게 만들어줄 추가 정보
    private String customerName; // 고객 이름 (예: 김*민)
    private String applianceInfo; // 가전 정보 (예: 삼성 세탁기)

    public static EngineerReviewResponse from(Review review) {
        // 고객 이름 마스킹 처리 (김태희 -> 김*희, 홍길동 -> 홍*동)
        String rawName = review.getCustomer().getName();
        String maskedName = rawName;
        if (rawName != null && rawName.length() >= 2) {
            if (rawName.length() == 2) {
                maskedName = rawName.charAt(0) + "*";
            } else {
                maskedName = rawName.charAt(0) + "*" + rawName.substring(2);
            }
        }

        // 가전 정보 조립 (예: LG 냉장고)
        String applianceInfo = "정보 없음";
        if (review.getAsRequest() != null && review.getAsRequest().getAppliance() != null) {
            applianceInfo = review.getAsRequest().getAppliance().getBrand() + " " +
                    review.getAsRequest().getAppliance().getCategory().getName();
        }

        return EngineerReviewResponse.builder()
                .reviewId(review.getId())
                .rating(review.getRating())
                .content(review.getContent())
                .createdAt(review.getCreatedAt())
                .customerName(maskedName)
                .applianceInfo(applianceInfo)
                .build();
    }
}