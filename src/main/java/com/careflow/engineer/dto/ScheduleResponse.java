package com.careflow.engineer.dto;

import com.careflow.engineer.domain.entity.EngineerSchedule;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDate;

@Getter
@Builder
public class ScheduleResponse {
    private Long scheduleId;
    private LocalDate workDate;
    private String timeSlots;
    private String status;

    public static ScheduleResponse from(EngineerSchedule entity){
        return ScheduleResponse.builder()
                .scheduleId(entity.getScheduleId())
                .workDate(entity.getWorkDate())
                .timeSlots(entity.getTimeSlots())
                .status(entity.getStatus().name())
                .build();
    }
}
