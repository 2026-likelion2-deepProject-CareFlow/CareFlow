package com.careflow.assignment.controller;

import com.careflow.assignment.dto.AgencyAssignmentResponse;
import com.careflow.assignment.service.AgenciesAssignmentService;
import com.careflow.auth.security.CustomUserDetails;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/agencies")
@RequiredArgsConstructor
public class AgenciesAssignmentController {

    private final AgenciesAssignmentService agenciesAssignmentService;

    /**
     * [GET] /api/agencies/assignment
     * 현재 로그인한 대행사 관리자 소속의 수리 기사 배차 내역 조회.
     * - role != AGENCY → 401 Unauthorized
     * - 배차 내역 없음 → 204 No Content
     * - 배차 내역 있음 → 200 OK + List<AgencyAssignmentResponse>
     */
    @GetMapping("/assignment")
    public ResponseEntity<List<AgencyAssignmentResponse>> getAgenciesAssignment(
            @AuthenticationPrincipal CustomUserDetails userDetails) throws IllegalAccessException {

        List<AgencyAssignmentResponse> result = agenciesAssignmentService.getAssignmentsByAgency(userDetails);

        if (result.isEmpty()) {
            return ResponseEntity.noContent().build();
        }

        return ResponseEntity.ok(result);
    }
}
