package com.careflow.assignment.service;

import com.careflow.assignment.dto.AssignmentFilterResponse;
import com.careflow.assignment.repository.AsAssignmentRepository;
import com.careflow.auth.security.CustomUserDetails;
import com.careflow.common.enums.Role;
import com.careflow.user.entity.User;
import com.careflow.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class AssignmentFilterService {

    private static final Set<String> VALID_STATUSES =
            Set.of("WAITING", "ACCEPTED", "REJECTED", "COMPLETED");

    private final AsAssignmentRepository asAssignmentRepository;
    private final UserRepository userRepository;

    /**
     * 날짜·상태 동적 필터로 대행사 소속 배차 목록 조회.
     * 1. role != AGENCY → 401
     * 2. status 입력값이 허용 ENUM 외 값 → 400
     * 3. 로그인 유저의 agencyId 추출
     * 4. searchByFilter(agencyId, date, status) 단일 쿼리 실행
     *    - date   = null 이면 날짜 조건 미적용
     *    - status = null 이면 상태 조건 미적용
     */
    @Transactional(readOnly = true)
    public List<AssignmentFilterResponse> search(CustomUserDetails userDetails,
                                                 LocalDate date,
                                                 String status) throws IllegalAccessException {
        // 1. 권한 검증
        if (!Role.AGENCY.name().equals(userDetails.getRole())) {
            throw new IllegalAccessException("대행사 관리자 권한이 없습니다.");
        }

        // 2. status 값 유효성 검증 (입력된 경우에만)
        if (status != null && !VALID_STATUSES.contains(status)) {
            throw new IllegalArgumentException(
                    "유효하지 않은 배차 상태입니다. 허용 값: WAITING, ACCEPTED, REJECTED, COMPLETED");
        }

        // 3. 로그인 유저 → 대행사 ID 추출
        User user = userRepository.findById(userDetails.getUserId())
                .orElseThrow(() -> new NoSuchElementException("로그인 정보에 해당하는 사용자를 찾을 수 없습니다."));

        if (user.getAgency() == null) {
            throw new IllegalStateException("소속 대행사 정보가 없습니다.");
        }

        Long agencyId = user.getAgency().getId();

        // 4. 동적 필터 조회 — null 파라미터는 해당 조건을 적용하지 않음
        return asAssignmentRepository.searchByFilter(agencyId, date, status)
                .stream()
                .map(AssignmentFilterResponse::from)
                .toList();
    }
}
