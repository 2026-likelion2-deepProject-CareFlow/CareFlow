// 파일 경로: src/main/java/com/careflow/assignment/controller/EngineerAssignmentController.java
package com.careflow.assignment.controller;

import com.careflow.assignment.dto.AssignmentRejectRequest;
import com.careflow.assignment.dto.EngineerAssignmentResponse;
import com.careflow.assignment.service.EngineerAssignmentService;
import com.careflow.auth.security.CustomUserDetails;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/engineer/assignments")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ENGINEER')")
public class EngineerAssignmentController {

    private final EngineerAssignmentService engineerAssignmentService;

    // 기사 배정 목록 조회
    @GetMapping
    public ResponseEntity<Page<EngineerAssignmentResponse>> getAssignments(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @RequestParam(required = false, defaultValue = "ALL") String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {

        PageRequest pageRequest = PageRequest.of(page, size);
        Page<EngineerAssignmentResponse> response = engineerAssignmentService.getAssignments(userDetails.getUserId(), status, pageRequest);
        return ResponseEntity.ok(response);
    }

    // 배정 수락
    @PutMapping("/{assignmentId}/accept")
    public ResponseEntity<String> acceptAssignment(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable Long assignmentId) {

        engineerAssignmentService.acceptAssignment(userDetails.getUserId(), assignmentId);
        return ResponseEntity.ok("배정이 성공적으로 수락되었습니다.");
    }

    // 배정 거절
    @PutMapping("/{assignmentId}/reject")
    public ResponseEntity<String> rejectAssignment(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable Long assignmentId,
            @Valid @RequestBody AssignmentRejectRequest request) {

        engineerAssignmentService.rejectAssignment(userDetails.getUserId(), assignmentId, request);
        return ResponseEntity.ok("배정이 거절되었습니다.");
    }
}