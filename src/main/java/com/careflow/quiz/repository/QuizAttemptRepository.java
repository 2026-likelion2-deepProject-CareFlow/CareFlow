package com.careflow.quiz.repository;

import com.careflow.quiz.entity.QuizAttempt;
import com.careflow.quiz.entity.QuizQuestion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface QuizAttemptRepository extends JpaRepository<QuizAttempt, Long> {

    // 합격 여부 확인 (현재 연도 기준)
    boolean existsByUserIdAndCategoryIdAndRequiredLevelAndQuizYearAndIsPassed(
            Long userId,
            Integer categoryId,
            QuizQuestion.RequiredLevel requiredLevel,
            Integer quizYear,
            boolean isPassed
    );

    // 기사 본인 응시 이력 전체 조회 (최신순)
    List<QuizAttempt> findByUser_IdOrderByAttemptedAtDesc(Long userId);

    // 대행사용: 특정 기사 응시 이력
    List<QuizAttempt> findByUser_IdAndQuizYearOrderByAttemptedAtDesc(Long userId, Integer quizYear);

    // 현재 사이클 응시 횟수 집계
    // lms_confirmations.is_active=0이 된 최신 시각 이후의 attempts만 카운트
    @Query("""
        SELECT COUNT(qa) FROM QuizAttempt qa
        WHERE qa.user.id = :userId
          AND qa.categoryId = :categoryId
          AND qa.requiredLevel = :level
          AND qa.quizYear = :year
          AND qa.attemptedAt >= (
              SELECT COALESCE(MAX(lc.updatedAt), '1970-01-01 00:00:00')
              FROM LmsConfirmation lc
              WHERE lc.user.id = :userId
                AND lc.content.category.categoryId = :categoryId
                AND lc.completionYear = :year
                AND lc.isActive = false
          )
        """)
    long countCurrentCycleAttempts(
            @Param("userId") Long userId,
            @Param("categoryId") Integer categoryId,
            @Param("level") QuizQuestion.RequiredLevel level,
            @Param("year") int year
    );

    // 물리 삭제 가능 여부 판단용 (응시 이력이 하나라도 있으면 물리 삭제 불가)
    boolean existsByCategoryIdAndRequiredLevelAndQuizYear(
            Integer categoryId,
            QuizQuestion.RequiredLevel requiredLevel,
            Integer quizYear
    );

    // 관리자용: 특정 기사 전체 응시 이력
    List<QuizAttempt> findByUser_IdOrderByQuizYearDescAttemptedAtDesc(Long userId);
}