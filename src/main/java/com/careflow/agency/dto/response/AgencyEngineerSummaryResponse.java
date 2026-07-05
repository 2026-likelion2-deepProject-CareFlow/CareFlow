package com.careflow.agency.dto.response;

import com.careflow.engineer.domain.entity.EngineerProfile;
import lombok.Builder;
import lombok.Getter;

import java.util.List;

/**
 * 소속 기사 목록 조회 응답 DTO
 * 목록 화면에서 필요한 요약 정보만 포함
 */
@Getter
@Builder
public class AgencyEngineerSummaryResponse {

    private Long engineerUserId;
    private String name;
    private Integer categoryId;
    private String categoryName;
    private String skillLevel;
    private List<Integer> serviceRegionIds;
    private boolean isLmsCompleted;
    private String currentWorkStatus;

    public static AgencyEngineerSummaryResponse from(
            EngineerProfile profile,
            List<Integer> serviceRegionIds,
            String currentWorkStatus) {

        return AgencyEngineerSummaryResponse.builder()
                .engineerUserId(profile.getUser().getId())
                .name(profile.getUser().getName())
                .categoryId(profile.getCategory() != null ? profile.getCategory().getCategoryId() : null)
                .categoryName(profile.getCategory() != null ? profile.getCategory().getName() : null)
                .skillLevel(profile.getSkillLevel().name())
                .serviceRegionIds(serviceRegionIds)
                .isLmsCompleted(profile.isLmsCompleted())
                .currentWorkStatus(currentWorkStatus)
                .build();
    }
}
