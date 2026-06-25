package com.careflow.assignment.service;

import com.careflow.assignment.dto.AgencyAssignmentResponse;
import com.careflow.assignment.entity.AsAssignment;
import com.careflow.assignment.repository.AsAssignmentRepository;
import com.careflow.auth.security.CustomUserDetails;
import com.careflow.common.enums.Role;
import com.careflow.user.entity.User;
import com.careflow.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.NoSuchElementException;

@Service
@RequiredArgsConstructor
public class AgenciesAssignmentService {

    private final AsAssignmentRepository asAssignmentRepository;
    private final UserRepository userRepository;

    /**
     * 현재 로그인한 대행사 관리자(AGENCY)와 같은 소속의 배차 내역 조회.
     * 1. 권한 확인 — role != AGENCY 이면 401 반환
     * 2. 로그인 유저의 agency_id 추출 후 배차 테이블 조회
     * 3. 조회 결과를 DTO 리스트로 변환하여 반환 (빈 결과 허용 — 204 는 컨트롤러에서 처리)
     */
    @Transactional(readOnly = true)
    public List<AgencyAssignmentResponse> getAssignmentsByAgency(CustomUserDetails userDetails) throws IllegalAccessException {

        // 권한 검증 — AGENCY 관리자만 접근 허용
        if (!Role.AGENCY.name().equals(userDetails.getRole())) {
            throw new IllegalAccessException("대행사 관리자 권한이 없습니다.");
        }

        // 로그인 유저 정보에서 소속 대행사 ID 추출
        User user = userRepository.findById(userDetails.getUserId())
                .orElseThrow(() -> new NoSuchElementException("로그인 정보에 해당하는 사용자를 찾을 수 없습니다."));

        if (user.getAgency() == null) {
            throw new IllegalStateException("소속 대행사 정보가 없습니다.");
        }

        Long agencyId = user.getAgency().getId();

        List<AsAssignment> assignments = asAssignmentRepository.findByAgency_Id(agencyId);

        return assignments.stream()
                .map(AgencyAssignmentResponse::from)
                .toList();
    }
}
