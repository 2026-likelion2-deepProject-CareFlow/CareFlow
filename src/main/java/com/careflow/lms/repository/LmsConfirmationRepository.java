package com.careflow.lms.repository;

import com.careflow.lms.entity.LmsConfirmation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface LmsConfirmationRepository extends JpaRepository<LmsConfirmation, Long> {

    /**
     * [v10 변경] is_active 조건 추가 버전
     * OX퀴즈 응시 자격 판단 및 이수 현황 집계 시 사용
     * is_active=false인 재이수 강제 이력은 제외
     */
    @Query("""
        SELECT c.content.contentId FROM LmsConfirmation c
        WHERE c.user.id = :userId
          AND c.completionYear = :year
          AND c.isActive = :isActive
        """)
    List<Long> findContentIdsByUserIdAndYearAndIsActive(
            @Param("userId") Long userId,
            @Param("year") int year,
            @Param("isActive") boolean isActive
    );

    /**
     * [v10 변경] is_active 조건 추가 버전 — 이수 이력 엔티티 반환
     * getRequiredContentsWithStatus()에서 confirmedAt 함께 조회 시 사용
     */
    @Query("""
        SELECT c FROM LmsConfirmation c
        JOIN FETCH c.content
        WHERE c.user.id = :userId
          AND c.completionYear = :year
          AND c.isActive = :isActive
        ORDER BY c.confirmedAt ASC
        """)
    List<LmsConfirmation> findByUserIdAndYearAndIsActive(
            @Param("userId") Long userId,
            @Param("year") int year,
            @Param("isActive") boolean isActive
    );

    /**
     * [v10 신규] 재이수 강제 처리 — 해당 계층 이수 이력 일괄 논리 삭제
     * QuizService.forceReLearn()에서 호출
     * category_id는 lms_contents.category_id를 통해 조인하여 필터링
     */
    @Modifying
    @Query("""
        UPDATE LmsConfirmation lc
        SET lc.isActive = false, lc.updatedAt = CURRENT_TIMESTAMP
        WHERE lc.user.id = :userId
          AND lc.content.category.categoryId = :categoryId
          AND lc.completionYear = :year
          AND lc.isActive = true
        """)
    int deactivateByUserIdAndCategoryIdAndYear(
            @Param("userId") Long userId,
            @Param("categoryId") Integer categoryId,
            @Param("year") int year
    );


    /**
     * [신규] (user, content, year) 조합의 이수 이력을 활성 여부와 무관하게 단건 조회.
     * completeContent()에서 INSERT(최초 이수) vs UPDATE 재활성화(재이수) 분기 판단에 사용.
     * uk_lms_confirm_year UNIQUE 제약 덕분에 결과는 최대 1건이므로 Optional로 반환.
     */
    @Query("""
        SELECT c FROM LmsConfirmation c
        WHERE c.user.id = :userId
          AND c.content.contentId = :contentId
          AND c.completionYear = :year
        """)
    Optional<LmsConfirmation> findByUserIdAndContentIdAndYear(
            @Param("userId") Long userId,
            @Param("contentId") Long contentId,
            @Param("year") int year
    );

    @Query("""
        SELECT c FROM LmsConfirmation c
        JOIN FETCH c.content
        WHERE c.user.id = :userId
        ORDER BY c.completionYear DESC, c.confirmedAt DESC
        """)
    List<LmsConfirmation> findAllByUserIdWithContent(@Param("userId") Long userId);

    @Query("""
        SELECT c FROM LmsConfirmation c
        JOIN FETCH c.content
        WHERE c.user.id = :userId
          AND c.completionYear = :year
        ORDER BY c.confirmedAt ASC
        """)
    List<LmsConfirmation> findByUserIdAndYear(
            @Param("userId") Long userId,
            @Param("year") int year
    );

    @Query("""
        SELECT c FROM LmsConfirmation c
        JOIN FETCH c.user
        WHERE c.content.contentId = :contentId
          AND c.completionYear = :year
        ORDER BY c.confirmedAt ASC
        """)
    List<LmsConfirmation> findByContentIdAndYear(
            @Param("contentId") Long contentId,
            @Param("year") int year
    );

    @Query("""
    SELECT c FROM LmsConfirmation c
    JOIN FETCH c.content
    WHERE c.user.id = :userId
      AND c.isActive = :isActive
    ORDER BY c.completionYear DESC, c.confirmedAt DESC
    """)
    List<LmsConfirmation> findAllByUserIdAndIsActiveWithContent(
            @Param("userId") Long userId,
            @Param("isActive") boolean isActive
    );
}