package com.careflow.agency.dto.response;

import com.careflow.engineer.domain.entity.EngineerProfile;
import com.careflow.engineer.domain.entity.EngineerSchedule;
import com.careflow.engineer.domain.entity.EngineerScheduleSlot;
import lombok.Builder;
import lombok.Getter;

import java.util.List;

/**
 * A/S 요청 기반 추천 기사 응답 DTO
 * GET /api/agency/engineers/recommended?requestId=
 */
@Getter
@Builder
public class EngineerRecommendResponse {

    private Long id;
    private String name;
    // 첫 번째 전문 브랜드 (없으면 null)
    private String brand;
    // 활동 지역명 (첫 번째 서비스 지역)
    private String region;
    private Double rating;
    // 근무 시작 가능 시각 (예: "09:00 이후 가능", 슬롯 없으면 "하루 종일 가능")
    private String availableFrom;
    private String profileImageUrl;
    private Boolean isLmsCompleted;

    public static EngineerRecommendResponse from(
            EngineerProfile profile,
            List<String> expertBrands,
            List<String> regionNames,
            EngineerSchedule schedule) {

        // 가용 시간 산정 — 첫 번째 timeSlot의 startTime 사용
        String availableFrom = "하루 종일 가능";
        if (schedule != null && !schedule.getTimeSlots().isEmpty()) {
            EngineerScheduleSlot firstSlot = schedule.getTimeSlots().get(0);
            availableFrom = firstSlot.getStartTime().toString().substring(0, 5) + " 이후 가능";
        }

        return EngineerRecommendResponse.builder()
                .id(profile.getUser().getId())
                .name(profile.getUser().getName())
                .brand(expertBrands.isEmpty() ? null : expertBrands.get(0))
                .region(regionNames.isEmpty() ? null : regionNames.get(0))
                .rating(profile.getAvgRating() != null ? profile.getAvgRating().doubleValue() : 0.0)
                .availableFrom(availableFrom)
                .profileImageUrl(profile.getProfileImageUrl())
                .isLmsCompleted(profile.isLmsCompleted())
                .build();
    }
}
