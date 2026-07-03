package com.careflow.quiz.controller;

import com.careflow.auth.security.CustomUserDetails;
import com.careflow.quiz.dto.*;
import com.careflow.quiz.entity.QuizQuestion;
import com.careflow.quiz.service.QuizService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/quiz")
@RequiredArgsConstructor
public class QuizController {

    private final QuizService quizService;

    // ─────────────────────────────────────────────
    // 기사용 API
    // ─────────────────────────────────────────────

    /**
     * 응시 자격 현황 조회
     * GET /api/quiz/status
     */
    @GetMapping("/status")
    @PreAuthorize("hasRole('ENGINEER')")
    public ResponseEntity<QuizStatusDto> getQuizStatus(
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        return ResponseEntity.ok(quizService.getQuizStatus(userDetails.getUserId()));
    }

    /**
     * 본인 계층 현재 연도 문항 조회 (정답 제외)
     * GET /api/quiz/questions
     */
    @GetMapping("/questions")
    @PreAuthorize("hasRole('ENGINEER')")
    public ResponseEntity<List<QuizQuestionResponseDto>> getQuestions(
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        return ResponseEntity.ok(quizService.getQuestions(userDetails.getUserId()));
    }

    /**
     * 퀴즈 제출 및 채점
     * POST /api/quiz/attempts
     */
    @PostMapping("/attempts")
    @PreAuthorize("hasRole('ENGINEER')")
    public ResponseEntity<QuizResultDto> submitQuiz(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @RequestBody QuizSubmitDto dto) {
        return ResponseEntity.ok(
                quizService.submitQuiz(userDetails.getUserId(), dto));
    }

    /**
     * 본인 응시 이력 조회
     * GET /api/quiz/attempts/me
     */
    @GetMapping("/attempts/me")
    @PreAuthorize("hasRole('ENGINEER')")
    public ResponseEntity<List<QuizAttemptResponseDto>> getMyAttempts(
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        return ResponseEntity.ok(quizService.getMyAttempts(userDetails.getUserId()));
    }

    // ─────────────────────────────────────────────
    // 관리자용 API
    // ─────────────────────────────────────────────

    /**
     * 문항 등록
     * POST /api/quiz/admin/questions
     */
    @PostMapping("/admin/questions")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> createQuestion(
            @RequestBody QuizQuestionCreateDto dto,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        quizService.createQuestion(dto, userDetails.getUserId());
        return ResponseEntity.ok().build();
    }

    /**
     * 문항 목록 조회 (카테고리·등급·연도 필터)
     * GET /api/quiz/admin/questions?categoryId=&level=&year=
     */
    @GetMapping("/admin/questions")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<QuizQuestionAdminResponseDto>> getAdminQuestions(
            @RequestParam(required = false) Integer categoryId,
            @RequestParam(required = false) QuizQuestion.RequiredLevel requiredLevel,
            @RequestParam(required = false) Integer quizYear) {
        return ResponseEntity.ok(
                quizService.getQuestions(categoryId, requiredLevel, quizYear));
    }

    /**
     * 문항 수정
     * PUT /api/quiz/admin/questions/{questionId}
     */
    @PutMapping("/admin/questions/{questionId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> updateQuestion(
            @PathVariable Long questionId,
            @RequestBody QuizQuestionUpdateDto dto) {
        quizService.updateQuestion(questionId, dto);
        return ResponseEntity.ok().build();
    }

    /**
     * 문항 비활성화
     * PATCH /api/quiz/admin/questions/{questionId}/deactivate
     */
    @PatchMapping("/admin/questions/{questionId}/deactivate")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> deactivateQuestion(@PathVariable Long questionId) {
        quizService.deactivateQuestion(questionId);
        return ResponseEntity.ok().build();
    }

    /**
     * 문항 삭제 (응시 이력 없는 경우만)
     * DELETE /api/quiz/admin/questions/{questionId}
     */
    @DeleteMapping("/admin/questions/{questionId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> deleteQuestion(@PathVariable Long questionId) {
        quizService.deleteQuestion(questionId);
        return ResponseEntity.ok().build();
    }

    /**
     * 특정 연도 미등록/미완성 계층 조회 (대시보드 경고 배너용)
     * GET /api/quiz/admin/unregistered-tiers?year=2027
     */
    @GetMapping("/admin/unregistered-tiers")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<Object[]>> getUnderRegisteredTiers(
            @RequestParam(required = false) Integer year) {
        int targetYear = (year != null) ? year : LocalDate.now().getYear();
        return ResponseEntity.ok(quizService.getUnderRegisteredTiers(targetYear));
    }

    /**
     * 기사별 응시 이력 조회
     * GET /api/quiz/admin/attempts?engineerUserId=
     */
    @GetMapping("/admin/attempts")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<QuizAttemptResponseDto>> getAttemptsByEngineer(
            @RequestParam Long engineerUserId) {
        return ResponseEntity.ok(quizService.getAttemptsByEngineer(engineerUserId));
    }

    // ─────────────────────────────────────────────
    // 대행사용 API
    // ─────────────────────────────────────────────

    /**
     * 소속 기사 응시 이력 조회
     * GET /api/quiz/agency/{agencyId}/engineers/{engineerUserId}/attempts?year=
     */
    @GetMapping("/agency/{agencyId}/engineers/{engineerUserId}/attempts")
    @PreAuthorize("hasAnyRole('ADMIN','AGENCY')")
    public ResponseEntity<List<QuizAttemptResponseDto>> getAttemptsByEngineerForAgency(
            @PathVariable Long agencyId,
            @PathVariable Long engineerUserId,
            @RequestParam(required = false) Integer year) {
        int targetYear = (year != null) ? year : LocalDate.now().getYear();
        return ResponseEntity.ok(
                quizService.getAttemptsByEngineerForAgency(engineerUserId, targetYear));
    }
}