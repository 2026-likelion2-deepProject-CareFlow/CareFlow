// 파일 경로: src/main/java/com/careflow/engineer/dto/ScheduleResponse.java
package com.careflow.engineer.dto;

import com.careflow.engineer.domain.entity.EngineerSchedule;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDate;
import java.util.List;

@Getter
@Builder
public class ScheduleResponse {
    private Long scheduleId;
    private LocalDate workDate;
    private String status;
    private List<TimeSlotResponse> timeSlots;

    // 🌟 수정 1: .from() 대신 DTO 컨벤션에 맞춰 .of()로 통일
    public static ScheduleResponse of(EngineerSchedule schedule) {
        return ScheduleResponse.builder()
                .scheduleId(schedule.getScheduleId())
                .workDate(schedule.getWorkDate())
                .status(schedule.getStatus().name())
                .timeSlots(schedule.getTimeSlots().stream()
                        .map(slot -> new TimeSlotResponse(slot.getStartTime().toString(), slot.getEndTime().toString()))
                        .toList())
                .build();
    }

    // 🌟 신규: 에러 대신 내보낼 안전한 '휴무일(OFF)' 응답 객체 생성기
    public static ScheduleResponse ofOffDay(LocalDate date) {
        return ScheduleResponse.builder()
                .scheduleId(null)
                .workDate(date)
                .status("OFF") // 프론트가 안전하게 휴무로 인식하도록 처리
                .timeSlots(List.of())
                .build();
    }

    @Getter
    public static class TimeSlotResponse {
        private String start;
        private String end;
        public TimeSlotResponse(String start, String end) {
            this.start = start;
            this.end = end;
        }
    }
}