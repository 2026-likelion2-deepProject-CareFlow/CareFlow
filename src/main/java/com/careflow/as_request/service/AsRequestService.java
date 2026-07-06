package com.careflow.as_request.service;

import com.careflow.appliance.entity.Appliance;
import com.careflow.appliance.repository.ApplianceRepository;
import com.careflow.as_request.dto.AsRequestCreateDto;
import com.careflow.as_request.dto.AsRequestCreateResponseDto;
import com.careflow.as_request.dto.AsRequestResponseDto;
import com.careflow.as_request.dto.CustomerAsRequestDetailResponse;
import com.careflow.as_status_log.entity.AsStatusLog;
import com.careflow.as_status_log.repository.AsStatusLogRepository;
import com.careflow.assignment.dto.MatchReason;
import com.careflow.as_request.entity.AsRequest;
import com.careflow.as_request.repository.AsRequestRepository;
import com.careflow.assignment.entity.AsAssignment;
import com.careflow.assignment.repository.AsAssignmentRepository;
import com.careflow.common.enums.AsStatus;
import com.careflow.common.enums.AssignType;
import com.careflow.common.enums.Role;
import com.careflow.engineer.domain.entity.EngineerProfile;
import com.careflow.common.enums.ScheduleStatus;
import com.careflow.engineer.repository.EngineerProfileRepository;
import com.careflow.notification.entity.Notification;
import com.careflow.notification.repository.NotificationRepository;
import com.careflow.notification.service.NotificationService;
import com.careflow.region.entity.Regions;
import com.careflow.region.repository.RegionRepository;
import com.careflow.symptom.entity.Symptom;
import com.careflow.symptom.repository.SymptomRepository;
import com.careflow.user.entity.User;
import com.careflow.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.context.ApplicationEventPublisher;
import com.careflow.notification.event.AsStatusNotificationEvent;


