package com.careflow.engineer.controller;

import com.careflow.engineer.dto.ScheduleRequest;
import com.careflow.engineer.dto.ScheduleResponse;
import com.careflow.engineer.service.EngineerScheduleService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/engineers/me/schedules")
@RequiredArgsConstructor
public class EngineerScheduleController {
    private final EngineerScheduleService scheduleService;

    @PostMapping
    public ResponseEntity<ScheduleResponse> createSchedule( // 근무표 등록
            @AuthenticationPrincipal Long userId,
            @RequestBody @Valid ScheduleRequest request) {

        ScheduleResponse response = scheduleService.createSchedule(userId, request);

        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }
}
