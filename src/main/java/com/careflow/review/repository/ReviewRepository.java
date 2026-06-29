package com.careflow.review.repository;

import com.careflow.review.entity.Review;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface ReviewRepository extends JpaRepository<Review, Long> {

    // 동일 A/S 건에 대한 중복 리뷰 방지
    boolean existsByAsRequest_Id(Long requestId);

    // 특정 기사가 받은 리뷰 목록 조회
    List<Review> findByEngineer_Id(Long engineerId);

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
}
