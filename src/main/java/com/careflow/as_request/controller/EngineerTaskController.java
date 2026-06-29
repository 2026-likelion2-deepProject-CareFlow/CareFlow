package com.careflow.as_request.controller;

import com.careflow.as_request.dto.EngineerTaskScheduleResponse;
import com.careflow.as_request.service.AsRequestService;
import com.careflow.as_request.service.EngineerTaskScheduleService;
import com.careflow.auth.security.CustomUserDetails;
import com.careflow.common.enums.AsStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/engineer")
@RequiredArgsConstructor
public class EngineerTaskController {

    private final AsRequestService asRequestService;
    private final EngineerTaskScheduleService engineerTaskScheduleService;

    /**
     * 기사 본인 A/S 작업 일정 조회
     * - 특정 날짜에 배정된 작업 목록(고객·제품·방문주소 포함) 반환
     * - REJECTED 배정 건은 제외
     */
    @GetMapping("/schedule")
    public ResponseEntity<List<EngineerTaskScheduleResponse>> getTaskSchedule(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @AuthenticationPrincipal CustomUserDetails userDetails) {

        List<EngineerTaskScheduleResponse> result =
                engineerTaskScheduleService.getTaskSchedule(userDetails.getUserId(), date);
        return ResponseEntity.ok(result);
    }

    /**
     * 기사 작업 수행 단계별 상태 업데이트 API
     * - 전달 가능한 상태: ENGINEER_DEPARTED(출발), ENGINEER_ARRIVED(도착), IN_PROGRESS(작업시작)
     */
    @PatchMapping("/tasks/{requestId}/status")
    public ResponseEntity<String> updateTaskStatus(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable Long requestId,
            @RequestParam AsStatus newStatus) {

        if (!"ENGINEER".equals(userDetails.getRole())) {
            throw new IllegalArgumentException("수리 기사만 작업 상태를 변경할 수 있습니다.");
        }

        asRequestService.updateEngineerTaskStatus(userDetails.getUserId(), requestId, newStatus);

        return ResponseEntity.ok("작업 상태가 " + newStatus.name() + " (으)로 성공적으로 변경되었습니다.");
    }
}