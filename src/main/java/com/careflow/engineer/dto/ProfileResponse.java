package com.careflow.engineer.dto;

import com.careflow.engineer.domain.EngineerProfile;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@Builder
public class ProfileResponse {
    private Long profileId;
    private Long userId;
    private Long categoryId;
    private Integer careerStartedYear;
    private String skillLevel;
    private Boolean isLmsCompleted;
    private String introduction;
    private String profileImageUrl;
    private BigDecimal avgRating;
    private Integer totalReviews;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static ProfileResponse from(EngineerProfile entity){
        return ProfileResponse.builder()
                .profileId(entity.getProfileId())
                .userId(entity.getUser().getUserId())
                .categoryId(entity.getCategory().getCategoryId())
                .careerStartedYear(entity.getCareerStartedYear())
                .skillLevel(entity.getSkillLevel().name())
                .isLmsCompleted(entity.isLmsCompleted())
                .introduction(entity.getIntroduction())
                .profileImageUrl(entity.getProfileImageUrl())
                .avgRating(entity.getAvgRating())
                .totalReviews(entity.getTotalReviews())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }
}
