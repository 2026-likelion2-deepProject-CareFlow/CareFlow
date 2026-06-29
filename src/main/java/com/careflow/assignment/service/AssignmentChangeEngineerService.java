package com.careflow.assignment.service;

import com.careflow.assignment.dto.AssignmentChangeEngineerRequest;
import com.careflow.assignment.dto.AssignmentChangeEngineerResponse;
import com.careflow.assignment.entity.AsAssignment;
import com.careflow.assignment.repository.AsAssignmentRepository;
import com.careflow.auth.security.CustomUserDetails;
import com.careflow.common.enums.AssignType;
import com.careflow.user.entity.User;
import com.careflow.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.NoSuchElementException;

@Service
@RequiredArgsConstructor
public class AssignmentChangeEngineerService {

    private final AsAssignmentRepository asAssignmentRepository;
    private final UserRepository userRepository;

    @Transactional
    public AssignmentChangeEngineerResponse changeEngineer(
            AssignmentChangeEngineerRequest request,
            CustomUserDetails userDetails) throws IllegalAccessException {

        // 역할 검증 — AGENCY만 접근 가능
        if (!"AGENCY".equals(userDetails.getRole())) {
            throw new IllegalAccessException("대행사 관리자만 접근할 수 있습니다.");
        }

        // 기존 배정 조회
        AsAssignment existing = asAssignmentRepository.findById(request.assignmentId())
                .orElseThrow(() -> new NoSuchElementException("해당 배정 내역을 찾을 수 없습니다."));

        // 대행사 소속 확인
        if (!existing.getAgency().getId().equals(userDetails.getAgencyId())) {
            throw new IllegalAccessException("소속 대행사의 배정만 변경할 수 있습니다.");
        }

        // 수락 대기(WAITING) 상태 배정만 기사 변경 가능
        if (!"WAITING".equals(existing.getStatus())) {
            throw new IllegalStateException("수락 대기 상태의 배정만 기사를 변경할 수 있습니다.");
        }

        // 새 기사 조회
        User newEngineer = userRepository.findById(request.newEngineerId())
                .orElseThrow(() -> new NoSuchElementException("해당 수리 기사를 찾을 수 없습니다."));

        // 기존 배정 취소(REJECTED)
        existing.cancel();

        // 새 배정 생성 (WAITING)
        AsAssignment newAssignment = AsAssignment.create(
                existing.getAsRequest(),
                newEngineer,
                existing.getAgency(),
                AssignType.MANUAL
        );
        AsAssignment saved = asAssignmentRepository.save(newAssignment);

        return new AssignmentChangeEngineerResponse(
                saved.getId(),
                saved.getAsRequest().getId(),
                newEngineer.getId(),
                newEngineer.getName(),
                saved.getStatus(),
                saved.getAssignedAt()
        );
    }
}
