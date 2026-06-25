package com.careflow.agency.dto.response;

import com.careflow.engineer.domain.entity.EngineerProfile;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 소속 기사 단건 상세 조회 응답 DTO
 * 대행사 관리자 전용 — User 정보 + EngineerProfile 정보를 함께 반환
 */
@Getter
@Builder
public class AgencyEngineerDetailResponse {

    private Long engineerUserId;
    private String name;
    private String email;
    private String phone;
    private Integer categoryId;
    private String categoryName;
    private Integer careerStartedYear;
    private String skillLevel;
    private String introduction;
    private String profileImageUrl;
    private BigDecimal avgRating;
    private Integer totalReviews;
    private boolean isLmsCompleted;
    private List<String> expertBrands;
    private List<Integer> serviceRegionIds;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static AgencyEngineerDetailResponse from(
            EngineerProfile profile,
            List<String> expertBrands,
            List<Integer> serviceRegionIds) {

        return AgencyEngineerDetailResponse.builder()
                .engineerUserId(profile.getUser().getId())
                .name(profile.getUser().getName())
                .email(profile.getUser().getEmail())
                .phone(profile.getUser().getPhone())
                .categoryId(profile.getCategory() != null ? profile.getCategory().getCategoryId() : null)
                .categoryName(profile.getCategory() != null ? profile.getCategory().getName() : null)
                .careerStartedYear(profile.getCareerStartedYear())
                .skillLevel(profile.getSkillLevel().name())
                .introduction(profile.getIntroduction())
                .profileImageUrl(profile.getProfileImageUrl())
                .avgRating(profile.getAvgRating())
                .totalReviews(profile.getTotalReviews())
                .isLmsCompleted(profile.isLmsCompleted())
                .expertBrands(expertBrands)
                .serviceRegionIds(serviceRegionIds)
                .createdAt(profile.getCreatedAt())
                .updatedAt(profile.getUpdatedAt())
                .build();
    }
}
