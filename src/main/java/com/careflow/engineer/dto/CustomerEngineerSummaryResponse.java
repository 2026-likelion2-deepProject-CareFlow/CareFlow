package com.careflow.engineer.dto;

import com.careflow.engineer.domain.entity.EngineerProfile;
import lombok.Builder;
import lombok.Getter;

import java.util.List;

/**
 * 고객용 수동 배정 - 후보 기사 목록 응답 DTO
 * GET /api/customers/{customerId}/engineers/available
 */
@Getter
@Builder
public class CustomerEngineerSummaryResponse {

    private Long engineerId;
    private String name;
    private Double rating;
    private List<String> brands;
    private String skills;
    private String profileImageUrl;

    public static CustomerEngineerSummaryResponse from(EngineerProfile profile, List<String> brands) {
        return CustomerEngineerSummaryResponse.builder()
                .engineerId(profile.getUser().getId())
                .name(profile.getUser().getName())
                .rating(profile.getAvgRating() != null ? profile.getAvgRating().doubleValue() : 0.0)
                .brands(brands)
                .skills(profile.getCategory() != null ? profile.getCategory().getName() : null)
                .profileImageUrl(profile.getProfileImageUrl())
                .build();
    }
}
