package com.careflow.engineer.controller;

import com.careflow.auth.security.CustomUserDetails;
import com.careflow.engineer.dto.ScheduleRequest;
import com.careflow.engineer.dto.ScheduleResponse;
import com.careflow.engineer.service.EngineerScheduleService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/engineer/my-schedule")
@RequiredArgsConstructor
public class EngineerScheduleController {
    private final EngineerScheduleService scheduleService;

    @PostMapping
    public ResponseEntity<ScheduleResponse> createSchedule( // 근무표 등록
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @RequestBody @Valid ScheduleRequest request) {

        ScheduleResponse response = scheduleService.createSchedule(userDetails.getUserId(), request);

        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping
    public ResponseEntity<List<ScheduleResponse>> getMonthlySchedules(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @RequestParam(name = "year") int year,
            @RequestParam(name = "month") int month) {  // 월간 내 근무 일정 조회

        List<ScheduleResponse> responses = scheduleService.getMonthlySchedules(userDetails.getUserId(), year, month);
        return ResponseEntity.ok(responses);
    }

    @DeleteMapping("/{scheduleId}")
    public ResponseEntity<String> deleteSchedule(   // 스케줄 삭제 및 OFF 상태 처리
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable(name = "scheduleId") Long scheduleId) {

        scheduleService.deleteSchedule(userDetails.getUserId(), scheduleId);
        return ResponseEntity.ok("해당 날짜의 근무표가 휴무(OFF) 처리되었습니다.");
    }
}
