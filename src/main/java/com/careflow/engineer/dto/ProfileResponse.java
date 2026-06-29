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

    // 🌟 프론트엔드 요구 추가 필드 (users, agencies)
    private String name;
    private String email;
    private String phone;
    private String agencyName;

    // 🌟 기존 ID + 프론트엔드 화면 표시용 이름
    private Integer categoryId;
    private String categoryName;
    private List<String> expertBrands;
    private List<Integer> serviceRegionIds;
    private List<String> serviceRegionNames;

    private Integer careerStartedYear;
    private String skillLevel;
    private Boolean isLmsCompleted;
    private String introduction;
    private String profileImageUrl;
    private BigDecimal avgRating;
    private Integer totalReviews;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static ProfileResponse of(EngineerProfile entity,
                                     List<String> expertBrands,
                                     List<Integer> serviceRegionIds,
                                     List<String> serviceRegionNames) {

        // 널 포인트(NPE) 방지를 위한 안전한 추출
        String agencyName = (entity.getUser().getAgency() != null)
                ? entity.getUser().getAgency().getAgencyName()
                : "소속 없음";

        String categoryName = (entity.getCategory() != null)
                ? entity.getCategory().getName()
                : "미지정";

        return ProfileResponse.builder()
                .profileId(entity.getProfileId())
                .userId(entity.getUser().getId())
                .name(entity.getUser().getName())
                .email(entity.getUser().getEmail())
                .phone(entity.getUser().getPhone())
                .agencyName(agencyName)
                .categoryId(entity.getCategory() != null ? entity.getCategory().getCategoryId() : null)
                .categoryName(categoryName)
                .careerStartedYear(entity.getCareerStartedYear())
                .skillLevel(entity.getSkillLevel().name())
                .isLmsCompleted(entity.isLmsCompleted())
                .introduction(entity.getIntroduction())
                .profileImageUrl(entity.getProfileImageUrl())
                .avgRating(entity.getAvgRating())
                .totalReviews(entity.getTotalReviews())
                .expertBrands(expertBrands)
                .serviceRegionIds(serviceRegionIds)
                .serviceRegionNames(serviceRegionNames)
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }
}