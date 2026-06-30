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
import com.careflow.user.entity.User;
import com.careflow.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class LmsService {

    private final LmsContentRepository      lmsContentRepository;
    private final LmsConfirmationRepository lmsConfirmationRepository;
    private final EngineerProfileRepository engineerProfileRepository;
    private final UserRepository            userRepository;
    private final ApplianceCategoryRepository  applianceCategoryRepository;

    // ─────────────────────────────────────────────
    // 기사용
    // ─────────────────────────────────────────────

    /**
     * [기사] 본인 이수 대상 콘텐츠 목록 조회
     *
     * 기사의 category_id + skill_level 기준으로 필터링
     * 각 콘텐츠에 당해 연도 이수 여부(completed)를 함께 반환
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

        Map<Long, LocalDateTime> confirmedAtMap = lmsConfirmationRepository
                .findByUserIdAndYear(engineerUserId, currentYear)
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
     * 처리 순서:
     * 1. 중복 이수 여부 체크
     * 2. lms_confirmations INSERT
     * 3. 전체 이수 완료 여부 판정
     * 4. 완료 시 engineer_profiles.is_lms_completed = 1 갱신
     */
    @Transactional
    public void completeContent(Long engineerUserId, Long contentId) {
        int currentYear = LocalDate.now().getYear();

        // 1. 중복 이수 체크
        if (lmsConfirmationRepository.existsByUserIdAndContentContentIdAndCompletionYear(
                engineerUserId, contentId, currentYear)) {
            throw new IllegalStateException("이미 이수한 콘텐츠입니다.");
        }

        User engineer   = getUserOrThrow(engineerUserId);
        LmsContent content = getContentOrThrow(contentId);

        // 2. 이수 이력 저장
        lmsConfirmationRepository.save(LmsConfirmation.of(engineer, content, currentYear));

        // 3. 전체 이수 완료 판정 후 is_lms_completed 갱신
        updateLmsCompletionStatus(engineer, currentYear);
    }

    /**
     * 전체 이수 완료 판정
     *
     * 이수 대상 content_id 전체 집합 == 이수 완료 content_id 집합 이면 완료 처리
     */
    private void updateLmsCompletionStatus(User engineer, int currentYear) {
        EngineerProfile profile = getEngineerProfile(engineer.getId());
        RequiredLevel requiredLevel = RequiredLevel.valueOf(profile.getSkillLevel().name());

        Set<Long> requiredIds = lmsContentRepository
                .findRequiredContents(
                        profile.getCategory().getCategoryId(),
                        requiredLevel
                )
                .stream()
                .map(LmsContent::getContentId)
                .collect(Collectors.toSet());

        Set<Long> completedIds = lmsConfirmationRepository
                .findContentIdsByUserIdAndYear(engineer.getId(), currentYear)
                .stream()
                .collect(Collectors.toSet());

        // 이수 대상 전부 완료했을 때만 갱신
        if (completedIds.containsAll(requiredIds)) {
            profile.completeLms();
        }
    }

    @Transactional(readOnly = true)
    public LmsAnnualStatusDto getAnnualStatus(Long engineerUserId) {
        int currentYear = LocalDate.now().getYear();

        EngineerProfile profile = getEngineerProfile(engineerUserId);
        RequiredLevel requiredLevel = RequiredLevel.valueOf(profile.getSkillLevel().name());

        int totalCount = lmsContentRepository.findRequiredContents(
                profile.getCategory().getCategoryId(),
                requiredLevel
        ).size();

        int completedCount = lmsConfirmationRepository
                .findContentIdsByUserIdAndYear(engineerUserId, currentYear)
                .size();

        return new LmsAnnualStatusDto(totalCount, completedCount, profile.isLmsCompleted());
    }

    // ─────────────────────────────────────────────
    // 관리자용
    // ─────────────────────────────────────────────

    /**
     * [관리자] 콘텐츠 등록 (M-13)
     */
    @Transactional
    public LmsContentResponseDto createContent(LmsContentCreateDto dto, Long adminUserId) {
        User admin = getUserOrThrow(adminUserId);
        ApplianceCategory category = applianceCategoryRepository.findById(dto.categoryId())
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 카테고리입니다."));

        LmsContent content = LmsContent.builder()
                .category(category)
                .title(dto.title())
                .body(dto.body())
                .requiredLevel(dto.requiredLevel())
                .contentType(LmsContent.ContentType.TEXT)
                .version(dto.version())
                .isActive(true)
                .createdBy(admin)
                .build();

        return LmsContentResponseDto.from(lmsContentRepository.save(content));
    }


    // 콘텐츠 단 건 조회
    @Transactional(readOnly = true)
    public LmsContentResponseDto getContent(Long contentId) {
        return LmsContentResponseDto.from(getContentOrThrow(contentId));
    }

    // 콘텐츠 전체 조회
    @Transactional(readOnly = true)
    public List<LmsContentResponseDto> getAllContents() {
        return lmsContentRepository
                .findAllByOrderByCategoryCategoryIdAscRequiredLevelAscContentIdAsc()
                .stream()
                .map(LmsContentResponseDto::from)
                .toList();
    }

    // 콘텐츠 필터링 조회
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

    /**
     * [관리자] 콘텐츠 수정 (M-13)
     */
    @Transactional
    public void updateContent(Long contentId, LmsContentUpdateDto dto) {
        LmsContent content = getContentOrThrow(contentId);
        content.update(dto.title(), dto.body(), dto.requiredLevel(), dto.version());
    }

    /**
     * [관리자] 콘텐츠 비활성화 (M-13)
     */
    @Transactional
    public void deactivateContent(Long contentId) {
        getContentOrThrow(contentId).deactivate();
    }

    /**
     * [관리자] 콘텐츠 활성화 (M-13)
     */
    @Transactional
    public void activateContent(Long contentId) {
        getContentOrThrow(contentId).activate();
    }

    /**
     * [관리자] 기사별 연도별 이수 현황 조회 (M-14)
     */
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


    // 대행사의 소속 엔지니어 LMS상태 전체 조회
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

                    int completedCount = lmsConfirmationRepository
                            .findContentIdsByUserIdAndYear(userId, currentYear)
                            .size();

                    return new LmsEngineerStatusDto(
                            userId,
                            profile.getUser().getName(),
                            totalCount,
                            completedCount,
                            profile.isLmsCompleted()
                    );
                })
                .toList();
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
