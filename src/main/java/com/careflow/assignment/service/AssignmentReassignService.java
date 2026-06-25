package com.careflow.assignment.service;

import com.careflow.appliance.entity.Appliance;
import com.careflow.as_request.entity.AsRequest;
import com.careflow.assignment.dto.AssignmentReassignResponse;
import com.careflow.assignment.dto.MatchReason;
import com.careflow.assignment.entity.AsAssignment;
import com.careflow.assignment.repository.AsAssignmentRepository;
import com.careflow.auth.security.CustomUserDetails;
import com.careflow.common.enums.AssignType;
import com.careflow.common.enums.Role;
import com.careflow.engineer.domain.entity.EngineerProfile;
import com.careflow.engineer.domain.enums.ScheduleStatus;
import com.careflow.engineer.repository.EngineerProfileRepository;
import com.careflow.notification.entity.Notification;
import com.careflow.notification.repository.NotificationRepository;
import com.careflow.user.entity.User;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalTime;
import java.time.format.DateTimeParseException;
import java.util.Comparator;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class AssignmentReassignService {

    private static final double RATING_WEIGHT = 0.7;
    private static final double LOAD_WEIGHT   = 0.3;

    private final AsAssignmentRepository     asAssignmentRepository;
    private final EngineerProfileRepository  engineerProfileRepository;
    private final NotificationRepository     notificationRepository;

    /**
     * 재배차 처리 진입점.
     *
     * 처리 흐름:
     * 1. role != AGENCY → 401
     * 2. assignmentId 조회 → 없으면 404
     * 3. as_request.assign_type 확인
     *    - AUTO  → 이전 배차 기사 제외 후 자동 재배차
     *    - MANUAL → 고객에게 재신청 알림 발송, 재배차 없음
     */
    @Transactional
    public AssignmentReassignResponse reassign(Long assignmentId,
                                               CustomUserDetails userDetails) throws IllegalAccessException {
        // 1. 권한 검증
        if (!Role.AGENCY.name().equals(userDetails.getRole())) {
            throw new IllegalAccessException("대행사 관리자 권한이 없습니다.");
        }

        // 2. 배차 조회 — asRequest, engineer, agency LAZY 로딩 필요
        AsAssignment targetAssignment = asAssignmentRepository.findDetailById(assignmentId)
                .orElseThrow(() -> new NoSuchElementException("해당 배차 내역을 찾을 수 없습니다."));

        AsRequest asRequest = targetAssignment.getAsRequest();

        // 3. assign_type 에 따라 분기
        if (asRequest.getAssignType() == AssignType.AUTO) {
            // 재배차 제외 대상: 재배차 요청된 그 배차를 받은 기사 1명만 제외
            Long excludeEngineerId = targetAssignment.getEngineer().getId();
            return processAutoReassignment(asRequest, excludeEngineerId);
        } else {
            return processManualFailureNotification(asRequest);
        }
    }

    /**
     * AUTO 재배차.
     *
     * 1. 재배차 요청 대상 배차(`assignmentId`)를 받은 기사 1명만 제외
     *    - 해당 건에서 30분 무응답이거나 거부한 기사만 빠짐
     *    - 동일 A/S 요청의 다른 배차 이력 기사는 제외 대상이 아님
     * 2. 기존 4단계 Fallback 로직 재현 — 단, 해당 기사만 제외
     * 3. 복합 점수(평점 × 0.7 - 대기중 배차수 × 0.3)로 최적 기사 선택
     * 4. 새 AsAssignment 생성 (기존 배차는 이력으로 보존)
     */
    private AssignmentReassignResponse processAutoReassignment(AsRequest asRequest,
                                                               Long excludeEngineerId) {
        // 재배차 요청 대상 배차를 받은 기사 1명만 제외 대상으로 설정
        Set<Long> excludeEngineerIds = Set.of(excludeEngineerId);

        Appliance appliance = asRequest.getAppliance();
        Integer categoryId  = appliance.getCategory().getCategoryId();
        String  brand       = appliance.getBrand();
        Integer regionId    = asRequest.getVisitRegion().getId();
        LocalTime workTime  = parseScheduledTime(asRequest.getScheduledTime());

        // 2. Fallback 탐색 — 이전 배차 기사 제외 버전 메서드 사용
        List<EngineerProfile> candidates = engineerProfileRepository.findByAllConditionsExcluding(
                asRequest.getScheduledDate(), workTime, ScheduleStatus.AVAILABLE,
                brand, categoryId, regionId, excludeEngineerIds);
        String matchReason = MatchReason.FALLBACK_0;

        if (candidates.isEmpty()) {
            candidates = engineerProfileRepository.findWithoutBrandExcluding(
                    asRequest.getScheduledDate(), workTime, ScheduleStatus.AVAILABLE,
                    categoryId, regionId, excludeEngineerIds);
            matchReason = MatchReason.FALLBACK_1;
        }

        if (candidates.isEmpty()) {
            candidates = engineerProfileRepository.findWithoutBrandAndRegionExcluding(
                    asRequest.getScheduledDate(), workTime, ScheduleStatus.AVAILABLE,
                    categoryId, excludeEngineerIds);
            matchReason = MatchReason.FALLBACK_2;
        }

        if (candidates.isEmpty()) {
            candidates = engineerProfileRepository.findByScheduleOnlyExcluding(
                    asRequest.getScheduledDate(), workTime, ScheduleStatus.AVAILABLE,
                    excludeEngineerIds);
            matchReason = MatchReason.FALLBACK_3;
        }

        if (candidates.isEmpty()) {
            throw new IllegalStateException(
                    "재배차 가능한 수리 기사가 없습니다. 요청 일정(" +
                    asRequest.getScheduledDate() + " " + asRequest.getScheduledTime() +
                    ")을 변경하거나 수동 배정을 이용해주세요.");
        }

        // 3. 복합 점수로 최적 기사 선택
        EngineerProfile selected = selectByCompositeScore(candidates);
        User engineer = selected.getUser();

        if (engineer.getAgency() == null) {
            throw new IllegalStateException("선택된 수리 기사에 소속 대행사 정보가 없습니다.");
        }

        // 4. 새 배차 생성 (기존 배차 레코드는 이력으로 보존, 삭제하지 않음)
        AsAssignment newAssignment = AsAssignment.create(
                asRequest, engineer, engineer.getAgency(), AssignType.AUTO);
        AsAssignment saved = asAssignmentRepository.save(newAssignment);

        return AssignmentReassignResponse.reassigned(saved.getId(), matchReason);
    }

    /**
     * MANUAL 배차 실패 → 고객에게 재신청 알림 발송.
     *
     * 수동 배차로 접수된 A/S 건은 재배차가 불가하므로,
     * 고객에게 "수리 기사를 다시 선택하여 재신청하라"는 알림을 저장한다.
     */
    private AssignmentReassignResponse processManualFailureNotification(AsRequest asRequest) {
        User customer = asRequest.getCustomer();
        String body = String.format(
                "요청하신 A/S 건(요청번호: %d)의 수리 기사 배정이 완료되지 못했습니다. " +
                "수리 기사를 다시 선택하여 A/S를 재신청해 주세요.", asRequest.getId());

        Notification notification = Notification.createAsStatusNotification(
                customer, "A/S 재신청 필요", body);
        Notification saved = notificationRepository.save(notification);

        return AssignmentReassignResponse.manualNotified(saved.getId());
    }

    /** 부하 반영 복합 점수로 최적 기사 선택 (AsRequestService 와 동일한 로직) */
    private EngineerProfile selectByCompositeScore(List<EngineerProfile> candidates) {
        return candidates.stream()
                .max(Comparator.comparingDouble(profile -> {
                    double avgRating = profile.getAvgRating() != null
                            ? profile.getAvgRating().doubleValue() : 0.0;
                    long pendingCount = asAssignmentRepository
                            .countByEngineer_IdAndStatus(profile.getUser().getId(), "WAITING");
                    return avgRating * RATING_WEIGHT - pendingCount * LOAD_WEIGHT;
                }))
                .orElseThrow(() -> new IllegalStateException("배정 가능한 기사 선택에 실패했습니다."));
    }

    /** "HH:MM" 문자열을 LocalTime 으로 변환 */
    private LocalTime parseScheduledTime(String scheduledTime) {
        try {
            return LocalTime.parse(scheduledTime);
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException("방문 예약 시간 형식이 올바르지 않습니다. (올바른 형식: HH:MM)");
        }
    }
}
