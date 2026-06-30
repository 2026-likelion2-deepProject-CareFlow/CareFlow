package com.careflow.assignment.service;

import com.careflow.assignment.dto.AssignmentScheduleRequest;
import com.careflow.assignment.dto.AssignmentScheduleResponse;
import com.careflow.assignment.entity.AsAssignment;
import com.careflow.assignment.repository.AsAssignmentRepository;
import com.careflow.auth.security.CustomUserDetails;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.NoSuchElementException;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class AssignmentScheduleService {

    private final AsAssignmentRepository asAssignmentRepository;

    // 일정 변경 가능한 배정 상태 — REJECTED·COMPLETED는 변경 불가
    private static final Set<String> CHANGEABLE_STATUSES = Set.of("WAITING", "ACCEPTED");

    @Transactional
    public AssignmentScheduleResponse updateSchedule(
            Long assignmentId,
            AssignmentScheduleRequest request,
            CustomUserDetails userDetails) throws IllegalAccessException {

        // 역할 검증 — AGENCY만 접근 가능
        if (!"AGENCY".equals(userDetails.getRole())) {
            throw new IllegalAccessException("대행사 관리자만 접근할 수 있습니다.");
        }

        // 배정 조회 (asRequest JOIN FETCH)
        AsAssignment assignment = asAssignmentRepository.findDetailById(assignmentId)
                .orElseThrow(() -> new NoSuchElementException("해당 배정 내역을 찾을 수 없습니다."));

        // 대행사 소속 확인
        if (!assignment.getAgency().getId().equals(userDetails.getAgencyId())) {
            throw new IllegalAccessException("소속 대행사의 배정만 변경할 수 있습니다.");
        }

        // 일정 변경 가능 상태 검증
        if (!CHANGEABLE_STATUSES.contains(assignment.getStatus())) {
            throw new IllegalStateException("완료되거나 취소된 배정의 일정은 변경할 수 없습니다.");
        }

        // as_requests 일정 업데이트 (더티 체킹으로 자동 flush)
        assignment.getAsRequest().updateSchedule(request.scheduledDate(), request.scheduledTime());

        return new AssignmentScheduleResponse(
                assignment.getId(),
                assignment.getAsRequest().getId(),
                request.scheduledDate(),
                request.scheduledTime()
        );
    }
}
