package com.careflow.assignment.service;

import com.careflow.as_status_log.entity.AsStatusLog;
import com.careflow.as_status_log.repository.AsStatusLogRepository;
import com.careflow.assignment.dto.AssignmentInProgressPageResponse;
import com.careflow.assignment.dto.AssignmentInProgressResponse;
import com.careflow.assignment.dto.AssignmentInProgressStats;
import com.careflow.assignment.entity.AsAssignment;
import com.careflow.assignment.repository.AsAssignmentRepository;
import com.careflow.auth.security.CustomUserDetails;
import com.careflow.engineer.domain.entity.EngineerProfile;
import com.careflow.engineer.repository.EngineerProfileRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
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

    // 이동 중 상태 — stats.movingCount 산정 기준
    private static final Set<String> MOVING_STATUSES = Set.of("ENGINEER_DEPARTED", "ENGINEER_ARRIVED");

    @Transactional(readOnly = true)
    public AssignmentInProgressPageResponse getInProgress(
            CustomUserDetails userDetails,
            LocalDate date,
            Long regionId,
            String latestLogStatus,
            String brand,
            Long engineerId,
            String keyword,
            int page,
            int size) throws IllegalAccessException {

        // 역할 검증 — AGENCY만 접근 가능
        if (!"AGENCY".equals(userDetails.getRole())) {
            throw new IllegalAccessException("대행사 관리자만 접근할 수 있습니다.");
        }

        Long agencyId = userDetails.getAgencyId();

        // DB 레벨 필터(date·regionId·brand·engineerId) 적용 후 ACCEPTED 배정 조회
        List<AsAssignment> assignments =
                asAssignmentRepository.findInProgressWithFilter(agencyId, date, regionId, brand, engineerId);

        // 배정 없음 — 리포지토리 추가 조회 없이 빈 페이지 반환
        if (assignments.isEmpty()) {
            int completedCount = asAssignmentRepository.countCompletedByAgencyId(agencyId);
            AssignmentInProgressStats emptyStats = new AssignmentInProgressStats(0, 0, 0, completedCount);
            return new AssignmentInProgressPageResponse(emptyStats, List.of(), 0L, 0, page, size);
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

        // as_status_logs 배치 조회 (N+1 방지) — requestIds 기반으로 직접 조회 후 request_id별 그룹화(최신순)
        List<Long> requestIds = assignments.stream()
                .map(a -> a.getAsRequest().getId())
                .collect(Collectors.toList());
        Map<Long, List<AsStatusLog>> logMap = asStatusLogRepository
                .findByRequestIdsOrderByCreatedAtDesc(requestIds)
                .stream()
                .collect(Collectors.groupingBy(l -> l.getAsRequest().getId()));

        // 응답 DTO 변환
        List<AssignmentInProgressResponse> responses = assignments.stream()
                .map(a -> toResponse(a, profileMap, logMap))
                .collect(Collectors.toList());

        // stats — DB 필터 적용 후 전체 목록 기준 집계 (latestLogStatus·keyword 전 단계)
        AssignmentInProgressStats stats = buildStats(responses, agencyId);

        // latestLogStatus 인메모리 필터
        if (latestLogStatus != null && !latestLogStatus.isBlank()) {
            responses = responses.stream()
                    .filter(r -> latestLogStatus.equals(r.latestLogStatus()))
                    .collect(Collectors.toList());
        }

        // keyword 인메모리 필터 — assignmentId·customerName·productName 에서 부분 일치
        if (keyword != null && !keyword.isBlank()) {
            String kw = keyword.trim().toLowerCase();
            responses = responses.stream()
                    .filter(r -> String.valueOf(r.assignmentId()).contains(kw)
                            || (r.customerName() != null && r.customerName().toLowerCase().contains(kw))
                            || (r.productName() != null && r.productName().toLowerCase().contains(kw)))
                    .collect(Collectors.toList());
        }

        // 인메모리 페이지네이션
        long totalElements = responses.size();
        int totalPages = (int) Math.ceil((double) totalElements / size);
        int fromIdx = page * size;
        int toIdx = (int) Math.min(fromIdx + size, totalElements);

        List<AssignmentInProgressResponse> pageContent =
                (fromIdx >= totalElements) ? List.of() : responses.subList(fromIdx, toIdx);

        return new AssignmentInProgressPageResponse(stats, pageContent, totalElements, totalPages, page, size);
    }

    /**
     * stats 블록 계산 — DB 필터 적용 후 전체 응답 리스트와 COMPLETED 건수 기반
     */
    private AssignmentInProgressStats buildStats(List<AssignmentInProgressResponse> all, Long agencyId) {
        int totalCount = all.size();
        int movingCount = (int) all.stream()
                .filter(r -> r.latestLogStatus() != null && MOVING_STATUSES.contains(r.latestLogStatus()))
                .count();
        int inProgressCount = (int) all.stream()
                .filter(r -> "IN_PROGRESS".equals(r.latestLogStatus()))
                .count();
        int completedCount = asAssignmentRepository.countCompletedByAgencyId(agencyId);

        return new AssignmentInProgressStats(totalCount, movingCount, inProgressCount, completedCount);
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
        String latestStatus = logs.isEmpty() ? null : logs.get(0).getToStatus();

        // 단계별 최초 도달 시각 매핑 (HH:mm)
        Map<String, String> stepTimes = new LinkedHashMap<>();
        for (String key : STEP_KEYS) {
            stepTimes.put(key, null);
        }
        // 수락 시각은 as_assignments.acceptedAt 기반
        if (a.getAcceptedAt() != null) {
            stepTimes.put("ACCEPTED", a.getAcceptedAt().toLocalTime().toString().substring(0, 5));
        }
        // 나머지 단계는 로그 오름차순에서 최초 등장 시각 사용
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
                req.getCreatedAt(),
                a.getAssignMethod().name(),

                req.getCustomer().getId(),
                req.getCustomer().getName(),
                req.getCustomer().getPhone(),

                appliance.getBrand() + " " + appliance.getModelName(),
                appliance.getSerialNumber(),

                engineer.getId(),
                engineer.getName(),
                engineer.getPhone(),
                profile != null ? profile.getAvgRating().doubleValue() : null,
                profile != null ? profile.getProfileImageUrl() : null,

                a.getStatus(),
                latestStatus,
                req.getUpdatedAt(),

                req.getScheduledDate().toString(),
                req.getScheduledTime(),
                req.getVisitAddressDetail(),

                logEntries,
                stepTimes
        );
    }
}
