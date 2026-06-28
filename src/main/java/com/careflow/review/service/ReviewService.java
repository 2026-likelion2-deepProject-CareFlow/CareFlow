package com.careflow.review.service;

import com.careflow.as_request.entity.AsRequest;
import com.careflow.as_request.repository.AsRequestRepository;
import com.careflow.common.enums.AsStatus;
import com.careflow.engineer.domain.entity.EngineerProfile;
import com.careflow.engineer.repository.EngineerProfileRepository;
import com.careflow.report.domain.entity.WorkReport;
import com.careflow.report.repository.WorkReportRepository;
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
import java.util.NoSuchElementException;

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
}
