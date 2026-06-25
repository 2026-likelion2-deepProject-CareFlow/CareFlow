package com.careflow.as_status_log.service;

import com.careflow.as_status_log.dto.AsStatusLogListResponse;
import com.careflow.as_status_log.dto.AsStatusLogSummaryResponse;
import com.careflow.as_status_log.entity.AsStatusLog;
import com.careflow.as_status_log.repository.AsStatusLogRepository;
import com.careflow.auth.security.CustomUserDetails;
import com.careflow.common.enums.Role;
import com.careflow.user.entity.User;
import com.careflow.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AgencyAsStatusLogService {

    private final AsStatusLogRepository asStatusLogRepository;
    private final UserRepository userRepository;

    /**
     * 소속 대행사 A/S 상태 변경 이력 목록 조회 (최신순).
     * 1. AGENCY 권한 확인
     * 2. 로그인 유저의 소속 agency_id 추출
     * 3. as_status_logs JOIN as_requests WHERE agency_id = :agencyId
     */
    @Transactional(readOnly = true)
    public AsStatusLogListResponse getStatusLogs(
            CustomUserDetails userDetails) throws IllegalAccessException {

        Long agencyId = extractAgencyId(userDetails);
        List<AsStatusLog> logs = asStatusLogRepository
                .findAllByAgencyIdOrderByCreatedAtDesc(agencyId);
        return AsStatusLogListResponse.of(logs);
    }

    /**
     * 소속 대행사 A/S to_status 기준 집계.
     * - 이력이 없는 상태는 0으로 채워 반환
     */
    @Transactional(readOnly = true)
    public AsStatusLogSummaryResponse getStatusSummary(
            CustomUserDetails userDetails) throws IllegalAccessException {

        Long agencyId = extractAgencyId(userDetails);

        // GROUP BY 결과를 Map<toStatus문자열, count>으로 변환
        List<Object[]> rows = asStatusLogRepository.countGroupByToStatusForAgency(agencyId);
        Map<String, Long> countMap = rows.stream()
                .collect(Collectors.toMap(
                        r -> (String) r[0],
                        r -> (Long) r[1]
                ));

        return new AsStatusLogSummaryResponse(
                countMap.getOrDefault("WAITING", 0L),
                countMap.getOrDefault("ENGINEER_DEPARTED", 0L),
                countMap.getOrDefault("ENGINEER_ARRIVED", 0L),
                countMap.getOrDefault("IN_PROGRESS", 0L),
                countMap.getOrDefault("COMPLETED", 0L)
        );
    }

    /**
     * 공통: AGENCY 권한 확인 후 소속 agency_id 추출
     */
    private Long extractAgencyId(CustomUserDetails userDetails) throws IllegalAccessException {
        if (!Role.AGENCY.name().equals(userDetails.getRole())) {
            throw new IllegalAccessException("대행사 관리자 권한이 없습니다.");
        }

        User user = userRepository.findById(userDetails.getUserId())
                .orElseThrow(() -> new NoSuchElementException("로그인 정보에 해당하는 사용자를 찾을 수 없습니다."));

        if (user.getAgency() == null) {
            throw new IllegalStateException("소속 대행사 정보가 없습니다.");
        }

        return user.getAgency().getId();
    }
}
