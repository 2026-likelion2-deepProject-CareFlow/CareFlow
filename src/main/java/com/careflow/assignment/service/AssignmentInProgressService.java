package com.careflow.assignment.service;

import com.careflow.as_status_log.entity.AsStatusLog;
import com.careflow.as_status_log.repository.AsStatusLogRepository;
import com.careflow.assignment.dto.AssignmentInProgressResponse;
import com.careflow.assignment.entity.AsAssignment;
import com.careflow.assignment.repository.AsAssignmentRepository;
import com.careflow.auth.security.CustomUserDetails;
import com.careflow.engineer.domain.entity.EngineerProfile;
import com.careflow.engineer.repository.EngineerProfileRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AssignmentInProgressService {

    private final AsAssignmentRepository asAssignmentRepository;
    private final AsStatusLogRepository asStatusLogRepository;
    private final EngineerProfileRepository engineerProfileRepository;

    // 단계별 진행 순서 — stepTimes 키 순서 정의
    private static final List<String> STEP_KEYS = List.of(
            "ACCEPTED", "WAITING", "ENGINEER_DEPARTED", "ENGINEER_ARRIVED", "IN_PROGRESS", "COMPLETED"
    );

    @Transactional(readOnly = true)
    public List<AssignmentInProgressResponse> getInProgress(CustomUserDetails userDetails)
            throws IllegalAccessException {

        // 역할 검증 — AGENCY만 접근 가능
        if (!"AGENCY".equals(userDetails.getRole())) {
            throw new IllegalAccessException("대행사 관리자만 접근할 수 있습니다.");
        }

        Long agencyId = userDetails.getAgencyId();

        // ACCEPTED 상태 배정 목록 조회 (as_requests·appliance·customer·engineer JOIN FETCH)
        List<AsAssignment> assignments = asAssignmentRepository.findInProgressByAgencyId(agencyId);
        if (assignments.isEmpty()) {
            return List.of();
        }

        // 기사 프로필 배치 조회 (N+1 방지)
        List<Long> engineerIds = assignments.stream()
                .map(a -> a.getEngineer().getId())
                .distinct()
                .collect(Collectors.toList());
        Map<Long, EngineerProfile> profileMap = engineerProfileRepository
                .findByUser_IdIn(engineerIds)
                .stream()
                .collect(Collectors.toMap(ep -> ep.getUser().getId(), ep -> ep));

        // 대행사 소속 전체 as_status_logs 배치 조회 (N+1 방지)
        List<Long> requestIds = assignments.stream()
                .map(a -> a.getAsRequest().getId())
                .collect(Collectors.toList());
        // request_id별 로그 그룹화 (최신순)
        Map<Long, List<AsStatusLog>> logMap = asStatusLogRepository
                .findAllByAgencyIdOrderByCreatedAtDesc(agencyId)
                .stream()
                .filter(l -> requestIds.contains(l.getAsRequest().getId()))
                .collect(Collectors.groupingBy(l -> l.getAsRequest().getId()));

        return assignments.stream()
                .map(a -> toResponse(a, profileMap, logMap))
                .collect(Collectors.toList());
    }

    private AssignmentInProgressResponse toResponse(
            AsAssignment a,
            Map<Long, EngineerProfile> profileMap,
            Map<Long, List<AsStatusLog>> logMap) {

        var req = a.getAsRequest();
        var engineer = a.getEngineer();
        var appliance = req.getAppliance();
        EngineerProfile profile = profileMap.get(engineer.getId());

        List<AsStatusLog> logs = logMap.getOrDefault(req.getId(), List.of());

        // 가장 최근 로그의 to_status
        String latestLogStatus = logs.isEmpty() ? null : logs.get(0).getToStatus();

        // 단계별 최초 도달 시각 매핑 (HH:mm)
        Map<String, String> stepTimes = new LinkedHashMap<>();
        for (String key : STEP_KEYS) {
            stepTimes.put(key, null);
        }
        // 수락 시각은 as_assignments.acceptedAt 기반
        if (a.getAcceptedAt() != null) {
            stepTimes.put("ACCEPTED", a.getAcceptedAt().toLocalTime().toString().substring(0, 5));
        }
        // 나머지 단계는 로그 역순(과거→최신)에서 최초 등장 시각 사용
        List<AsStatusLog> logsAsc = new ArrayList<>(logs);
        Collections.reverse(logsAsc);
        for (AsStatusLog log : logsAsc) {
            String key = log.getToStatus();
            if (STEP_KEYS.contains(key)) {
                stepTimes.put(key, log.getCreatedAt().toLocalTime().toString().substring(0, 5));
            }
        }

        // 로그 항목 변환 (최신순)
        List<AssignmentInProgressResponse.StatusLogEntry> logEntries = logs.stream()
                .map(l -> new AssignmentInProgressResponse.StatusLogEntry(
                        l.getToStatus(), l.getMemo(), l.getCreatedAt()))
                .collect(Collectors.toList());

        return new AssignmentInProgressResponse(
                a.getId(),
                req.getId(),
                a.getAssignMethod().name(),

                req.getCustomer().getId(),
                req.getCustomer().getName(),
                req.getCustomer().getPhone(),

                appliance.getBrand() + " " + appliance.getModelName(),
                appliance.getModelName(),

                engineer.getId(),
                engineer.getName(),
                engineer.getPhone(),
                profile != null ? profile.getAvgRating().doubleValue() : null,
                profile != null ? profile.getProfileImageUrl() : null,

                a.getStatus(),
                latestLogStatus,
                req.getUpdatedAt(),

                req.getScheduledDate().toString(),
                req.getScheduledTime(),
                req.getVisitAddressDetail(),

                logEntries,
                stepTimes
        );
    }
}
