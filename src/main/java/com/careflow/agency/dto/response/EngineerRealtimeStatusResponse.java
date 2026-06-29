package com.careflow.agency.dto.response;

import com.careflow.assignment.entity.AsAssignment;
import com.careflow.engineer.domain.entity.EngineerProfile;
import lombok.Builder;
import lombok.Getter;

import java.util.List;

/**
 * 소속 기사 실시간 배정 현황 응답 DTO
 * GET /api/agency/engineers/realtime-status
 */
@Getter
@Builder
public class EngineerRealtimeStatusResponse {

    private Long id;
    private String name;
    // 전문 카테고리명 (없으면 null)
    private String specialty;
    // 활동 지역명 (첫 번째 서비스 지역)
    private String region;
    // 현재 작업 제품명 (예: "LG 디오스 냉장고 수리", 없으면 null)
    private String product;
    // 예약 시간 범위 (예: "10:00 ~ 12:00", 없으면 null)
    private String timeRange;
    // 진행률 (IN_PROGRESS = 50, 그 외 = null)
    private Integer progress;
    private Boolean isLmsCompleted;
    private String profileImageUrl;
    // "IN_PROGRESS" | "ASSIGNED" | null
    private String asStatus;

    public static EngineerRealtimeStatusResponse from(
            EngineerProfile profile,
            List<String> regionNames,
            AsAssignment activeAssignment) {

        String product = null;
        String timeRange = null;
        Integer progress = null;
        String asStatus = null;

        if (activeAssignment != null) {
            String requestStatus = activeAssignment.getAsRequest().getStatus().name();

            if ("IN_PROGRESS".equals(requestStatus)) {
                asStatus = "IN_PROGRESS";
                progress = 50;
            } else if ("ASSIGNED".equals(requestStatus) || "ACCEPTED".equals(requestStatus)) {
                asStatus = "ASSIGNED";
            }

            // 작업 제품명 — "{브랜드} {모델명} 수리"
            var appliance = activeAssignment.getAsRequest().getAppliance();
            product = appliance.getBrand() + " " + appliance.getModelName() + " 수리";

            // 예약 시간 범위 — 시작 시각 ~ 시작+2시간 (예상 작업 시간)
            String scheduledTime = activeAssignment.getAsRequest().getScheduledTime();
            if (scheduledTime != null && scheduledTime.length() >= 5) {
                try {
                    String[] parts = scheduledTime.substring(0, 5).split(":");
                    int hour = Integer.parseInt(parts[0]);
                    int minute = Integer.parseInt(parts[1]);
                    int endHour = (hour + 2) % 24;
                    timeRange = String.format("%02d:%02d ~ %02d:%02d", hour, minute, endHour, minute);
                } catch (NumberFormatException ignored) {
                    timeRange = scheduledTime;
                }
            }
        }

        String specialty = profile.getCategory() != null ? profile.getCategory().getName() : null;

        return EngineerRealtimeStatusResponse.builder()
                .id(profile.getUser().getId())
                .name(profile.getUser().getName())
                .specialty(specialty)
                .region(regionNames.isEmpty() ? null : regionNames.get(0))
                .product(product)
                .timeRange(timeRange)
                .progress(progress)
                .isLmsCompleted(profile.isLmsCompleted())
                .profileImageUrl(profile.getProfileImageUrl())
                .asStatus(asStatus)
                .build();
    }
}
