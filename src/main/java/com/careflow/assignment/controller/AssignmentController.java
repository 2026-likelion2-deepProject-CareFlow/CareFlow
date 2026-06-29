package com.careflow.assignment.controller;

import com.careflow.assignment.dto.*;
import com.careflow.assignment.service.*;
import com.careflow.auth.security.CustomUserDetails;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/agency/as-assignments")
@RequiredArgsConstructor
public class AssignmentController {

    private final AgenciesAssignmentService agenciesAssignmentService;
    private final AssignmentDetailService assignmentDetailService;
    private final AssignmentFilterService assignmentFilterService;
    private final AssignmentReassignService assignmentReassignService;
    private final AssignmentInProgressService assignmentInProgressService;
    private final AssignmentCompletedService assignmentCompletedService;
    private final AssignmentChangeEngineerService assignmentChangeEngineerService;
    private final AssignmentScheduleService assignmentScheduleService;
    private final AssignmentCancelService assignmentCancelService;

    /**
     * [GET] /api/agencies/assignment
     * 현재 로그인한 대행사 관리자 소속의 수리 기사 배차 내역 조회.
     * - role != AGENCY → 401 Unauthorized
     * - 배차 내역 없음 → 204 No Content
     * - 배차 내역 있음 → 200 OK + List<AgencyAssignmentResponse>
     */
    @GetMapping
    public ResponseEntity<List<AssignmentResponse>> getAgenciesAssignment(
            @AuthenticationPrincipal CustomUserDetails userDetails) throws IllegalAccessException {

        List<AssignmentResponse> result = agenciesAssignmentService.getAssignmentsByAgency(userDetails);

        if (result.isEmpty()) {
            return ResponseEntity.noContent().build();
        }

        return ResponseEntity.ok(result);
    }

    /**
     * [GET] /api/assignment/detail/{assignmentId}
     * 배차 내역 단건 상세 조회.
     * - role != AGENCY → 401 Unauthorized
     * - assignmentId 미존재 → 404 Not Found
     * - 성공 → 200 OK + AssignmentDetailResponse
     */
    @GetMapping("/{assignmentId}/detail")
    public ResponseEntity<AssignmentDetailResponse> getAssignmentDetail(
            @PathVariable Long assignmentId,
            @AuthenticationPrincipal CustomUserDetails userDetails) throws IllegalAccessException {

        AssignmentDetailResponse response = assignmentDetailService.getDetail(assignmentId, userDetails);
        return ResponseEntity.ok(response);
    }

    /**
     * [GET] /api/assignment/search
     * 날짜·상태 동적 필터로 대행사 소속 배차 현황 조회.
     * - date   미입력 시 오늘 날짜 기본 적용
     * - status 미입력 시 전체 상태 반환
     * - role != AGENCY → 401
     * - 결과 없음 → 204 No Content
     */
    @GetMapping("/search")
    public ResponseEntity<List<AssignmentFilterResponse>> searchAssignments(
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(required = false) String status,
            @AuthenticationPrincipal CustomUserDetails userDetails) throws IllegalAccessException {

        // 날짜 미입력 시 오늘 날짜를 기본값으로 적용
        LocalDate filterDate = (date != null) ? date : LocalDate.now();

        List<AssignmentFilterResponse> result =
                assignmentFilterService.search(userDetails, filterDate, status);

        if (result.isEmpty()) {
            return ResponseEntity.noContent().build();
        }

        return ResponseEntity.ok(result);
    }

    /**
     * [GET] /api/agency/as-assignments/in-progress
     * 진행 중(ACCEPTED) 배정 목록 조회. as_status_logs 포함.
     * - role != AGENCY → 401
     * - 결과 없음 → 204 No Content
     */
    @GetMapping("/in-progress")
    public ResponseEntity<List<AssignmentInProgressResponse>> getInProgress(
            @AuthenticationPrincipal CustomUserDetails userDetails) throws IllegalAccessException {

        List<AssignmentInProgressResponse> result = assignmentInProgressService.getInProgress(userDetails);
        if (result.isEmpty()) {
            return ResponseEntity.noContent().build();
        }
        return ResponseEntity.ok(result);
    }

