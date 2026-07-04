package com.careflow.lms.service;

import com.careflow.appliance.entity.ApplianceCategory;
import com.careflow.appliance.repository.ApplianceCategoryRepository;
import com.careflow.common.enums.Role;
import com.careflow.engineer.domain.entity.EngineerProfile;
import com.careflow.engineer.repository.EngineerProfileRepository;
import com.careflow.lms.dto.*;
import com.careflow.lms.entity.LmsConfirmation;
import com.careflow.lms.entity.LmsContent;
import com.careflow.lms.entity.LmsContent.RequiredLevel;
import com.careflow.lms.repository.LmsConfirmationRepository;
import com.careflow.lms.repository.LmsContentRepository;
import com.careflow.notification.service.NotificationService;
import com.careflow.user.entity.User;
import com.careflow.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class LmsService {

    private final LmsContentRepository       lmsContentRepository;
    private final LmsConfirmationRepository  lmsConfirmationRepository;
    private final EngineerProfileRepository  engineerProfileRepository;
    private final UserRepository             userRepository;
    private final ApplianceCategoryRepository applianceCategoryRepository;
    private final NotificationService        notificationService;

    // ─────────────────────────────────────────────
    // 기사용
    // ─────────────────────────────────────────────

    /**
     * [기사] 본인 이수 대상 콘텐츠 목록 조회
     * is_active=true인 이수 이력만 완료로 인정
     */
    @Transactional(readOnly = true)
    public List<LmsContentWithStatusDto> getRequiredContentsWithStatus(Long engineerUserId) {
        EngineerProfile profile = getEngineerProfile(engineerUserId);
        RequiredLevel requiredLevel = RequiredLevel.valueOf(profile.getSkillLevel().name());
        int currentYear = LocalDate.now().getYear();

        List<LmsContent> required = lmsContentRepository.findRequiredContents(
                profile.getCategory().getCategoryId(),
                requiredLevel
        );

        // [v10 변경] is_active=true 조건 추가 — 재이수 강제로 논리 삭제된 이력 제외
        Map<Long, LocalDateTime> confirmedAtMap = lmsConfirmationRepository
                .findByUserIdAndYearAndIsActive(engineerUserId, currentYear, true)
                .stream()
                .collect(Collectors.toMap(
                        c -> c.getContent().getContentId(),
                        LmsConfirmation::getConfirmedAt
                ));

        return required.stream()
                .map(c -> new LmsContentWithStatusDto(
                        LmsContentResponseDto.from(c),
                        confirmedAtMap.containsKey(c.getContentId()),
                        confirmedAtMap.get(c.getContentId())
                ))
                .toList();
    }

    /**
     * [기사] 콘텐츠 이수 처리
     *
     * [v10 변경] completeLms() 호출 제거
     * OX퀴즈 도입으로 is_lms_completed 갱신 책임이 QuizService.submitQuiz()로 이전.
     * 콘텐츠를 전부 이수해도 OX퀴즈 합격 전까지 is_lms_completed=0 유지.
     *
     * [v11 수정] existsBy...() 사전 체크 → is_active 무관 단건 조회 후 3분기로 교체
     *  1) 행이 아예 없음            → 최초 이수. 신규 INSERT (기존과 동일)
     *  2) 행이 있고 is_active=true  → 진짜 중복 완료 요청. 403 (기존과 동일한 사용자 경험)
     *  3) 행이 있고 is_active=false → 재이수 강제로 비활성화됐던 이력.
     *                               INSERT가 아니라 UPDATE로 재활성화하여
     *                               uk_lms_confirm_year UNIQUE 제약과 충돌하지 않도록 처리
     * (기존 버그: 3번 케이스도 1번과 동일하게 "이미 이수함"으로 오판해 403을 던졌음)
     */
    @Transactional
    public void completeContent(Long engineerUserId, Long contentId) {
        int currentYear = LocalDate.now().getYear();

        User engineer       = getUserOrThrow(engineerUserId);
        LmsContent content  = getContentOrThrow(contentId);

        Optional<LmsConfirmation> existing = lmsConfirmationRepository
                .findByUserIdAndContentIdAndYear(engineerUserId, contentId, currentYear);

        if (existing.isPresent()) {
            LmsConfirmation confirmation = existing.get();

            if (confirmation.isActive()) {
                // 진짜 중복 완료 요청 — 기존과 동일한 메시지/상태 코드 유지
                throw new IllegalStateException("이미 이수한 콘텐츠입니다.");
            }

            // 재이수 강제(is_active=false)로 비활성화됐던 이력 → 재활성화 (INSERT 아님)
            confirmation.reactivate(content.getVersion());

        } else {
            // 최초 이수 → 신규 INSERT (기존 로직과 동일)
            lmsConfirmationRepository.save(LmsConfirmation.of(engineer, content, currentYear));
        }

        lmsConfirmationRepository.flush();

        // [v10 변경] 콘텐츠 전부 이수 시 completeLms() 호출하지 않음
        // → QuizService.submitQuiz() 합격 시에만 is_lms_completed=1로 갱신
    }

    /**
     * [v10 신규] OX퀴즈 응시 자격 충족 여부 반환
     * QuizService.validateEligibility()에서 호출
     *
     * is_active=true인 이수 이력만 집계 —
     * 재이수 강제(deactivate)된 이력은 제외하여 재이수 필요 상태를 정확히 반영
     */
    @Transactional(readOnly = true)
    public boolean isQuizEligible(Long engineerUserId) {
        int currentYear = LocalDate.now().getYear();
        EngineerProfile profile = getEngineerProfile(engineerUserId);
        RequiredLevel requiredLevel = RequiredLevel.valueOf(profile.getSkillLevel().name());

        Set<Long> requiredIds = lmsContentRepository
                .findRequiredContents(profile.getCategory().getCategoryId(), requiredLevel)
                .stream()
                .map(LmsContent::getContentId)
                .collect(Collectors.toSet());

        // is_active=true인 이수 이력만 집계
        Set<Long> completedIds = new HashSet<>(
                lmsConfirmationRepository.findContentIdsByUserIdAndYearAndIsActive(
                        engineerUserId, currentYear, true)
        );

        return completedIds.containsAll(requiredIds);
    }

    @Transactional(readOnly = true)
    public LmsAnnualStatusDto getAnnualStatus(Long engineerUserId) {
        int currentYear = LocalDate.now().getYear();

        EngineerProfile profile = getEngineerProfile(engineerUserId);
        RequiredLevel requiredLevel = RequiredLevel.valueOf(profile.getSkillLevel().name());

        Set<Long> requiredIds = lmsContentRepository.findRequiredContents(
                profile.getCategory().getCategoryId(),
                requiredLevel
        ).stream().map(LmsContent::getContentId).collect(Collectors.toSet());

        int totalCount = requiredIds.size();

        Set<Long> completedIds = new HashSet<>(
                lmsConfirmationRepository.findContentIdsByUserIdAndYearAndIsActive(
                        engineerUserId, currentYear, true)
        );
        completedIds.retainAll(requiredIds); // 현재 필수 목록에 속하는 것만 카운트

        int completedCount = completedIds.size();

        return new LmsAnnualStatusDto(totalCount, completedCount, profile.isLmsCompleted());
    }

    // ─────────────────────────────────────────────
    // 관리자용
    // ─────────────────────────────────────────────

    @Transactional
    public LmsContentResponseDto createContent(LmsContentCreateDto dto, Long adminUserId) {
        User admin = getUserOrThrow(adminUserId);
        ApplianceCategory category = applianceCategoryRepository.findById(dto.categoryId())
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 카테고리입니다."));

        LmsContent content = LmsContent.builder()
                .category(category)
                .title(dto.title())
                .body(dto.body())
                .videoUrl(dto.videoUrl())
                .requiredLevel(dto.requiredLevel())
                .contentType(dto.contentType())
                .version(dto.version())
                .isActive(true)
                .createdBy(admin)
                .build();

        return LmsContentResponseDto.from(lmsContentRepository.save(content));
    }

    @Transactional(readOnly = true)
    public LmsContentResponseDto getContent(Long contentId) {
        return LmsContentResponseDto.from(getContentOrThrow(contentId));
    }

    @Transactional(readOnly = true)
    public List<LmsContentResponseDto> getAllContents() {
        return lmsContentRepository
                .findAllByOrderByCategoryCategoryIdAscRequiredLevelAscContentIdAsc()
                .stream()
                .map(LmsContentResponseDto::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<LmsContentResponseDto> getContentsByFilters(
            Integer categoryId,
            LmsContent.RequiredLevel requiredLevel,
            Boolean isActive
    ) {
        return lmsContentRepository.findByFilters(categoryId, requiredLevel, isActive)
                .stream()
                .map(LmsContentResponseDto::from)
                .toList();
    }

    @Transactional
    public void updateContent(Long contentId, LmsContentUpdateDto dto) {
        LmsContent content = getContentOrThrow(contentId);
        content.update(dto.title(), dto.body(), dto.videoUrl(), dto.requiredLevel(), dto.version());
    }

    @Transactional
    public void deactivateContent(Long contentId) {
        getContentOrThrow(contentId).deactivate();
    }

    @Transactional
    public void activateContent(Long contentId) {
        getContentOrThrow(contentId).activate();
    }

    @Transactional(readOnly = true)
    public List<LmsConfirmationResponseDto> getEngineerConfirmations(Long engineerUserId, Integer year) {
        List<LmsConfirmation> confirmations;
        if (year != null) {
            confirmations = lmsConfirmationRepository.findByUserIdAndYear(engineerUserId, year);
        } else {
            confirmations = lmsConfirmationRepository.findAllByUserIdWithContent(engineerUserId);
        }
        return confirmations.stream()
                .map(LmsConfirmationResponseDto::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<LmsEngineerStatusDto> getAgencyEngineersLmsStatus(Long agencyId, Long requestUserId) {
        User requestUser = getUserOrThrow(requestUserId);
        if (!requestUser.getRole().equals(Role.ADMIN) &&
                !agencyId.equals(requestUser.getAgency().getId())) {
            throw new IllegalStateException("본인 소속 대행사만 조회할 수 있습니다.");
        }

        int currentYear = LocalDate.now().getYear();
        List<EngineerProfile> engineers = engineerProfileRepository.findByAgencyId(agencyId);

        return engineers.stream()
                .map(profile -> {
                    Long userId = profile.getUser().getId();
                    RequiredLevel requiredLevel = RequiredLevel.valueOf(profile.getSkillLevel().name());

                    int totalCount = lmsContentRepository.findRequiredContents(
                            profile.getCategory().getCategoryId(),
                            requiredLevel
                    ).size();

                    // [v10 변경] is_active=true 조건 추가
                    int completedCount = lmsConfirmationRepository
                            .findContentIdsByUserIdAndYearAndIsActive(userId, currentYear, true)
                            .size();

                    return new LmsEngineerStatusDto(
                            userId,
                            profile.getUser().getName(),
                            profile.getCategory().getCategoryId(),
                            profile.getSkillLevel().name(),
                            totalCount,
                            completedCount,
                            profile.isLmsCompleted()
                    );
                })
                .toList();
    }

    @Transactional
    public void sendLmsNotification(Long agencyId, Long engineerUserId, Long requestUserId) {
        User requestUser = getUserOrThrow(requestUserId);
        if (!requestUser.getRole().equals(Role.ADMIN) &&
                !agencyId.equals(requestUser.getAgency().getId())) {
            throw new IllegalStateException("본인 소속 대행사의 기사에게만 알림을 발송할 수 있습니다.");
        }

        User engineer = getUserOrThrow(engineerUserId);
        EngineerProfile profile = getEngineerProfile(engineerUserId);

        if (profile.isLmsCompleted()) {
            throw new IllegalStateException("이미 LMS 교육을 이수한 기사입니다.");
        }

        int currentYear = LocalDate.now().getYear();
        RequiredLevel requiredLevel = RequiredLevel.valueOf(profile.getSkillLevel().name());

        int totalCount = lmsContentRepository.findRequiredContents(
                profile.getCategory().getCategoryId(),
                requiredLevel
        ).size();

        // [v10 변경] is_active=true 조건 추가
        int completedCount = lmsConfirmationRepository
                .findContentIdsByUserIdAndYearAndIsActive(engineerUserId, currentYear, true)
                .size();

        int remainingCount = totalCount - completedCount;

        notificationService.send(
                engineer,
                "LMS",
                "LMS 교육 이수 안내",
                String.format(
                        "%d년도 필수 교육 중 %d개가 미이수 상태입니다. LMS 교육 메뉴에서 이수를 완료해 주세요.",
                        currentYear, remainingCount
                )
        );
    }

    // ─────────────────────────────────────────────
    // 내부 헬퍼
    // ─────────────────────────────────────────────

    private User getUserOrThrow(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 사용자입니다."));
    }

    private LmsContent getContentOrThrow(Long contentId) {
        return lmsContentRepository.findById(contentId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 콘텐츠입니다."));
    }

    private EngineerProfile getEngineerProfile(Long userId) {
        return engineerProfileRepository.findByUser_Id(userId)
                .orElseThrow(() -> new IllegalArgumentException("기사 프로필이 존재하지 않습니다."));
    }
}