package com.careflow.review.repository;

import com.careflow.agency.dto.response.AgencyReviewListResponse;
import com.careflow.review.entity.Review;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface ReviewRepository extends JpaRepository<Review, Long> {

    // 동일 A/S 건에 대한 중복 리뷰 방지
    boolean existsByAsRequest_Id(Long requestId);

    // 특정 기사가 받은 리뷰 목록 조회
    List<Review> findByEngineer_Id(Long engineerId);

    // request_id 단건 조회 (완료 목록 상세용)
    Optional<Review> findByAsRequest_Id(Long requestId);

    // request_id 복수 조회 — 완료 배정 목록 N+1 방지 배치용
    @Query("SELECT r FROM Review r WHERE r.asRequest.id IN :requestIds")
    List<Review> findByAsRequestIdIn(@Param("requestIds") List<Long> requestIds);

    /**
     * 특정 기사의 공개 리뷰 목록 조회 (최신순)
     * GET /api/agency/engineers/{id}/reviews 에서 사용
     */
    @Query("""
            SELECT r FROM Review r
            JOIN FETCH r.customer
            WHERE r.engineer.id = :engineerId
              AND r.isVisible = true
            ORDER BY r.createdAt DESC
            """)
    List<Review> findVisibleByEngineerId(@Param("engineerId") Long engineerId);

    /**
     * 특정 기사들의 해당 월 평균 평점 조회
     * - 기사 ID 목록과 날짜 범위를 기준으로 그룹핑
     * - 해당 기간에 리뷰가 없는 기사는 결과에 포함되지 않음 (JOIN 방식으로 처리)
     */
    @Query("""
            SELECT r.engineer.id AS engineerId,
                   AVG(CAST(r.rating AS double)) AS avgRating
            FROM Review r
            WHERE r.engineer.id IN :engineerIds
              AND r.createdAt >= :from
              AND r.createdAt < :to
              AND r.isVisible = true
            GROUP BY r.engineer.id
            """)
    List<EngineerAvgRating> findAvgRatingByEngineers(
            @Param("engineerIds") List<Long> engineerIds,
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to);

    /**
     * 기사별 평균 평점 결과를 받을 인터페이스 프로젝션
     */
    interface EngineerAvgRating {
        Long getEngineerId();
        Double getAvgRating();
    }

    // ─────────────────────────────────────────────────────────────
    // GET /api/agency/reviews 전용 쿼리
    // ─────────────────────────────────────────────────────────────

    /**
     * 대행사 리뷰 목록 조회 (필터 + 페이징)
     * - as_requests.agency_id 로 대행사 소속 리뷰만 필터링
     * - keyword: 고객명 / 기사명 / 주문번호(requestId) 부분 일치
     * - rating / engineerId / isVisible / dateFrom / dateTo 조건 선택적 적용
     */
    @Query("""
            SELECT new com.careflow.agency.dto.response.AgencyReviewListResponse$ReviewSummary(
                r.id,
                r.asRequest.id,
                r.customer.name,
                r.engineer.id,
                r.engineer.name,
                r.asRequest.agency.agencyName,
                r.asRequest.appliance.brand,
                r.asRequest.appliance.modelName,
                CAST(r.asRequest.scheduledDate AS string),
                r.asRequest.scheduledTime,
                r.rating,
                r.content,
                r.isVisible,
                r.createdAt
            )
            FROM Review r
            WHERE r.asRequest.agency.id = :agencyId
              AND (:rating IS NULL OR r.rating = :rating)
              AND (:engineerId IS NULL OR r.engineer.id = :engineerId)
              AND (:isVisible IS NULL OR r.isVisible = :isVisible)
              AND (:dateFrom IS NULL OR r.createdAt >= :dateFrom)
              AND (:dateTo IS NULL OR r.createdAt < :dateTo)
              AND (:keyword IS NULL
                   OR r.customer.name LIKE %:keyword%
                   OR r.engineer.name LIKE %:keyword%)
            ORDER BY r.createdAt DESC
            """)
    Page<AgencyReviewListResponse.ReviewSummary> findAgencyReviews(
            @Param("agencyId") Long agencyId,
            @Param("rating") Integer rating,
            @Param("engineerId") Long engineerId,
            @Param("isVisible") Boolean isVisible,
            @Param("dateFrom") LocalDateTime dateFrom,
            @Param("dateTo") LocalDateTime dateTo,
            @Param("keyword") String keyword,
            Pageable pageable);

    /**
     * 대행사 전체 리뷰 통계 집계 (stats 용)
     * - 검색 필터 무관, 항상 전체 모수 기준
     * - result.get(0): Object[] { totalCount(Long), avgRating(Double), fiveStarCount(Long) }
     */
    @Query("""
            SELECT COUNT(r),
                   COALESCE(AVG(CAST(r.rating AS double)), 0.0),
                   SUM(CASE WHEN r.rating = 5 THEN 1L ELSE 0L END)
            FROM Review r
            WHERE r.asRequest.agency.id = :agencyId
            """)
    List<Object[]> findAgencyReviewStats(@Param("agencyId") Long agencyId);

    /**
     * 대행사 특정 기간 리뷰 수 및 평균 평점 조회 (stats 이번달/전월 비교용)
     * - result.get(0): Object[] { count(Long), avgRating(Double) }
     */
    @Query("""
            SELECT COUNT(r),
                   COALESCE(AVG(CAST(r.rating AS double)), 0.0)
            FROM Review r
            WHERE r.asRequest.agency.id = :agencyId
              AND r.createdAt >= :from
              AND r.createdAt < :to
            """)
    List<Object[]> findAgencyReviewStatsByPeriod(
            @Param("agencyId") Long agencyId,
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to);


    /**
     * [기사용] 특정 기사의 공개 리뷰 목록 페이징 조회 (별점 필터 포함, N+1 방지)
     */
    @Query(value = "SELECT r FROM Review r " +
            "JOIN FETCH r.customer " +
            "JOIN FETCH r.asRequest req " +
            "JOIN FETCH req.appliance app " +
            "JOIN FETCH app.category " +
            "WHERE r.engineer.id = :engineerId " +
            "AND r.isVisible = true " +
            "AND (:rating IS NULL OR r.rating = :rating) " +
            "ORDER BY r.createdAt DESC",
            countQuery = "SELECT COUNT(r) FROM Review r WHERE r.engineer.id = :engineerId AND r.isVisible = true AND (:rating IS NULL OR r.rating = :rating)")
    org.springframework.data.domain.Page<Review> findVisibleByEngineerIdAndRatingWithPaging(
            @org.springframework.data.repository.query.Param("engineerId") Long engineerId,
            @org.springframework.data.repository.query.Param("rating") Integer rating,
            org.springframework.data.domain.Pageable pageable);
}
