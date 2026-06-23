package com.careflow.engineer.dto;

import com.careflow.engineer.domain.entity.EngineerSchedule;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDate;
import java.util.List;
import java.util.stream.Collectors;

@Getter
@Builder
public class ScheduleResponse {
    private Long scheduleId;
    private LocalDate workDate;
    private List<TimeSlotResponseDto> timeSlots;
    private String status;

    @Getter
    @Builder
    public static class TimeSlotResponseDto {
        private String start;
        private String end;
    }

    public static ScheduleResponse from(EngineerSchedule entity){

        List<TimeSlotResponseDto> slotDtos = entity.getTimeSlots().stream()
                .map(slot -> TimeSlotResponseDto.builder()
                        .start(slot.getStartTime().toString())
                        .end(slot.getEndTime().toString())
                        .build())
                .toList();

        return ScheduleResponse.builder()
                .scheduleId(entity.getScheduleId())
                .workDate(entity.getWorkDate())
                .timeSlots(slotDtos)
                .status(entity.getStatus().name())
                .build();
    }
}
