package com.careflow.quiz.repository;

import com.careflow.quiz.entity.QuizQuestion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface QuizQuestionRepository extends JpaRepository<QuizQuestion, Long> {

    // 응시 자격 검증 + 채점용: 현재 연도 활성 문항 조회
    List<QuizQuestion> findByCategoryIdAndRequiredLevelAndQuizYearAndIsActiveTrue(
            Integer categoryId,
            QuizQuestion.RequiredLevel requiredLevel,
            Integer quizYear
    );

    // 문항 수 확인 (5개 미만이면 응시 불가)
    long countByCategoryIdAndRequiredLevelAndQuizYearAndIsActiveTrue(
            Integer categoryId,
            QuizQuestion.RequiredLevel requiredLevel,
            Integer quizYear
    );

    // 관리자 목록 조회 (연도·카테고리·등급 필터, is_active 무관)
    @Query("""
        SELECT q FROM QuizQuestion q
        WHERE (:categoryId IS NULL OR q.category.categoryId = :categoryId)
          AND (:requiredLevel IS NULL OR q.requiredLevel = :requiredLevel)
          AND (:quizYear IS NULL OR q.quizYear = :quizYear)
        ORDER BY q.category.categoryId ASC, q.requiredLevel ASC, q.quizYear DESC, q.sortOrder ASC
        """)
    List<QuizQuestion> findByFilters(
            @Param("categoryId") Integer categoryId,
            @Param("requiredLevel") QuizQuestion.RequiredLevel requiredLevel,
            @Param("quizYear") Integer quizYear
    );

    // QuizYearRolloverJob — 전년도 문항 아카이브
    @Modifying
    @Query("UPDATE QuizQuestion q SET q.isActive = false, q.updatedAt = CURRENT_TIMESTAMP WHERE q.quizYear = :year")
    int deactivateByYear(@Param("year") int year);

    // QuizYearRolloverJob — 신년도 문항 활성화
    @Modifying
    @Query("UPDATE QuizQuestion q SET q.isActive = true, q.updatedAt = CURRENT_TIMESTAMP WHERE q.quizYear = :year")
    int activateByYear(@Param("year") int year);

    // 대시보드 경고 배너용: 특정 연도 5문제 미만 계층 조회
    @Query("""
        SELECT q.category.categoryId, q.requiredLevel, COUNT(q)
        FROM QuizQuestion q
        WHERE q.quizYear = :year AND q.isActive = true
        GROUP BY q.category.categoryId, q.requiredLevel
        HAVING COUNT(q) < 5
        """)
    List<Object[]> findUnderRegisteredTiers(@Param("year") int year);


}