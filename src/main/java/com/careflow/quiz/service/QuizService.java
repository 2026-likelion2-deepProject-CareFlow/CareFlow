package com.careflow.quiz.service;

import com.careflow.appliance.entity.ApplianceCategory;
import com.careflow.appliance.repository.ApplianceCategoryRepository;
import com.careflow.engineer.domain.entity.EngineerProfile;
import com.careflow.engineer.repository.EngineerProfileRepository;
import com.careflow.lms.service.LmsService;
import com.careflow.quiz.dto.*;
import com.careflow.quiz.entity.QuizAttempt;
import com.careflow.quiz.entity.QuizQuestion;
import com.careflow.quiz.entity.QuizQuestion.RequiredLevel;
import com.careflow.quiz.repository.QuizAttemptRepository;
import com.careflow.quiz.repository.QuizQuestionRepository;
import com.careflow.lms.repository.LmsConfirmationRepository;
import com.careflow.user.entity.User;
import com.careflow.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class QuizService {

    private static final int MAX_ATTEMPTS     = 3;
    private static final int PASS_SCORE       = 4;
    private static final int REQUIRED_QUESTIONS = 5;

    private final QuizQuestionRepository     quizQuestionRepository;
    private final QuizAttemptRepository      quizAttemptRepository;
    private final LmsConfirmationRepository  lmsConfirmationRepository;
    private final EngineerProfileRepository  engineerProfileRepository;
    private final ApplianceCategoryRepository applianceCategoryRepository;
    private final UserRepository             userRepository;
    private final LmsService                 lmsService;

    // ─────────────────────────────────────────────
    // 기사용
    // ─────────────────────────────────────────────

    /**
     * [기사] 응시 자격 현황 조회
     * 화면 상태 배너 렌더링에 사용
     */
    @Transactional(readOnly = true)
    public QuizStatusDto getQuizStatus(Long engineerUserId) {
        int currentYear = LocalDate.now().getYear();
        EngineerProfile profile = getEngineerProfile(engineerUserId);
        Integer categoryId = profile.getCategory().getCategoryId();
        RequiredLevel level = RequiredLevel.valueOf(profile.getSkillLevel().name());

        boolean isContentCompleted  = lmsService.isQuizEligible(engineerUserId);
        boolean isQuestionRegistered = quizQuestionRepository
                .countByCategoryIdAndRequiredLevelAndQuizYearAndIsActiveTrue(
                        categoryId, level, currentYear) >= REQUIRED_QUESTIONS;
        boolean isPassed = quizAttemptRepository
                .existsByUserIdAndCategoryIdAndRequiredLevelAndQuizYearAndIsPassed(
                        engineerUserId, categoryId, level, currentYear, true);

        long attemptCount = 0;
        boolean isForceReLearning = false;

        if (!isPassed) {
            attemptCount = quizAttemptRepository
                    .countCurrentCycleAttempts(engineerUserId, categoryId, level, currentYear);
            isForceReLearning = (attemptCount >= MAX_ATTEMPTS) && !isContentCompleted;
        }

        return new QuizStatusDto(
                isContentCompleted,
                isQuestionRegistered,
                isPassed,
                (int) attemptCount,
                MAX_ATTEMPTS,
                isForceReLearning
        );
    }

    /**
     * [기사] 본인 계층 + 현재 연도 문항 조회 (정답 필드 제외)
     */
    @Transactional(readOnly = true)
    public List<QuizQuestionResponseDto> getQuestions(Long engineerUserId) {
        int currentYear = LocalDate.now().getYear();
        EngineerProfile profile = getEngineerProfile(engineerUserId);
        Integer categoryId = profile.getCategory().getCategoryId();
        RequiredLevel level = RequiredLevel.valueOf(profile.getSkillLevel().name());

        // 응시 자격 사전 검증 (문항 조회 전에도 체크 — 정답 유출 방지)
        validateEligibility(engineerUserId, categoryId, level, currentYear);

        return quizQuestionRepository
                .findByCategoryIdAndRequiredLevelAndQuizYearAndIsActiveTrue(
                        categoryId, level, currentYear)
                .stream()
                .sorted((a, b) -> Integer.compare(a.getSortOrder(), b.getSortOrder()))
                .map(QuizQuestionResponseDto::from)
                .toList();
    }

    /**
     * [기사] 퀴즈 제출 및 채점
     *
     * 처리 순서:
     * 1. 응시 자격 검증
     * 2. 채점 (서버에서 정답 비교)
     * 3. quiz_attempts INSERT
     * 4. 합격 시 → is_lms_completed = 1 갱신 + 문항별 정오답 상세 반환 (재학습 유도)
     * 5. 불합격 + 3회 초과 시 → lms_confirmations 논리 삭제 (재이수 강제)
     * 6. 결과 반환
     *    - 합격: 응시 횟수 + 문항별 정오답 상세 공개 (어떤 문제를 틀렸는지 알고 재학습 가능)
     *    - 불합격: 응시 횟수만 공개. 점수·정오답 상세 비공개 (역추산 방지 — 재응시 전략 차단)
     */
    @Transactional
    public QuizResultDto submitQuiz(Long engineerUserId, QuizSubmitDto dto) {

        int currentYear = LocalDate.now().getYear();
        EngineerProfile profile = getEngineerProfile(engineerUserId);
        Integer categoryId = profile.getCategory().getCategoryId();
        RequiredLevel level = RequiredLevel.valueOf(profile.getSkillLevel().name());

        // 1. 응시 자격 검증
        validateEligibility(engineerUserId, categoryId, level, currentYear);

        // 2. 채점
        List<QuizQuestion> questions = quizQuestionRepository
                .findByCategoryIdAndRequiredLevelAndQuizYearAndIsActiveTrue(
                        categoryId, level, currentYear);

        Map<Long, QuizQuestion> questionMap = questions.stream()
                .collect(Collectors.toMap(QuizQuestion::getQuestionId, q -> q));

        // 제출 답안과 정답 비교
        List<QuizAnswerResultDto> answerResults = dto.answers().stream()
                .map(a -> {
                    QuizQuestion q = questionMap.get(a.questionId());
                    if (q == null) return null;
                    boolean isCorrect = q.isCorrectAnswer() == a.submittedAnswer();
                    return new QuizAnswerResultDto(
                            q.getQuestionId(),
                            q.getSortOrder(),
                            q.getQuestionText(),
                            a.submittedAnswer(),
                            q.isCorrectAnswer(),
                            isCorrect
                    );
                })
                .filter(r -> r != null)
                .sorted((a, b) -> Integer.compare(a.sortOrder(), b.sortOrder()))
                .toList();

        int score = (int) answerResults.stream().filter(QuizAnswerResultDto::isCorrect).count();
        boolean isPassed = score >= PASS_SCORE;

        // 3. 응시 이력 저장
        QuizAttempt attempt = QuizAttempt.builder()
                .user(getUserOrThrow(engineerUserId))
                .categoryId(categoryId)
                .requiredLevel(level)
                .quizYear(currentYear)
                .score(score)
                .isPassed(isPassed)
                .build();
        quizAttemptRepository.save(attempt);
        quizAttemptRepository.flush();

        // 4. 합격 처리 — is_lms_completed = 1 갱신 + 정오답 상세 반환
        if (isPassed) {
            profile.completeLms();
            engineerProfileRepository.save(profile);
            engineerProfileRepository.flush();

            int finalAttemptCount = (int) quizAttemptRepository
                    .countCurrentCycleAttempts(engineerUserId, categoryId, level, currentYear);
            // 합격 시에만 answerDetails 포함 (틀린 문제 재학습 유도)
            return QuizResultDto.passed(finalAttemptCount, answerResults);
        }

        // 5. 불합격 처리
        long attemptCount = quizAttemptRepository
                .countCurrentCycleAttempts(engineerUserId, categoryId, level, currentYear);

        if (attemptCount >= MAX_ATTEMPTS) {
            forceReLearn(engineerUserId, categoryId, currentYear);
            // 불합격 시 answerDetails = null (역추산 방지)
            return QuizResultDto.failedWithReLearning((int) attemptCount);
        }

        return QuizResultDto.failed((int) attemptCount);
    }

    /**
     * [기사] 본인 응시 이력 조회
     */
    @Transactional(readOnly = true)
    public List<QuizAttemptResponseDto> getMyAttempts(Long engineerUserId) {
        return quizAttemptRepository
                .findByUser_IdOrderByAttemptedAtDesc(engineerUserId)
                .stream()
                .map(QuizAttemptResponseDto::from)
                .toList();
    }

    // ─────────────────────────────────────────────
    // 관리자용
    // ─────────────────────────────────────────────

    /**
     * [관리자] 문항 등록
     */
    @Transactional
    public void createQuestion(QuizQuestionCreateDto dto, Long adminUserId) {
        User admin = getUserOrThrow(adminUserId);
        ApplianceCategory category = applianceCategoryRepository.findById(dto.categoryId())
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 카테고리입니다."));

        QuizQuestion question = QuizQuestion.builder()
                .category(category)
                .requiredLevel(dto.requiredLevel())
                .quizYear(dto.quizYear())
                .questionText(dto.questionText())
                .correctAnswer(dto.correctAnswer())
                .sortOrder(dto.sortOrder())
                .createdBy(admin)
                .build();

        quizQuestionRepository.save(question);
    }

    /**
     * [관리자] 문항 수정
     */
    @Transactional
    public void updateQuestion(Long questionId, QuizQuestionUpdateDto dto) {
        QuizQuestion question = getQuestionOrThrow(questionId);
        question.update(dto.questionText(), dto.correctAnswer(), dto.sortOrder());
    }

    /**
     * [관리자] 문항 비활성화
     */
    @Transactional
    public void deactivateQuestion(Long questionId) {
        getQuestionOrThrow(questionId).deactivate();
    }

    /**
     * [관리자] 문항 삭제 (응시 이력 없는 경우만 허용)
     */
    @Transactional
    public void deleteQuestion(Long questionId) {
        QuizQuestion question = getQuestionOrThrow(questionId);

        boolean hasAttempt = quizAttemptRepository
                .existsByCategoryIdAndRequiredLevelAndQuizYear(
                        question.getCategory().getCategoryId(),
                        question.getRequiredLevel(),
                        question.getQuizYear()
                );
        if (hasAttempt) {
            throw new IllegalStateException(
                    "해당 계층·연도에 응시 이력이 존재하여 물리 삭제가 불가합니다. 비활성화를 사용하세요.");
        }

        quizQuestionRepository.delete(question);
    }

    /**
     * [관리자] 문항 목록 조회 (카테고리·등급·연도 필터)
     */
    @Transactional(readOnly = true)
    public List<QuizQuestionAdminResponseDto> getQuestions(
            Integer categoryId, RequiredLevel requiredLevel, Integer quizYear) {
        return quizQuestionRepository.findByFilters(categoryId, requiredLevel, quizYear)
                .stream()
                .map(QuizQuestionAdminResponseDto::from)
                .toList();
    }

    /**
     * [관리자] 특정 연도 문항 미등록/미완성 계층 목록 조회 (대시보드 경고 배너용)
     */
    @Transactional(readOnly = true)
    public List<Object[]> getUnderRegisteredTiers(int year) {
        return quizQuestionRepository.findUnderRegisteredTiers(year);
    }

    /**
     * [관리자] 기사별 응시 이력 조회
     */
    @Transactional(readOnly = true)
    public List<QuizAttemptResponseDto> getAttemptsByEngineer(Long engineerUserId) {
        return quizAttemptRepository
                .findByUser_IdOrderByQuizYearDescAttemptedAtDesc(engineerUserId)
                .stream()
                .map(QuizAttemptResponseDto::from)
                .toList();
    }

    // ─────────────────────────────────────────────
    // 대행사용
    // ─────────────────────────────────────────────

    /**
     * [대행사] 소속 기사 퀴즈 합격 현황 목록
     */
    @Transactional(readOnly = true)
    public List<QuizAttemptResponseDto> getAttemptsByEngineerForAgency(
            Long engineerUserId, Integer quizYear) {
        return quizAttemptRepository
                .findByUser_IdAndQuizYearOrderByAttemptedAtDesc(engineerUserId, quizYear)
                .stream()
                .map(QuizAttemptResponseDto::from)
                .toList();
    }

    // ─────────────────────────────────────────────
    // 내부 로직
    // ─────────────────────────────────────────────

    /**
     * 응시 자격 검증
     * 순서: 합격 여부 → 문항 등록 여부 → 콘텐츠 이수 여부 → 응시 횟수
     */
    private void validateEligibility(Long engineerUserId, Integer categoryId,
                                     RequiredLevel level, int currentYear) {
        // 1. 이미 합격한 계층인지 확인
        if (quizAttemptRepository.existsByUserIdAndCategoryIdAndRequiredLevelAndQuizYearAndIsPassed(
                engineerUserId, categoryId, level, currentYear, true)) {
            throw new IllegalStateException("이미 합격한 시험입니다.");
        }

        // 2. 신년도 문항 5개 등록 여부 확인
        long questionCount = quizQuestionRepository
                .countByCategoryIdAndRequiredLevelAndQuizYearAndIsActiveTrue(
                        categoryId, level, currentYear);
        if (questionCount < REQUIRED_QUESTIONS) {
            throw new IllegalStateException(
                    "현재 연도 문항이 등록되지 않았습니다. 관리자에게 문의하세요.");
        }

        // 3. 콘텐츠 전부 이수 여부 확인 (is_active=true 이수 이력 기준)
        if (!lmsService.isQuizEligible(engineerUserId)) {
            throw new IllegalStateException("콘텐츠를 모두 이수한 후 응시할 수 있습니다.");
        }

        // 4. 현재 사이클 응시 횟수 확인
        long attemptCount = quizAttemptRepository
                .countCurrentCycleAttempts(engineerUserId, categoryId, level, currentYear);
        if (attemptCount >= MAX_ATTEMPTS) {
            throw new IllegalStateException(
                    "최대 응시 횟수를 초과했습니다. 교육 콘텐츠를 재이수하세요.");
        }
    }

    /**
     * 재이수 강제 처리
     * lms_confirmations.is_active = false → 이수 현황 초기화 효과
     * 물리 삭제 없이 이력 보존
     */
    private void forceReLearn(Long userId, Integer categoryId, int year) {
        int deactivated = lmsConfirmationRepository
                .deactivateByUserIdAndCategoryIdAndYear(userId, categoryId, year);
        if (deactivated == 0) {
            // 이수 이력이 없는 경우 (정상 — 이미 재이수 강제된 상태)
            return;
        }
    }

    // ─────────────────────────────────────────────
    // 헬퍼
    // ─────────────────────────────────────────────

    private EngineerProfile getEngineerProfile(Long userId) {
        return engineerProfileRepository.findByUser_Id(userId)
                .orElseThrow(() -> new IllegalArgumentException("기사 프로필이 존재하지 않습니다."));
    }

    private User getUserOrThrow(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 사용자입니다."));
    }

    private QuizQuestion getQuestionOrThrow(Long questionId) {
        return quizQuestionRepository.findById(questionId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 문항입니다."));
    }
}