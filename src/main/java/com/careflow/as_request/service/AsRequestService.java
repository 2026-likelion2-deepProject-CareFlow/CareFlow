package com.careflow.as_request.service;

import com.careflow.appliance.entity.Appliance;
import com.careflow.appliance.repository.ApplianceRepository;
import com.careflow.as_request.dto.AsRequestCreateDto;
import com.careflow.as_request.dto.AsRequestCreateResponseDto;
import com.careflow.as_request.dto.AsRequestResponseDto;
import com.careflow.assignment.dto.MatchReason;
import com.careflow.as_request.entity.AsRequest;
import com.careflow.as_request.repository.AsRequestRepository;
import com.careflow.assignment.entity.AsAssignment;
import com.careflow.assignment.repository.AsAssignmentRepository;
import com.careflow.common.enums.AssignType;
import com.careflow.common.enums.Role;
import com.careflow.engineer.domain.entity.EngineerProfile;
import com.careflow.engineer.domain.enums.ScheduleStatus;
import com.careflow.engineer.repository.EngineerProfileRepository;
import com.careflow.region.entity.Regions;
import com.careflow.region.repository.RegionRepository;
import com.careflow.symptom.entity.Symptom;
import com.careflow.symptom.repository.SymptomRepository;
import com.careflow.user.entity.User;
import com.careflow.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalTime;
import java.time.format.DateTimeParseException;
import java.util.Comparator;
import java.util.List;
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

    public List<AsRequestResponseDto> getMyAsRequests(Long customerId) {
        return asRequestRepository.findByCustomer_IdOrderByIdDesc(customerId)
                .stream()
                .map(AsRequestResponseDto::new)
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
}