    /**
     * [GET] /api/agency/as-assignments/completed
     * 완료(COMPLETED) 배정 목록 조회. work_reports + reviews 포함.
     * - role != AGENCY → 401
     * - 결과 없음 → 204 No Content
     */
    @GetMapping("/completed")
    public ResponseEntity<List<AssignmentCompletedResponse>> getCompleted(
            @AuthenticationPrincipal CustomUserDetails userDetails) throws IllegalAccessException {

        List<AssignmentCompletedResponse> result = assignmentCompletedService.getCompleted(userDetails);
        if (result.isEmpty()) {
            return ResponseEntity.noContent().build();
        }
        return ResponseEntity.ok(result);
    }

    /**
     * [POST] /api/agency/as-assignments/change
     * WAITING 상태 배정의 기사를 변경. 기존 배정 REJECTED + 새 배정 WAITING 생성.
     * - role != AGENCY → 401
     * - assignmentId or newEngineerId 미존재 → 404
     * - 배정 status != WAITING → 400
     */
    @PostMapping("/change")
    public ResponseEntity<AssignmentChangeEngineerResponse> changeEngineer(
            @Valid @RequestBody AssignmentChangeEngineerRequest request,
            @AuthenticationPrincipal CustomUserDetails userDetails) throws IllegalAccessException {

        AssignmentChangeEngineerResponse response =
                assignmentChangeEngineerService.changeEngineer(request, userDetails);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * [PUT] /api/agency/as-assignments/{assignmentId}/schedule
     * 배정된 A/S 방문 일정(날짜·시간) 변경.
     * - role != AGENCY → 401
     * - assignmentId 미존재 → 404
     * - 배정 status = REJECTED or COMPLETED → 400
     */
    @PutMapping("/{assignmentId}/schedule")
    public ResponseEntity<AssignmentScheduleResponse> updateSchedule(
            @PathVariable Long assignmentId,
            @Valid @RequestBody AssignmentScheduleRequest request,
            @AuthenticationPrincipal CustomUserDetails userDetails) throws IllegalAccessException {

        AssignmentScheduleResponse response =
                assignmentScheduleService.updateSchedule(assignmentId, request, userDetails);
        return ResponseEntity.ok(response);
    }

    /**
     * [DELETE] /api/agency/as-assignments/{assignmentId}
     * 배정 취소. 배정 REJECTED + A/S 요청 AGENCY_RECEIVED(재배정 대기)로 변경.
     * - role != AGENCY → 401
     * - assignmentId 미존재 → 404
     * - 배정 status = REJECTED or COMPLETED → 400
     */
    @DeleteMapping("/{assignmentId}")
    public ResponseEntity<AssignmentCancelResponse> cancelAssignment(
            @PathVariable Long assignmentId,
            @AuthenticationPrincipal CustomUserDetails userDetails) throws IllegalAccessException {

        AssignmentCancelResponse response = assignmentCancelService.cancel(assignmentId, userDetails);
        return ResponseEntity.ok(response);
    }

    /**
     * [POST] /api/assignment/{assignmentId}/reassign
     * 배차 응답 없음(30분 초과) 또는 거부(REJECTED) 건에 대한 재배차 처리.
     *
     * - as_request.assign_type = AUTO  → 이전 배차 기사 제외 자동 재배차
     * - as_request.assign_type = MANUAL → 재배차 불가, 고객에게 재신청 알림 발송
     * - role != AGENCY → 401
     * - assignmentId 미존재 → 404
     * - AUTO 재배차 가용 기사 없음 → 403
     */
    @PostMapping("/{assignmentId}/reassign")
    public ResponseEntity<AssignmentReassignResponse> reassignAssignment(
            @PathVariable Long assignmentId,
            @AuthenticationPrincipal CustomUserDetails userDetails) throws IllegalAccessException {

        AssignmentReassignResponse response =
                assignmentReassignService.reassign(assignmentId, userDetails);
        return ResponseEntity.ok(response);
    }
}
