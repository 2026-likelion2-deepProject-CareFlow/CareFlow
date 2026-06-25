package com.careflow.assignment.controller;

import com.careflow.assignment.dto.AssignmentDetailResponse;
import com.careflow.assignment.dto.AssignmentFilterResponse;
import com.careflow.assignment.dto.AssignmentReassignResponse;
import com.careflow.assignment.dto.AssignmentResponse;
import com.careflow.assignment.service.AgenciesAssignmentService;
import com.careflow.assignment.service.AssignmentDetailService;
import com.careflow.assignment.service.AssignmentFilterService;
import com.careflow.assignment.service.AssignmentReassignService;
import com.careflow.auth.security.CustomUserDetails;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/assignment")
@RequiredArgsConstructor
public class AssignmentController {

    private final AgenciesAssignmentService agenciesAssignmentService;
    private final AssignmentDetailService assignmentDetailService;
    private final AssignmentFilterService assignmentFilterService;
    private final AssignmentReassignService assignmentReassignService;

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
    @GetMapping("/detail/{assignmentId}")
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
