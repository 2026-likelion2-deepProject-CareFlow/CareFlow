package com.careflow.lms.repository;

import com.careflow.lms.entity.LmsContent;
import com.careflow.lms.entity.LmsContent.RequiredLevel;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface LmsContentRepository extends JpaRepository<LmsContent, Long> {

    /**
     * [기사용] 이수 대상 콘텐츠 목록 조회
     *
     * 기사의 category_id + skill_level 기준으로 필터링
     * required_level = 'ALL' 인 콘텐츠는 등급 무관 전원 이수 대상이므로 IN절로 함께 조회
     *
     * 사용 인덱스: idx_lms_category_level (category_id, required_level, is_active)
     *
     * 호출 예시 (Service):
     *   findRequiredContents(categoryId, RequiredLevel.INTERMEDIATE)
     *   → INTERMEDIATE 콘텐츠 + ALL 콘텐츠 반환
     */
    @Query("""
        SELECT c FROM LmsContent c
        WHERE c.category.categoryId = :categoryId
          AND c.requiredLevel IN (:skillLevel, com.careflow.lms.entity.LmsContent$RequiredLevel.ALL)
          AND c.isActive = true
        ORDER BY c.requiredLevel, c.contentId
        """)
    List<LmsContent> findRequiredContents(
            @Param("categoryId") Integer categoryId,
            @Param("skillLevel") RequiredLevel skillLevel
    );


    // LMS 필터링 조회
    @Query("""
    SELECT c FROM LmsContent c
    WHERE (:categoryId IS NULL OR c.category.categoryId = :categoryId)
      AND (:requiredLevel IS NULL OR c.requiredLevel = :requiredLevel)
      AND (:isActive IS NULL OR c.isActive = :isActive)
    ORDER BY c.category.categoryId ASC, c.requiredLevel ASC, c.contentId ASC
    """)
    List<LmsContent> findByFilters(
            @Param("categoryId") Integer categoryId,
            @Param("requiredLevel") RequiredLevel requiredLevel,
            @Param("isActive") Boolean isActive
    );


    /**
     * [관리자용] 전체 콘텐츠 목록 (비활성 포함)
     * M-13: 관리자 전체 콘텐츠 관리 화면
     */
    List<LmsContent> findAllByOrderByCategoryCategoryIdAscRequiredLevelAscContentIdAsc();

}