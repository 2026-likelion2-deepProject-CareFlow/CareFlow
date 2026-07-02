package com.careflow.review.service;

import com.careflow.as_request.entity.AsRequest;
import com.careflow.as_request.repository.AsRequestRepository;
import com.careflow.common.enums.AsStatus;
import com.careflow.engineer.domain.entity.EngineerProfile;
import com.careflow.engineer.repository.EngineerProfileRepository;
import com.careflow.report.domain.entity.WorkReport;
import com.careflow.report.repository.WorkReportRepository;
import com.careflow.review.dto.EngineerReviewResponse;
import com.careflow.review.dto.EngineerReviewStatsResponse;
import com.careflow.review.dto.ReviewCreateRequest;
import com.careflow.review.dto.ReviewResponse;
import com.careflow.review.entity.Review;
import com.careflow.review.repository.ReviewRepository;
import com.careflow.user.entity.User;
import com.careflow.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ReviewService {

    private final ReviewRepository reviewRepository;
    private final AsRequestRepository asRequestRepository;
    private final UserRepository userRepository;
    private final WorkReportRepository workReportRepository;
    private final EngineerProfileRepository engineerProfileRepository;

    /**
     * 고객 리뷰 작성 (C-23)
     *
     * 처리 순서:
     * 1. A/S 요청 조회 및 본인 소유 검증
     * 2. PAID 상태 확인 (결제 완료 후에만 리뷰 가능)
     * 3. 중복 리뷰 방지
     * 4. WorkReport에서 실제 작업한 기사 조회
     * 5. Review 생성 및 저장
     * 6. 기사 프로필 avgRating, totalReviews 역정규화 필드 갱신
     */
    @Transactional
    public ReviewResponse createReview(Long customerId, Long requestId, ReviewCreateRequest dto)
            throws IllegalAccessException {

        // 1. A/S 요청 조회
        AsRequest asRequest = asRequestRepository.findById(requestId)
                .orElseThrow(() -> new NoSuchElementException("존재하지 않는 A/S 요청입니다."));

        // 본인 요청인지 검증 — 타인의 request_id로 리뷰 시도 차단
        if (!asRequest.getCustomer().getId().equals(customerId)) {
            throw new IllegalAccessException("본인의 A/S 요청에 대해서만 리뷰를 작성할 수 있습니다.");
        }

        // 2. PAID 상태인지 확인 (결제 완료 후에만 리뷰 허용)
        if (asRequest.getStatus() != AsStatus.PAID) {
            throw new IllegalStateException("결제가 완료된 건만 리뷰를 작성할 수 있습니다.");
        }

        // 3. 중복 리뷰 방지
        if (reviewRepository.existsByAsRequest_Id(requestId)) {
            throw new IllegalStateException("이미 리뷰를 작성한 요청입니다.");
        }

        // 4. 실제 작업한 기사 조회 — AsAssignment(배정)가 아닌 WorkReport(보고서 제출자)가 실제 작업 기사
        WorkReport workReport = workReportRepository.findByAsRequest_Id(requestId)
                .orElseThrow(() -> new NoSuchElementException("작업 완료 보고서가 존재하지 않습니다."));
        User engineer = workReport.getEngineer();

        // 고객 엔티티 조회 (Review FK 세팅용)
        User customer = userRepository.findById(customerId)
                .orElseThrow(() -> new NoSuchElementException("존재하지 않는 사용자입니다."));

        // 5. Review 생성 및 저장
        Review review = Review.create(asRequest, customer, engineer, dto.rating(), dto.content());
        reviewRepository.save(review);

        // 6. 기사 프로필 avgRating, totalReviews 갱신
        // 새 평균 = (기존 평균 * 기존 리뷰 수 + 새 평점) / (기존 리뷰 수 + 1)
        EngineerProfile engineerProfile = engineerProfileRepository.findByUser_Id(engineer.getId())
                .orElseThrow(() -> new NoSuchElementException("기사 프로필이 존재하지 않습니다."));

        int prevTotal = engineerProfile.getTotalReviews();
        BigDecimal newAvg = engineerProfile.getAvgRating()
                .multiply(BigDecimal.valueOf(prevTotal))
                .add(BigDecimal.valueOf(dto.rating()))
                .divide(BigDecimal.valueOf(prevTotal + 1), 2, RoundingMode.HALF_UP);
        engineerProfile.updateRating(newAvg, prevTotal + 1);

        return new ReviewResponse(
                review.getId(),
                requestId,
                engineer.getId(),
                review.getRating(),
                review.getContent(),
                review.getCreatedAt()
        );
    }

    /**
     * 수리기사(ENGINEER)용: 본인에게 달린 리뷰 목록 조회
     */
    @Transactional(readOnly = true)
    public List<EngineerReviewResponse> getEngineerReviews(Long engineerId) {
        // Repository에 이미 만들어둔 메서드를 활용!
        List<Review> reviews = reviewRepository.findByEngineer_Id(engineerId);

        return reviews.stream()
                // 최신 리뷰가 위로 오도록 정렬
                .sorted((r1, r2) -> r2.getCreatedAt().compareTo(r1.getCreatedAt()))
                .map(EngineerReviewResponse::from)
                .collect(Collectors.toList());
    }

    /**
     * 수리기사(ENGINEER)용: 본인에게 달린 리뷰 목록 조회 (페이징 + 별점 필터)
     */
    @Transactional(readOnly = true)
    public org.springframework.data.domain.Page<EngineerReviewResponse> getEngineerReviewsPaging(Long engineerId, Integer rating, org.springframework.data.domain.Pageable pageable) {
        org.springframework.data.domain.Page<Review> reviews = reviewRepository.findVisibleByEngineerIdAndRatingWithPaging(engineerId, rating, pageable);
        return reviews.map(EngineerReviewResponse::from);
    }

    /**
     * 수리기사(ENGINEER)용: 본인 리뷰 통계 (평점 분포 + 탭 카운트)
     *
     * <p>목록 API와 분리된 additive 엔드포인트(GET /api/engineer/reviews/stats)의 서비스 로직.</p>
     * <ul>
     * <li>분포/카
     * 운트는 공개(isVisible=true) 리뷰 기준 — 목록 API의 모수와 동일.</li>
     * <li>평균은 분포로부터 산정(Σ(rating×count)/total)하여 분포와 항상 정합하도록 함.
     * (engineer_profiles.avg_rating 은 비공개 리뷰까지 반영된 누적 평균이라 값이 다를 수 있어 재사용하지 않음)</li>
     * <li>리뷰가 0건인 점수도 count=0 으로 반드시 포함(프론트 탭 렌더링용).</li>
     * </ul>
     */
    @Transactional(readOnly = true)
    public EngineerReviewStatsResponse getEngineerReviewStats(Long engineerId) {
        // 1) 1~5 버킷을 0으로 초기화 (5점부터 1점 순서대로 응답하기 위해 LinkedHashMap 사용)
        java.util.Map<Integer, Long> counts = new java.util.LinkedHashMap<>();
        for (int score = 5; score >= 1; score--) {
            counts.put(score, 0L);
        }

        // 2) GROUP BY 집계 결과를 버킷에 반영
        for (ReviewRepository.RatingCount row : reviewRepository.countByRatingForEngineer(engineerId)) {
            if (row.getRating() != null) {
                counts.put(row.getRating(), row.getCount() != null ? row.getCount() : 0L);
            }
        }

        // 3) 전체 개수 및 가중합(Σ rating×count) 산정 → 평균은 분포로부터 도출
        long totalCount = 0L;
        long weightedSum = 0L;
        for (int score = 1; score <= 5; score++) {
            long c = counts.get(score);
            totalCount += c;
            weightedSum += (long) score * c;
        }
        double averageRating = totalCount > 0
                ? Math.round((double) weightedSum / totalCount * 100.0) / 100.0
                : 0.0;

        // 4) DTO 필드명에 정확히 맞춰서 빌더 호출! (List -> Map으로 직접 전달)
        return EngineerReviewStatsResponse.builder()
                .avgRating(averageRating)          // DTO의 avgRating 필드에 매핑
                .totalReviews(totalCount)          // DTO의 totalReviews 필드에 매핑
                .ratingDistribution(counts)        // 조립된 Map을 그대로 전달
                .build();
    }
}
