// 파일 경로: src/main/java/com/careflow/engineer/dto/EngineerDashboardResponse.java
package com.careflow.engineer.dto;

import lombok.Builder;
import lombok.Getter;
import java.math.BigDecimal;
import java.util.List;

@Getter
@Builder
public class EngineerDashboardResponse {
    // 기사 기본 프로필 정보
    private String engineerName;
    private String skillLevel;
    private boolean isLmsCompleted;
    private BigDecimal avgRating;
    private int totalReviews;
    private String profileImageUrl;

    // 통계 (Stats)
    private int todayExpectedCount; // 오늘 예정 작업 건수
    private int todayCompletedCount; // 오늘 완료 건수
    private int thisMonthExpectedEarning; // 이번 달 예상 수익

    // 상태 및 스케줄
    private String currentWorkStatus; // 진행 중인 작업 상태
    private List<TodayScheduleDto> todaySchedules;
    private List<NoticeDto> notices;

    @Getter @Builder
    public static class TodayScheduleDto {
        private Long assignmentId;
        private String time;
        private String status;
        private String productName;
        private String modelNo;
        private String customerName;
        private String customerPhone;
        private String address;
        private String symptom;
    }

    @Getter @Builder
    public static class NoticeDto {
        private Long id;
        private String text;
        private String date;
    }
}