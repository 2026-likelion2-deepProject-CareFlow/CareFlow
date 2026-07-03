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
    //
    // [수정] 이전 버전은 lms_confirmations.is_active=false가 된 시점(=재이수 강제 시점)을
    // 사이클 경계로 삼았는데, 재이수를 완료하면 is_active가 다시 true로 바뀌면서
    // 그 경계 신호 자체가 사라져버려 재이수 직후 예전 응시 이력까지 전부 다시 카운트되는
    // 버그가 있었음 (LmsConfirmation.reactivate() 도입으로 인한 회귀).
    //
    // 대신 "현재 활성 상태인(is_active=true) 이수 이력의 confirmed_at"을 사이클 경계로 삼는다.
    // confirmed_at은 최초 이수든 재이수든 매번 "이수를 완료(갱신)한 시각"으로 채워지고,
    // is_active=true 상태에서는 절대 사라지지 않는 값이라 안정적인 앵커가 된다.
    // → 재이수를 몇 번을 반복하든, 매번 그 시점 이후의 응시만 정확히 카운트됨.
    @Query("""
        SELECT COUNT(qa) FROM QuizAttempt qa
        WHERE qa.user.id = :userId
          AND qa.categoryId = :categoryId
          AND qa.requiredLevel = :level
          AND qa.quizYear = :year
          AND qa.attemptedAt >= (
              SELECT COALESCE(MAX(lc.confirmedAt), '1970-01-01 00:00:00')
              FROM LmsConfirmation lc
              WHERE lc.user.id = :userId
                AND lc.content.category.categoryId = :categoryId
                AND lc.completionYear = :year
                AND lc.isActive = true
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