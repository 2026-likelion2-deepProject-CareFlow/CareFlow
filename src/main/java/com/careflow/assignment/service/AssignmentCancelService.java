package com.careflow.assignment.service;

import com.careflow.assignment.dto.AssignmentCancelResponse;
import com.careflow.assignment.entity.AsAssignment;
import com.careflow.assignment.repository.AsAssignmentRepository;
import com.careflow.auth.security.CustomUserDetails;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.NoSuchElementException;

@Service
@RequiredArgsConstructor
public class AssignmentCancelService {

    private final AsAssignmentRepository asAssignmentRepository;

    @Transactional
    public AssignmentCancelResponse cancel(Long assignmentId, CustomUserDetails userDetails)
            throws IllegalAccessException {

        // 역할 검증 — AGENCY만 접근 가능
        if (!"AGENCY".equals(userDetails.getRole())) {
            throw new IllegalAccessException("대행사 관리자만 접근할 수 있습니다.");
        }

        // 배정 조회 (asRequest JOIN FETCH)
        AsAssignment assignment = asAssignmentRepository.findDetailById(assignmentId)
                .orElseThrow(() -> new NoSuchElementException("해당 배정 내역을 찾을 수 없습니다."));

        // 대행사 소속 확인
        if (!assignment.getAgency().getId().equals(userDetails.getAgencyId())) {
            throw new IllegalAccessException("소속 대행사의 배정만 취소할 수 있습니다.");
        }

        // 배정 취소 — 내부에서 REJECTED·COMPLETED 상태 검증 포함
        assignment.cancel();

        // 연결된 A/S 요청 → AGENCY_RECEIVED(재배정 대기)로 되돌림
        assignment.getAsRequest().revertToAgencyReceived();

        return new AssignmentCancelResponse(
                assignment.getId(),
                assignment.getAsRequest().getId(),
                "REJECTED",
                "배정이 취소되었습니다. A/S 요청이 대기 상태로 변경되었습니다."
        );
    }
}
