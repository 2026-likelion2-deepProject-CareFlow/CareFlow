package com.careflow.engineer.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.List;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ScheduleRequest {
    @NotNull
    @FutureOrPresent
    private LocalDate workDate;

    @Valid
    @NotEmpty
    private List<TimeSlotDto> timeSlots;

    @Getter
    @NoArgsConstructor(access = AccessLevel.PROTECTED)
    public static class TimeSlotDto {  // json 객체 배열을 list 객체로 변환해주는 내부 메서드
        @Pattern(regexp = "^([01]\\d|2[0-3]):([0-5]\\d)$", message = "근무 가능 시간대의 작성형식이 00:00 ~ 23:59 형식이어야 합니다.")
        @NotNull(message = "시작 시간 정보는 필수입니다.")
        private String start;

        @Pattern(regexp = "^([01]\\d|2[0-3]):([0-5]\\d)$", message = "근무 가능 시간대의 작성형식이 00:00 ~ 23:59 형식이어야 합니다.")
        @NotNull(message = "종료 시간 정보는 필수입니다.")
        private String end;
    }
}
