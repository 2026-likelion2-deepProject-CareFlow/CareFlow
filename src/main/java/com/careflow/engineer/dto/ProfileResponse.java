package com.careflow.engineer.dto;

import com.careflow.engineer.domain.entity.EngineerProfile;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Getter
@Builder
public class ProfileResponse {
    private Long profileId;
    private Long userId;
    private Integer categoryId;
    private Integer careerStartedYear;
    private String skillLevel;
    private Boolean isLmsCompleted;
    private String introduction;
    private String profileImageUrl;
    private BigDecimal avgRating;
    private Integer totalReviews;

    private List<String> expertBrands;
    private List<Integer> serviceRegionIds;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static ProfileResponse from(EngineerProfile entity,
                                       List<String> expertBrands,
                                       List<Integer> serviceRegionIds) {
        return ProfileResponse.builder()
                .profileId(entity.getProfileId())
                .userId(entity.getUser().getId())
                .categoryId(entity.getCategory() != null ? entity.getCategory().getCategoryId() : null)
                .careerStartedYear(entity.getCareerStartedYear())
                .skillLevel(entity.getSkillLevel().name())
                .isLmsCompleted(entity.isLmsCompleted())
                .introduction(entity.getIntroduction())
                .profileImageUrl(entity.getProfileImageUrl())
                .avgRating(entity.getAvgRating())
                .totalReviews(entity.getTotalReviews())
                .expertBrands(expertBrands)
                .serviceRegionIds(serviceRegionIds)
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }
}