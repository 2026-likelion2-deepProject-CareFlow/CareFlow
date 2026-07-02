package com.careflow.review.dto;

import lombok.Builder;
import lombok.Getter;

import java.util.Map;

/**
 * 기사 본인 리뷰 통계 응답 (GET /api/engineer/reviews/stats)
 *
 * <p>프론트 확정 스펙에 맞춘 응답 형식:</p>
 * <pre>
 * {
 *   "avgRating": 4.5,
 *   "totalReviews": 19,
 *   "ratingDistribution": { "5": 12, "4": 5, "3": 1, "2": 1, "1": 0 }
 * }
 * </pre>
 *
 * <ul>
 *   <li>avgRating         : 공개 리뷰 기준 평균 평점 (분포로부터 Σ(rating×count)/total 로 산정 → 분포와 항상 정합)</li>
 *   <li>totalReviews      : 공개 리뷰 총 건수 = 프론트 '전체' 탭 카운트</li>
 *   <li>ratingDistribution: 1~5점 각각의 리뷰 개수 (각 점수 탭 카운트). 리뷰 0건인 점수도 0 으로 포함.
 *                            키 순서는 5→1 (LinkedHashMap). JSON 직렬화 시 키는 문자열("5"~"1")이 됨.</li>
 * </ul>
 *
 * <p>모든 값은 공개 리뷰(is_visible = true) 기준 — 목록 API(GET /api/engineer/reviews)의 모수와 동일하다.</p>
 */
@Getter
@Builder
public class EngineerReviewStatsResponse {

    private double avgRating;

    private long totalReviews;

    private Map<Integer, Long> ratingDistribution;
}