import java.math.BigDecimal;
import java.time.LocalTime;
import java.time.format.DateTimeParseException;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AsRequestService {

    private final AsRequestRepository asRequestRepository;
    private final AsAssignmentRepository asAssignmentRepository;
    private final UserRepository userRepository;
    private final ApplianceRepository applianceRepository;
    private final RegionRepository regionRepository;
    private final SymptomRepository symptomRepository;
    private final EngineerProfileRepository engineerProfileRepository;
    private final AsStatusLogRepository asStatusLogRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final NotificationRepository notificationRepository;

    /**
     * 고객 A/S 접수 및 수리 기사 배정
     * - AUTO: 조건 기반 자동 배정 (fallback 단계적 완화 포함)
     * - MANUAL: 고객이 직접 지정한 기사에게 배정
     * 하나의 트랜잭션으로 처리되며, 도중 실패 시 전체 롤백
     */
    @Transactional
    public AsRequestCreateResponseDto createAsRequest(Long customerId, AsRequestCreateDto dto) {

        // 1. 연관 엔티티 조회
        User customer = userRepository.findById(customerId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 사용자입니다."));

        Appliance appliance = applianceRepository.findById(dto.getApplianceId())
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 가전제품입니다."));

        // v5 스키마: symptom_code VARCHAR → symptoms FK(symptom_id)로 변경
        Symptom symptom = symptomRepository.findById(dto.getSymptomId())
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 증상 코드입니다."));

        Regions visitRegion = regionRepository.findById(dto.getVisitRegionId())
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 방문 지역입니다."));

        // 2. AsRequest 생성 및 저장
        AsRequest asRequest = AsRequest.builder()
                .customer(customer)
                .appliance(appliance)
                .symptom(symptom)
                .symptomDesc(dto.getSymptomDesc())
                .imageUrls(dto.getImageUrls())
                .visitRegion(visitRegion)
                .visitAddressDetail(dto.getVisitAddressDetail())
                .scheduledDate(dto.getScheduledDate())
                .scheduledTime(dto.getScheduledTime())
                .build();

        // save() 반환값을 사용해야 JPA 가 채워준 PK(id) 를 얻을 수 있음
        AsRequest persistedRequest = asRequestRepository.save(asRequest);

        // A/S 요청 성공 후 고객에게 A/S 요청 성공 알림 발송
        String title = "A/S 접수 반려 안내";
        String body = "A/S 요청이 성공적으로 접수되었습니다. 수리 기사님이 요청을 수락하면 수리 과정이 시작됩니다.";

        Notification notification = Notification.createAsStatusNotification(asRequest.getCustomer(), title, body);
        notificationRepository.save(notification);

        // 3. assignType 에 따라 배정 로직 분기
        if (dto.getAssignType() == AssignType.AUTO) {
            AutoResult result = processAutoAssignment(persistedRequest, appliance, dto);
            return new AsRequestCreateResponseDto(
                    persistedRequest.getId(), result.assignment().getId(), result.matchReason());
        } else {
            AsAssignment assignment = processManualAssignment(persistedRequest, dto);
            // MANUAL 배차는 고객이 직접 기사를 선택하므로 matchReason 없음
            return new AsRequestCreateResponseDto(persistedRequest.getId(), assignment.getId(), null);
        }
    }

    /**
     * AUTO 배정 로직
     * 4가지 조건을 순차적으로 완화하며 기사를 탐색, 없으면 예외 반환.
     * 매칭 성사 시 어떤 Fallback 조건으로 선택됐는지 사유를 함께 반환한다.
     */
    private AutoResult processAutoAssignment(AsRequest asRequest, Appliance appliance,
                                             AsRequestCreateDto dto) {
        LocalTime workTime = parseScheduledTime(dto.getScheduledTime());
        Integer categoryId = appliance.getCategory().getCategoryId();
        String brand = appliance.getBrand();
        Integer regionId = dto.getVisitRegionId();

        // Fallback 0: 스케줄 + 브랜드 + 카테고리 + 서비스 지역
        List<EngineerProfile> candidates = engineerProfileRepository.findByAllConditions(
                dto.getScheduledDate(), workTime, ScheduleStatus.AVAILABLE,
                brand, categoryId, regionId);
        String matchReason = MatchReason.FALLBACK_0;

        // Fallback 1: 브랜드 조건 완화
        if (candidates.isEmpty()) {
            candidates = engineerProfileRepository.findWithoutBrand(
                    dto.getScheduledDate(), workTime, ScheduleStatus.AVAILABLE,
                    categoryId, regionId);
            matchReason = MatchReason.FALLBACK_1;
        }

        // Fallback 2: 브랜드 + 서비스 지역 조건 완화
        if (candidates.isEmpty()) {
            candidates = engineerProfileRepository.findWithoutBrandAndRegion(
                    dto.getScheduledDate(), workTime, ScheduleStatus.AVAILABLE, categoryId);
            matchReason = MatchReason.FALLBACK_2;
        }

        // Fallback 3: 브랜드 + 지역 + 카테고리 조건 완화 (스케줄 조건만)
        if (candidates.isEmpty()) {
            candidates = engineerProfileRepository.findByScheduleOnly(
                    dto.getScheduledDate(), workTime, ScheduleStatus.AVAILABLE);
            matchReason = MatchReason.FALLBACK_3;
        }

        // Fallback 4: 모든 조건 완화 후에도 기사 없음 → 일정 재협의 안내
        if (candidates.isEmpty()) {
            throw new IllegalStateException(
                    "요청하신 날짜/시간(" + dto.getScheduledDate() + " " + dto.getScheduledTime() +
                    ")에 가용한 수리 기사가 없습니다. 다른 일정으로 재신청하거나 수동 배정을 이용해주세요.");
        }

        // 부하 반영 복합 점수로 최적 기사 선택 (Option B)
        // score = avgRating × 0.7 - 대기중_배차수 × 0.3
        // → 평점이 높아도 대기 중인 일이 많으면 우선순위에서 밀려 작업 집중 완화
        EngineerProfile selected = selectByCompositeScore(candidates);
        User engineer = selected.getUser();

        return new AutoResult(createAssignment(asRequest, engineer, dto.getAssignType()), matchReason);
    }

    /** 자동 배차 결과 — 생성된 배차 엔티티 + 매칭 성사 사유를 묶어 반환하기 위한 내부 레코드 */
    private record AutoResult(AsAssignment assignment, String matchReason) {}

    /**
     * MANUAL 배정 로직
     * 고객이 직접 지정한 기사(preferredEngineerId)에게 배정
     */
    private AsAssignment processManualAssignment(AsRequest asRequest, AsRequestCreateDto dto) {
        if (dto.getPreferredEngineerId() == null) {
            throw new IllegalArgumentException("수동 배정 시 수리 기사 선택은 필수입니다.");
        }

        User engineer = userRepository.findById(dto.getPreferredEngineerId())
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 수리 기사입니다."));

        // role = ENGINEER 여부 검증
        if (engineer.getRole() != Role.ENGINEER) {
            throw new IllegalArgumentException("선택한 사용자는 수리 기사가 아닙니다.");
        }

        return createAssignment(asRequest, engineer, AssignType.MANUAL);
    }

    /**
     * AsAssignment 생성 및 AsRequest 상태 ASSIGNED 전환 공통 처리
     * v5 스키마: assigned_by 컬럼 삭제 → AsAssignment.create() 파라미터에서 제거
     */
    private AsAssignment createAssignment(AsRequest asRequest, User engineer, AssignType assignType) {
        if (engineer.getAgency() == null) {
            throw new IllegalStateException("해당 수리 기사는 소속 대행사 정보가 없습니다.");
        }

        // AsRequest 상태를 ASSIGNED 로 전환 및 agency 설정
        asRequest.processAssignment(engineer.getAgency());

        AsAssignment assignment = AsAssignment.create(
                asRequest,
                engineer,
                engineer.getAgency(),
                assignType
        );

        return asAssignmentRepository.save(assignment);
    }

    /**
     * 고객용: A/S 요청 단건 상세 조회
     * 1. appliance 포함 JOIN FETCH 단건 조회 (findDetailById 재사용)
     * 2. 본인 소유 검증
     * 3. REJECTED가 아닌 배정에서 기사 정보 추출 (없으면 null — 아직 배정 전 상태)
     */
    @Transactional(readOnly = true)
    public CustomerAsRequestDetailResponse getMyAsRequestDetail(Long customerId, Long requestId)
            throws IllegalAccessException {

        AsRequest asRequest = asRequestRepository.findDetailById(requestId)
                .orElseThrow(() -> new NoSuchElementException("존재하지 않는 A/S 요청입니다."));

        // 본인 요청인지 검증
        if (!asRequest.getCustomer().getId().equals(customerId)) {
            throw new IllegalAccessException("본인의 A/S 요청만 조회할 수 있습니다.");
        }

        // 배정된 기사 조회 — REJECTED가 아닌 첫 번째 배정의 기사 반환 (없으면 null)
        User engineer = asAssignmentRepository.findByAsRequest_Id(requestId).stream()
                .filter(a -> !a.getStatus().equals("REJECTED"))
                .findFirst()
                .map(AsAssignment::getEngineer)
                .orElse(null);

        return CustomerAsRequestDetailResponse.from(asRequest, engineer);
    }

    @Transactional(readOnly = true)
    public List<AsRequestResponseDto> getMyAsRequests(Long customerId) {
        List<AsRequest> requests = asRequestRepository.findByCustomer_IdOrderByIdDesc(customerId);
        if (requests.isEmpty()) {
            return List.of();
        }

        List<Long> requestIds = requests.stream().map(AsRequest::getId).collect(Collectors.toList());

        // requestId → 유효한(REJECTED 아닌) 배정 중 가장 최근 배정 1건 매핑 — 목록에 기사 이름/평점을 붙이기 위함
        Map<Long, AsAssignment> latestAssignmentByRequestId = asAssignmentRepository
                .findByAsRequest_IdInWithEngineer(requestIds).stream()
                .filter(a -> !"REJECTED".equals(a.getStatus()))
                .collect(Collectors.toMap(
                        a -> a.getAsRequest().getId(),
                        a -> a,
                        (a1, a2) -> a1.getAssignedAt().isAfter(a2.getAssignedAt()) ? a1 : a2));

        List<Long> engineerIds = latestAssignmentByRequestId.values().stream()
                .map(a -> a.getEngineer().getId())
                .distinct()
                .collect(Collectors.toList());

        Map<Long, BigDecimal> ratingByEngineerId = engineerProfileRepository.findByUser_IdIn(engineerIds).stream()
                .collect(Collectors.toMap(ep -> ep.getUser().getId(), EngineerProfile::getAvgRating));

        return requests.stream()
                .map(r -> {
                    AsAssignment assignment = latestAssignmentByRequestId.get(r.getId());
                    if (assignment == null) {
                        return new AsRequestResponseDto(r);
                    }
                    String engineerName = assignment.getEngineer().getName();
                    BigDecimal engineerRating = ratingByEngineerId.get(assignment.getEngineer().getId());
                    return new AsRequestResponseDto(r, engineerName, engineerRating);
                })
                .collect(Collectors.toList());
    }

    @Transactional
    public void cancelAsRequest(Long customerId, Long asRequestId, String cancelReason) {
        AsRequest asRequest = asRequestRepository.findById(asRequestId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 A/S 요청입니다."));

        // SecurityException 은 GlobalExceptionHandler 에 미등록 → IllegalStateException(→ 403) 으로 통일
        if (!asRequest.getCustomer().getId().equals(customerId)) {
            throw new IllegalStateException("본인의 A/S 요청만 취소할 수 있습니다.");
        }

        asRequest.cancel(cancelReason);

        // ASSIGNED 상태에서 취소된 경우 기사가 아직 수락하지 않은(WAITING) 배정이 남아있으므로 함께 취소 처리
        // (그대로 두면 이미 취소된 요청의 배정을 기사가 수락/거절할 수 있는 상태로 남게 됨)
        asAssignmentRepository.findByAsRequest_Id(asRequestId).stream()
                .filter(a -> "WAITING".equals(a.getStatus()))
                .forEach(AsAssignment::cancel);
    }

    /**
     * 부하 반영 복합 점수(Option B) 기반 최적 기사 선택
     *
     * score = avgRating × RATING_WEIGHT - pendingAssignments × LOAD_WEIGHT
     *
     * - avgRating(0.0~5.0)이 높을수록 유리하지만,
     *   대기 중(WAITING) 배차 수가 많을수록 불리하게 반영해 작업 집중을 완화
     */
    private static final double RATING_WEIGHT = 0.7;
    private static final double LOAD_WEIGHT   = 0.3;

    private EngineerProfile selectByCompositeScore(List<EngineerProfile> candidates) {
        return candidates.stream()
                .max(Comparator.comparingDouble(profile -> {
                    double avgRating = profile.getAvgRating() != null
                            ? profile.getAvgRating().doubleValue()
                            : 0.0;
                    long pendingCount = asAssignmentRepository
                            .countByEngineer_IdAndStatus(profile.getUser().getId(), "WAITING");
                    return avgRating * RATING_WEIGHT - pendingCount * LOAD_WEIGHT;
                }))
                // candidates 가 비어있지 않음은 호출 전에 보장됨
                .orElseThrow(() -> new IllegalStateException("배정 가능한 기사 선택에 실패했습니다."));
    }

    /** "HH:MM" 형식의 문자열을 LocalTime 으로 변환 */
    private LocalTime parseScheduledTime(String scheduledTime) {
        try {
            return LocalTime.parse(scheduledTime);
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException("방문 예약 시간 형식이 올바르지 않습니다. (올바른 형식: HH:MM)");
        }
    }

    /**
     * 기사의 작업 수행 상태 변경 (출발 -> 도착 -> 시작)
     */
    @Transactional
    public void updateEngineerTaskStatus(Long engineerId, Long requestId, AsStatus newStatus) {
        AsRequest asRequest = asRequestRepository.findById(requestId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 A/S 요청입니다."));

        // 1. 해당 기사 본인에게 배정된 건이며 수락(ACCEPTED) 이상의 상태인지 철저한 검증
        boolean isMyTask = asAssignmentRepository.findByAsRequest_Id(requestId).stream()
                .anyMatch(a -> a.getEngineer().getId().equals(engineerId) &&
                        !a.getStatus().equals("WAITING") && !a.getStatus().equals("REJECTED"));

        if (!isMyTask) {
            throw new IllegalStateException("본인에게 배정되어 진행 중인 작업만 상태를 변경할 수 있습니다.");
        }

        User engineer = userRepository.findById(engineerId).orElseThrow();

        // 해당 요청의 가장 최근 "세부 진행상태"를 as_status_logs 에서 조회 (없으면 기본값 WAITING)
        // 세부 진행상태(ENGINEER_DEPARTED/ENGINEER_ARRIVED)는 as_requests.status ENUM 에 존재하지 않으므로,
        // 순서 검증은 반드시 로그(to_status)를 유일한 기준으로 삼는다.
        List<AsStatusLog> logs = asStatusLogRepository.findByAsRequest_IdOrderByCreatedAtAsc(requestId);
        String lastLogStatus = logs.isEmpty() ? "WAITING" : logs.get(logs.size() - 1).getToStatus();

        // 2. 세부 진행상태 순서 검증 (로그 기준)
        validateProgressOrder(lastLogStatus, newStatus);

        // 3. 도메인 메서드로 상태 변경 (더티 체킹)
        //    - 출발/도착: as_requests.status 는 ACCEPTED 로 유지, 로그로만 기록
        //    - 작업시작: as_requests.status 를 IN_PROGRESS 로 전환
        String actionMemo;
        switch (newStatus) {
            case ENGINEER_DEPARTED -> {
                asRequest.depart();
                actionMemo = engineer.getName() + " 기사님이 고객님 댁으로 출발했습니다.";
            }
            case ENGINEER_ARRIVED -> {
                asRequest.arrive();
                actionMemo = engineer.getName() + " 기사님이 현장에 도착했습니다.";
            }
            case IN_PROGRESS -> {
                asRequest.startWork();
                actionMemo = engineer.getName() + " 기사님이 수리 작업을 시작했습니다.";
            }
            default -> throw new IllegalArgumentException("해당 API로는 출발/도착/작업시작 상태만 변경 가능합니다.");
        }

        // 🌟 4. 상태 변경 로그(AsStatusLog) 기록 (세부 진행상태의 진실의 원천)
        AsStatusLog statusLog = AsStatusLog.builder()
                .asRequest(asRequest)
                .changedBy(engineer)
                .fromStatus(lastLogStatus)
                .toStatus(newStatus.name())
                .memo(actionMemo)
                .build();
        asStatusLogRepository.save(statusLog);

        // 🌟 4. SSE 실시간 알림 발송 (고객 & 대행사)
        sendRealTimeNotification(asRequest, actionMemo);
    }

    /**
     * 알림 발송 공통 헬퍼 메서드
     */
    private void sendRealTimeNotification(AsRequest asRequest, String message) {
        String title = "A/S 진행 상태 업데이트";
        String applianceInfo = asRequest.getAppliance().getBrand() + " " + asRequest.getAppliance().getModelName();
        String body = "[" + applianceInfo + "] " + message;

        // 직접 전송이 아닌 "이벤트 발행"
        eventPublisher.publishEvent(new AsStatusNotificationEvent(asRequest.getCustomer(), title, body));

        if (asRequest.getAgency() != null && asRequest.getAgency().getRepresentativeId() != null) {
            eventPublisher.publishEvent(new AsStatusNotificationEvent(asRequest.getAgency().getRepresentativeId(), title, body));
        }
    }

    /**
     * 세부 진행상태(출발/도착/작업시작) 순서를 as_status_logs 최신 상태를 기준으로 검증한다.
     * as_requests.status 에는 ENGINEER_DEPARTED/ENGINEER_ARRIVED 가 저장되지 않으므로,
     * 이 순서 검증은 로그(to_status)를 유일한 기준으로 삼는다.
     */
    private void validateProgressOrder(String lastLogStatus, AsStatus target) {
        switch (target) {
            case ENGINEER_DEPARTED -> {
                if (isDepartedOrLater(lastLogStatus)) {
                    throw new IllegalStateException("이미 출발 이후 단계입니다. (현재 진행상태: " + lastLogStatus + ")");
                }
            }
            case ENGINEER_ARRIVED -> {
                if (!AsStatus.ENGINEER_DEPARTED.name().equals(lastLogStatus)) {
                    throw new IllegalStateException("출발 처리 후에만 도착할 수 있습니다. (현재 진행상태: " + lastLogStatus + ")");
                }
            }
            case IN_PROGRESS -> {
                if (!AsStatus.ENGINEER_ARRIVED.name().equals(lastLogStatus)) {
                    throw new IllegalStateException("도착 처리 후에만 작업을 시작할 수 있습니다. (현재 진행상태: " + lastLogStatus + ")");
                }
            }
            default -> { /* 그 외 상태는 이 API 범위 밖 */ }
        }
    }

    /** 로그상 이미 '출발' 이후 단계인지 여부 (중복 출발/역행 방지용) */
    private boolean isDepartedOrLater(String logStatus) {
        return AsStatus.ENGINEER_DEPARTED.name().equals(logStatus)
                || AsStatus.ENGINEER_ARRIVED.name().equals(logStatus)
                || AsStatus.IN_PROGRESS.name().equals(logStatus)
                || AsStatus.COMPLETED.name().equals(logStatus)
                || AsStatus.PAID.name().equals(logStatus);
    }
}
