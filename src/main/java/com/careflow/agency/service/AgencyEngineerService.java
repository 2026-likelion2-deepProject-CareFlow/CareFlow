package com.careflow.agency.service;

import com.careflow.agency.dto.request.AgencyEngineerProfileUpdateRequest;
import com.careflow.agency.dto.response.AgencyEngineerDetailResponse;
import com.careflow.agency.dto.response.AgencyEngineerSummaryResponse;
import com.careflow.agency.dto.response.EngineerLmsStatusResponse;
import com.careflow.agency.dto.response.EngineerRankResponse;
import com.careflow.agency.dto.response.EngineerRealtimeStatusResponse;
import com.careflow.agency.dto.response.EngineerRecommendResponse;
import com.careflow.agency.dto.response.EngineerReviewListResponse;
import com.careflow.agency.dto.response.EngineerSettlementResponse;
import com.careflow.as_request.dto.EngineerTaskScheduleResponse;
import com.careflow.as_request.entity.AsRequest;
import com.careflow.as_request.repository.AsRequestRepository;
import com.careflow.assignment.dto.EngineerCompletedCount;
import com.careflow.assignment.entity.AsAssignment;
import com.careflow.assignment.repository.AsAssignmentRepository;
import com.careflow.common.enums.AsStatus;
import com.careflow.appliance.entity.ApplianceCategory;
import com.careflow.appliance.repository.ApplianceCategoryRepository;
import com.careflow.engineer.domain.entity.EngineerExpertBrand;
import com.careflow.engineer.domain.entity.EngineerProfile;
import com.careflow.engineer.domain.entity.EngineerSchedule;
import com.careflow.engineer.domain.entity.EngineerServiceRegion;
import com.careflow.common.enums.ScheduleStatus;
import com.careflow.common.enums.SkillLevel;
import com.careflow.engineer.dto.ScheduleResponse;
import com.careflow.engineer.repository.EngineerExpertBrandRepository;
import com.careflow.engineer.repository.EngineerProfileRepository;
import com.careflow.engineer.repository.EngineerScheduleRepository;
import com.careflow.engineer.repository.EngineerServiceRegionRepository;
import com.careflow.lms.entity.LmsConfirmation;
import com.careflow.lms.repository.LmsConfirmationRepository;
import com.careflow.region.entity.Regions;
import com.careflow.region.repository.RegionRepository;
import com.careflow.review.entity.Review;
import com.careflow.review.repository.ReviewRepository;
import com.careflow.settlement.repository.SettlementRepository;
import com.careflow.user.entity.User;
import com.careflow.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.Year;
import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AgencyEngineerService {

    private final UserRepository userRepository;
    private final EngineerProfileRepository engineerProfileRepository;
    private final EngineerExpertBrandRepository expertBrandRepository;
    private final EngineerServiceRegionRepository serviceRegionRepository;
    private final EngineerScheduleRepository engineerScheduleRepository;
    private final ApplianceCategoryRepository categoryRepository;
    private final RegionRepository regionRepository;
    private final AsAssignmentRepository asAssignmentRepository;
    private final AsRequestRepository asRequestRepository;
    private final SettlementRepository settlementRepository;
    private final ReviewRepository reviewRepository;
    private final LmsConfirmationRepository lmsConfirmationRepository;

    /**
     * 소속 기사 목록 조회
     * 로그인한 대행사 관리자의 agencyId 기준으로 소속 기사 전체를 조회한다.
     * 기사별 오늘 날짜 근무 상태(AVAILABLE/BOOKED/OFF)를 함께 반환한다.
     */
    public List<AgencyEngineerSummaryResponse> getAgencyEngineers(Long agencyUserId) {
        // 로그인한 유저의 소속 대행사 조회
        User agencyUser = findUserById(agencyUserId);
        Long agencyId = getAgencyId(agencyUser);

        List<EngineerProfile> profiles = engineerProfileRepository.findByAgencyId(agencyId);

        LocalDate today = LocalDate.now();

        return profiles.stream()
                .map(profile -> {
                    Long engineerUserId = profile.getUser().getId();

                    List<Integer> regionIds = serviceRegionRepository.findByEngineer_Id(engineerUserId).stream()
                            .map(r -> r.getRegion().getId())
                            .toList();

                    // 오늘 근무표가 있으면 해당 상태, 없으면 OFF로 표시
                    String currentStatus = engineerScheduleRepository
                            .findByUser_IdAndWorkDateBetweenOrderByWorkDateAsc(engineerUserId, today, today)
                            .stream()
                            .findFirst()
                            .map(s -> s.getStatus().name())
                            .orElse(ScheduleStatus.OFF.name());

                    return AgencyEngineerSummaryResponse.from(profile, regionIds, currentStatus);
                })
                .toList();
    }

    /**
     * 소속 기사 단건 상세 조회
     * 타 대행사 소속 기사 접근 시 IllegalAccessException 발생
     */
    public AgencyEngineerDetailResponse getAgencyEngineerDetail(Long agencyUserId, Long engineerUserId) throws IllegalAccessException {
        User agencyUser = findUserById(agencyUserId);
        Long agencyId = getAgencyId(agencyUser);

        EngineerProfile profile = engineerProfileRepository.findByUser_Id(engineerUserId)
                .orElseThrow(() -> new NoSuchElementException("해당 기사의 프로필 정보가 존재하지 않습니다."));

        // 소속 대행사 일치 검증
        validateSameAgency(profile, agencyId);

        List<String> expertBrands = expertBrandRepository.findByEngineer_Id(engineerUserId).stream()
                .map(EngineerExpertBrand::getBrandName)
                .toList();

        List<Integer> regionIds = serviceRegionRepository.findByEngineer_Id(engineerUserId).stream()
                .map(r -> r.getRegion().getId())
                .toList();

        return AgencyEngineerDetailResponse.from(profile, expertBrands, regionIds);
    }

    /**
     * 소속 기사 프로필 수정 (대행사 관리자 권한)
     * 수정 가능 항목: 전문 가전 카테고리, 전문 브랜드 목록, 활동 지역 목록, 연락처, 이메일, 경력 시작 연도, 소개
     * null로 전달된 필드는 기존값 유지. 단 expertBrands/serviceRegionIds는 빈 배열이면 전체 삭제로 처리(전체 교체 방식이므로).
     * 기술 등급은 요청으로 받지 않고 경력 시작 연도 기준으로 서버가 자동 재산정한다(기사 본인 수정 플로우와 동일 규칙).
     */
    @Transactional
    public AgencyEngineerDetailResponse updateAgencyEngineerProfile(
            Long agencyUserId, Long engineerUserId, AgencyEngineerProfileUpdateRequest request) throws IllegalAccessException {

        User agencyUser = findUserById(agencyUserId);
        Long agencyId = getAgencyId(agencyUser);

        EngineerProfile profile = engineerProfileRepository.findByUser_Id(engineerUserId)
                .orElseThrow(() -> new NoSuchElementException("해당 기사의 프로필 정보가 존재하지 않습니다."));

        validateSameAgency(profile, agencyId);

        User engineerUser = profile.getUser();

        // 카테고리 수정 — depth=2(소분류)만 허용
        if (request.getCategoryId() != null) {
            ApplianceCategory category = categoryRepository.findById(request.getCategoryId())
                    .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 카테고리입니다. (categoryId=" + request.getCategoryId() + ")"));
            if (category.getDepth() != 2) {
                throw new IllegalArgumentException("전문 분야는 소분류(depth=2) 카테고리만 선택 가능합니다.");
            }
            profile.updateCategory(category);
        }

        // 연락처·이메일 수정 — 이메일은 로그인 식별자이므로 변경 전 중복 검증
        if (request.getPhone() != null || request.getEmail() != null) {
            if (request.getEmail() != null
                    && !request.getEmail().equals(engineerUser.getEmail())
                    && userRepository.existsByEmail(request.getEmail())) {
                throw new IllegalArgumentException("이미 사용 중인 이메일입니다.");
            }
            engineerUser.updateContact(request.getPhone(), request.getEmail());
        }

        // 경력 시작 연도·소개 수정 — 경력 연도가 바뀌면 기술 등급도 함께 재산정
        if (request.getCareerStartedYear() != null || request.getIntroduction() != null) {
            SkillLevel newSkillLevel = profile.getSkillLevel();
            if (request.getCareerStartedYear() != null) {
                int currentYear = LocalDate.now().getYear();
                if (request.getCareerStartedYear() > currentYear) {
                    throw new IllegalArgumentException("경력 시작 연도는 미래일 수 없습니다.");
                }
                newSkillLevel = calculateSkillLevel(request.getCareerStartedYear());
            }
            profile.updateBasicInfo(request.getCareerStartedYear(), newSkillLevel, request.getIntroduction(), null);
        }

        // 전문 브랜드 수정 (전체 교체, 빈 배열이면 전체 삭제)
        List<String> resultBrands = expertBrandRepository.findByEngineer_Id(engineerUserId).stream()
                .map(EngineerExpertBrand::getBrandName).toList();
        if (request.getExpertBrands() != null) {
            resultBrands = saveExpertBrands(engineerUser, request.getExpertBrands());
        }

        // 활동 지역 수정 (전체 교체, depth=2 구 단위만 허용, 빈 배열이면 전체 삭제)
        List<Integer> resultRegionIds = serviceRegionRepository.findByEngineer_Id(engineerUserId).stream()
                .map(r -> r.getRegion().getId()).toList();
        if (request.getServiceRegionIds() != null) {
            resultRegionIds = saveServiceRegions(engineerUser, request.getServiceRegionIds());
        }

        return AgencyEngineerDetailResponse.from(profile, resultBrands, resultRegionIds);
    }

    // 연차별 기술 등급 자동 산정 (1~5년=초급, 6~10년=중급, 11년↑=고급) — EngineerProfileService.calculateSkillLevel과 동일 규칙
    private SkillLevel calculateSkillLevel(Integer careerStartedYear) {
        int workYear = LocalDate.now().getYear() - careerStartedYear;
        if (workYear <= 5) {
            return SkillLevel.BEGINNER;
        } else if (workYear <= 10) {
            return SkillLevel.INTERMEDIATE;
        } else {
            return SkillLevel.ADVANCED;
        }
    }

    /**
     * 소속 기사 월간 근무표 조회
     * year/month 기준 해당 기사의 근무 일정 전체 반환
     */
    public List<ScheduleResponse> getAgencyEngineerSchedules(Long agencyUserId, Long engineerUserId, int year, int month) throws IllegalAccessException {
        User agencyUser = findUserById(agencyUserId);
        Long agencyId = getAgencyId(agencyUser);

        // 대상 기사 존재 여부 및 소속 검증
        User engineerUser = userRepository.findById(engineerUserId)
                .orElseThrow(() -> new NoSuchElementException("해당 기사 정보가 존재하지 않습니다."));

        if (engineerUser.getAgency() == null || !engineerUser.getAgency().getId().equals(agencyId)) {
            throw new IllegalAccessException("소속 대행사의 기사만 조회할 수 있습니다.");
        }

        LocalDate startDate = LocalDate.of(year, month, 1);
        LocalDate endDate = startDate.withDayOfMonth(startDate.lengthOfMonth());

        return engineerScheduleRepository
                .findByUser_IdAndWorkDateBetweenOrderByWorkDateAsc(engineerUserId, startDate, endDate)
                .stream()
                .map(ScheduleResponse::of)
                .toList();
    }

    /**
     * 소속 기사 특정 날짜 A/S 작업 일정 조회
     * - 해당 날짜 배정된 작업(고객·제품·방문주소 포함) 반환
     * - REJECTED 건 제외, 타 대행사 기사 조회 시 IllegalAccessException 발생
     */
    public List<EngineerTaskScheduleResponse> getAgencyEngineerTaskSchedule(
            Long agencyUserId, Long engineerUserId, LocalDate date) throws IllegalAccessException {

        User agencyUser = findUserById(agencyUserId);
        Long agencyId = getAgencyId(agencyUser);

        // 대상 기사 존재 여부 및 소속 검증
        User engineerUser = userRepository.findById(engineerUserId)
                .orElseThrow(() -> new NoSuchElementException("해당 기사 정보가 존재하지 않습니다."));

        if (engineerUser.getAgency() == null || !engineerUser.getAgency().getId().equals(agencyId)) {
            throw new IllegalAccessException("소속 대행사의 기사만 조회할 수 있습니다.");
        }

        return asAssignmentRepository.findTaskSchedule(engineerUserId, date)
                .stream()
                .map(EngineerTaskScheduleResponse::from)
                .toList();
    }

    /**
     * 대행사 소속 기사 수리 완료 실적 TOP 3 조회.
     * as_requests.status = COMPLETED 기준으로 기사별 건수를 집계해 내림차순 상위 3명을 반환한다.
     * 소속 기사가 3명 미만이면 실제 인원 수만큼만 반환한다(빈 리스트도 정상 응답).
     */
    public List<EngineerRankResponse> getTop3Engineers(Long agencyUserId) {
        // 소속 대행사 식별
        User agencyUser = findUserById(agencyUserId);
        Long agencyId = getAgencyId(agencyUser);

        // COMPLETED 건수 기준 내림차순 상위 3명 집계
        List<EngineerCompletedCount> results = asAssignmentRepository
                .findTopByCompletedCount(agencyId, AsStatus.COMPLETED, PageRequest.of(0, 3));

        // 순위 부여 후 DTO 변환
        AtomicInteger rankCounter = new AtomicInteger(1);
        return results.stream()
                .map(r -> EngineerRankResponse.builder()
                        .rank(rankCounter.getAndIncrement())
                        .engineerUserId(r.getEngineerUserId())
                        .name(r.getEngineerName())
                        .completedCount(r.getCompletedCount())
                        .build())
                .toList();
    }

    /**
     * A/S 요청 기반 추천 기사 목록 조회
     * - LMS 이수 완료 기사 + 해당 날짜 AVAILABLE 근무표 보유 기사를 필터링해 반환
     * - 평점 내림차순 정렬
     */
    public List<EngineerRecommendResponse> getRecommendedEngineers(Long agencyUserId, Long requestId) {
        User agencyUser = findUserById(agencyUserId);
        Long agencyId = getAgencyId(agencyUser);

        // A/S 요청 존재 여부 확인
        AsRequest asRequest = asRequestRepository.findById(requestId)
                .orElseThrow(() -> new NoSuchElementException("해당 A/S 요청이 존재하지 않습니다."));

        LocalDate scheduledDate = asRequest.getScheduledDate();

        // 대행사 소속 기사 전체 조회
        List<EngineerProfile> profiles = engineerProfileRepository.findByAgencyId(agencyId);

        return profiles.stream()
                // LMS 이수 완료 기사만 필터
                .filter(EngineerProfile::isLmsCompleted)
                // 해당 날짜 AVAILABLE 근무표 보유 기사만 필터
                .filter(profile -> engineerScheduleRepository
                        .findByUser_IdAndWorkDateBetweenOrderByWorkDateAsc(
                                profile.getUser().getId(), scheduledDate, scheduledDate)
                        .stream()
                        .anyMatch(s -> s.getStatus() == ScheduleStatus.AVAILABLE))
                // 평점 내림차순 정렬
                .sorted((a, b) -> {
                    double ratingA = a.getAvgRating() != null ? a.getAvgRating().doubleValue() : 0.0;
                    double ratingB = b.getAvgRating() != null ? b.getAvgRating().doubleValue() : 0.0;
                    return Double.compare(ratingB, ratingA);
                })
                .map(profile -> {
                    Long engineerUserId = profile.getUser().getId();

                    List<String> expertBrands = expertBrandRepository.findByEngineer_Id(engineerUserId).stream()
                            .map(EngineerExpertBrand::getBrandName)
                            .toList();

                    List<String> regionNames = serviceRegionRepository.findByEngineer_Id(engineerUserId).stream()
                            .map(r -> r.getRegion().getName())
                            .toList();

                    // 해당 날짜 AVAILABLE 근무표 — 첫 번째 timeSlot startTime 추출용
                    Optional<EngineerSchedule> availableSchedule = engineerScheduleRepository
                            .findByUser_IdAndWorkDateBetweenOrderByWorkDateAsc(engineerUserId, scheduledDate, scheduledDate)
                            .stream()
                            .filter(s -> s.getStatus() == ScheduleStatus.AVAILABLE)
                            .findFirst();

                    return EngineerRecommendResponse.from(
                            profile, expertBrands, regionNames, availableSchedule.orElse(null));
                })
                .toList();
    }

    /**
     * 소속 기사 실시간 배정 현황 조회
     * - 소속 기사 전원의 현재 진행 중 배정 상태 + LMS 이수 여부 반환
     * - REJECTED·COMPLETED 제외한 최신 배정 1건 기준
     */
    public List<EngineerRealtimeStatusResponse> getEngineersRealtimeStatus(Long agencyUserId) {
        User agencyUser = findUserById(agencyUserId);
        Long agencyId = getAgencyId(agencyUser);

        List<EngineerProfile> profiles = engineerProfileRepository.findByAgencyId(agencyId);

        return profiles.stream()
                .map(profile -> {
                    Long engineerUserId = profile.getUser().getId();

                    List<String> regionNames = serviceRegionRepository.findByEngineer_Id(engineerUserId).stream()
                            .map(r -> r.getRegion().getName())
                            .toList();

                    // 현재 활성 배정 1건 조회 (최신순 첫 번째)
                    AsAssignment activeAssignment = asAssignmentRepository
                            .findActiveByEngineerId(engineerUserId)
                            .stream()
                            .findFirst()
                            .orElse(null);

                    return EngineerRealtimeStatusResponse.from(profile, regionNames, activeAssignment);
                })
                .toList();
    }

    /**
     * 소속 기사 정산 내역 조회
     * - 타 대행사 소속 기사 접근 시 IllegalAccessException 발생
     */
    public List<EngineerSettlementResponse> getEngineerSettlements(Long agencyUserId, Long engineerUserId)
            throws IllegalAccessException {
        User agencyUser = findUserById(agencyUserId);
        Long agencyId = getAgencyId(agencyUser);

        EngineerProfile profile = engineerProfileRepository.findByUser_Id(engineerUserId)
                .orElseThrow(() -> new NoSuchElementException("해당 기사의 프로필 정보가 존재하지 않습니다."));
        validateSameAgency(profile, agencyId);

        return settlementRepository.findByEngineerIdWithRequest(engineerUserId).stream()
                .map(EngineerSettlementResponse::from)
                .toList();
    }

    /**
     * 소속 기사 LMS 이수 현황 조회
     * - 당해 연도 이수 완료 여부 + 이수한 콘텐츠 이력 반환
     * - 타 대행사 소속 기사 접근 시 IllegalAccessException 발생
     */
    public EngineerLmsStatusResponse getEngineerLmsStatus(Long agencyUserId, Long engineerUserId)
            throws IllegalAccessException {
        User agencyUser = findUserById(agencyUserId);
        Long agencyId = getAgencyId(agencyUser);

        EngineerProfile profile = engineerProfileRepository.findByUser_Id(engineerUserId)
                .orElseThrow(() -> new NoSuchElementException("해당 기사의 프로필 정보가 존재하지 않습니다."));
        validateSameAgency(profile, agencyId);

        int currentYear = Year.now().getValue();

        List<LmsConfirmation> confirmations = lmsConfirmationRepository
                .findByUserIdAndYear(engineerUserId, currentYear);

        return EngineerLmsStatusResponse.of(profile.isLmsCompleted(), currentYear, confirmations);
    }

    /**
     * 소속 기사 수신 리뷰 목록 조회
     * - 공개(isVisible=true) 리뷰만 반환, 최신순 정렬
     * - 타 대행사 소속 기사 접근 시 IllegalAccessException 발생
     */
    public EngineerReviewListResponse getEngineerReviews(Long agencyUserId, Long engineerUserId)
            throws IllegalAccessException {
        User agencyUser = findUserById(agencyUserId);
        Long agencyId = getAgencyId(agencyUser);

        EngineerProfile profile = engineerProfileRepository.findByUser_Id(engineerUserId)
                .orElseThrow(() -> new NoSuchElementException("해당 기사의 프로필 정보가 존재하지 않습니다."));
        validateSameAgency(profile, agencyId);

        List<Review> reviews = reviewRepository.findVisibleByEngineerId(engineerUserId);
        return EngineerReviewListResponse.of(reviews);
    }

    // ──────────────────────────────────────────────────
    // Private helper methods
    // ──────────────────────────────────────────────────

    private User findUserById(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new NoSuchElementException("유저 정보가 존재하지 않습니다."));
    }

    private Long getAgencyId(User user) {
        if (user.getAgency() == null) {
            throw new NoSuchElementException("소속 대행사 정보가 없습니다.");
        }
        return user.getAgency().getId();
    }

    // 대상 기사가 동일 대행사 소속인지 검증
    private void validateSameAgency(EngineerProfile profile, Long agencyId) throws IllegalAccessException {
        if (profile.getUser().getAgency() == null
                || !profile.getUser().getAgency().getId().equals(agencyId)) {
            throw new IllegalAccessException("소속 대행사의 기사만 조회/수정할 수 있습니다.");
        }
    }

    private List<String> saveExpertBrands(User engineer, List<String> brands) {
        expertBrandRepository.deleteByEngineer_Id(engineer.getId());
        // Hibernate의 flush 순서(Insert → Delete)로 인해 flush 없이 바로 재삽입하면
        // 기존 값과 겹치는 브랜드에서 uk_eng_brand 유니크 제약 위반이 발생함 — 삭제를 먼저 반영
        expertBrandRepository.flush();

        List<String> distinctBrands = brands.stream()
                .filter(b -> b != null && !b.isBlank())
                .map(String::trim)
                .distinct()
                .toList();

        List<EngineerExpertBrand> entities = distinctBrands.stream()
                .map(b -> EngineerExpertBrand.builder().engineer(engineer).brandName(b).build())
                .toList();
        expertBrandRepository.saveAll(entities);
        return distinctBrands;
    }

    private List<Integer> saveServiceRegions(User engineer, List<Integer> regionIds) {
        serviceRegionRepository.deleteByEngineer_Id(engineer.getId());
        // 위 saveExpertBrands와 동일한 이유로 삭제를 먼저 flush — 겹치는 지역에서 uk_eng_region 위반 방지
        serviceRegionRepository.flush();

        List<EngineerServiceRegion> entities = new ArrayList<>();
        List<Integer> result = new ArrayList<>();
        for (Integer regionId : regionIds.stream().distinct().toList()) {
            Regions region = regionRepository.findById(regionId)
                    .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 지역입니다. (regionId=" + regionId + ")"));
            if (region.getDepth() != 2) {
                throw new IllegalArgumentException("서비스 가능 지역은 구 단위(depth=2)만 선택 가능합니다.");
            }
            entities.add(EngineerServiceRegion.builder().engineer(engineer).region(region).build());
            result.add(regionId);
        }
        serviceRegionRepository.saveAll(entities);
        return result;
    }
}
