package com.careflow.lms.repository;

import com.careflow.lms.entity.LmsConfirmation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface LmsConfirmationRepository extends JpaRepository<LmsConfirmation, Long> {

    /**
     * [이수 완료 판정] 기사의 당해 연도 이수 콘텐츠 ID 목록 조회
     *
     * Service에서 아래 흐름으로 완료 판정에 사용:
     *   1. findRequiredContents() → 이수 대상 content_id 목록
     *   2. findContentIdsByUserIdAndYear() → 이미 이수한 content_id 목록
     *   3. 두 집합이 일치하면 is_lms_completed = 1 업데이트
     *
     * 사용 인덱스: idx_lms_confirm_user_year (user_id, completion_year)
     */
    @Query("""
        SELECT c.content.contentId FROM LmsConfirmation c
        WHERE c.user.id = :userId
          AND c.completionYear = :year
        """)
    List<Long> findContentIdsByUserIdAndYear(
            @Param("userId") Long userId,
            @Param("year") int year
    );

    /**
     * 단건 이수 여부 체크 (중복 이수 방지 사전 검증)
     *
     * DB에 uk_lms_confirm_year가 있어서 중복 시 DataIntegrityViolationException이 발생하지만,
     * 예외 발생 전 사전 체크로 더 명확한 에러 메시지를 반환할 수 있도록 제공
     *
     * 사용 인덱스: uk_lms_confirm_year (user_id, content_id, completion_year) — UK가 인덱스 역할도 수행
     */
    boolean existsByUserIdAndContentContentIdAndCompletionYear(
            Long userId, Long contentId, int completionYear
    );

    /**
     * [관리자용] 특정 기사의 연도별 이수 이력 전체 조회
     * M-14: 기사별 연도별 LMS 이수 현황 조회
     *
     * 사용 인덱스: idx_lms_confirm_user_year (user_id, completion_year)
     */
    @Query("""
        SELECT c FROM LmsConfirmation c
        JOIN FETCH c.content
        WHERE c.user.id = :userId
        ORDER BY c.completionYear DESC, c.confirmedAt DESC
        """)
    List<LmsConfirmation> findAllByUserIdWithContent(@Param("userId") Long userId);

    /**
     * [관리자용] 특정 연도 기준 기사별 이수 현황 조회
     * M-14: 연도 필터 적용 버전
     *
     * 사용 인덱스: idx_lms_confirm_user_year (user_id, completion_year)
     */
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

    /**
     * [관리자용] 특정 콘텐츠의 이수자 목록 조회 (이수 시각 순)
     * M-14: 콘텐츠별 이수 현황 확인
     *
     * 사용 인덱스: idx_lms_confirm_content (content_id, confirmed_at)
     */
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
}