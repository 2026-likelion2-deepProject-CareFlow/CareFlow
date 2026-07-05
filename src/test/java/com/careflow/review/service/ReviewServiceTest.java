package com.careflow.review.service;

import com.careflow.as_request.entity.AsRequest;
import com.careflow.as_request.repository.AsRequestRepository;
import com.careflow.review.dto.EngineerReviewStatsResponse;
import com.careflow.review.dto.ReviewResponse;
import com.careflow.review.entity.Review;
import com.careflow.review.repository.ReviewRepository;
import com.careflow.user.entity.User;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

@ExtendWith(MockitoExtension.class)
@DisplayName("ReviewService 단위 테스트")
class ReviewServiceTest {

    @InjectMocks
    private ReviewService reviewService;

    @Mock
    private ReviewRepository reviewRepository;

    @Mock
    private AsRequestRepository asRequestRepository;

    @Test
    @DisplayName("성공: 리뷰 통계 조회 시 0건인 점수도 포함하여 1~5점 분포와 정확한 평균을 반환한다.")
    void getEngineerReviewStats_Success() {
        // Given
        Long engineerId = 1L;

        // DB에서 5점짜리 2개, 4점짜리 1개만 조회되었다고 가정 (3, 2, 1점은 없음)
        ReviewRepository.RatingCount count5 = mock(ReviewRepository.RatingCount.class);
        given(count5.getRating()).willReturn(5);
        given(count5.getCount()).willReturn(2L);

        ReviewRepository.RatingCount count4 = mock(ReviewRepository.RatingCount.class);
        given(count4.getRating()).willReturn(4);
        given(count4.getCount()).willReturn(1L);

        given(reviewRepository.countByRatingForEngineer(engineerId)).willReturn(List.of(count5, count4));

        // When
        EngineerReviewStatsResponse response = reviewService.getEngineerReviewStats(engineerId);

        // Then
        // 1. 총 리뷰 개수: 2 + 1 = 3
        assertThat(response.getTotalReviews()).isEqualTo(3L);

        // 2. 평균 평점: (5*2 + 4*1) / 3 = 14 / 3 = 4.666... -> 소수점 둘째 자리 반올림 -> 4.67
        assertThat(response.getAvgRating()).isEqualTo(4.67);

        // 3. 맵 분포 검증 (없는 3, 2, 1점도 반드시 0으로 들어있어야 함!)
        assertThat(response.getRatingDistribution())
                .containsEntry(5, 2L)
                .containsEntry(4, 1L)
                .containsEntry(3, 0L)
                .containsEntry(2, 0L)
                .containsEntry(1, 0L);

        // 4. 순서가 5점부터 1점 순으로 정렬되어 있는지 확인 (LinkedHashMap의 Key 반복자 테스트)
        List<Integer> keys = response.getRatingDistribution().keySet().stream().toList();
        assertThat(keys).containsExactly(5, 4, 3, 2, 1);
    }

    @Test
    @DisplayName("성공: 본인이 작성한 리뷰가 있으면 정상 반환한다.")
    void getMyReview_Success() throws IllegalAccessException {
        // Given
        Long customerId = 1L;
        Long requestId = 100L;
        Long engineerId = 10L;

        User customer = mock(User.class);
        given(customer.getId()).willReturn(customerId);

        AsRequest asRequest = mock(AsRequest.class);
        given(asRequest.getCustomer()).willReturn(customer);
        given(asRequestRepository.findById(requestId)).willReturn(Optional.of(asRequest));

        User engineer = mock(User.class);
        given(engineer.getId()).willReturn(engineerId);

        Review review = mock(Review.class);
        given(review.getId()).willReturn(500L);
        given(review.getEngineer()).willReturn(engineer);
        given(review.getRating()).willReturn(5);
        given(review.getContent()).willReturn("친절하고 꼼꼼하세요");
        given(review.getCreatedAt()).willReturn(LocalDateTime.of(2026, 7, 1, 10, 0));

        given(reviewRepository.findByAsRequest_Id(requestId)).willReturn(Optional.of(review));

        // When
        ReviewResponse response = reviewService.getMyReview(customerId, requestId);

        // Then
        assertThat(response.reviewId()).isEqualTo(500L);
        assertThat(response.requestId()).isEqualTo(requestId);
        assertThat(response.engineerId()).isEqualTo(engineerId);
        assertThat(response.rating()).isEqualTo(5);
        assertThat(response.content()).isEqualTo("친절하고 꼼꼼하세요");
    }

    @Test
    @DisplayName("예외: 아직 작성한 리뷰가 없으면 NoSuchElementException을 던진다.")
    void getMyReview_NotWritten_ThrowsNoSuchElement() {
        // Given
        Long customerId = 1L;
        Long requestId = 100L;

        User customer = mock(User.class);
        given(customer.getId()).willReturn(customerId);

        AsRequest asRequest = mock(AsRequest.class);
        given(asRequest.getCustomer()).willReturn(customer);
        given(asRequestRepository.findById(requestId)).willReturn(Optional.of(asRequest));

        given(reviewRepository.findByAsRequest_Id(requestId)).willReturn(Optional.empty());

        // When & Then
        assertThatThrownBy(() -> reviewService.getMyReview(customerId, requestId))
                .isInstanceOf(NoSuchElementException.class);
    }

    @Test
    @DisplayName("예외: 본인의 A/S 요청이 아니면 IllegalAccessException을 던진다.")
    void getMyReview_NotOwner_ThrowsIllegalAccess() {
        // Given
        Long customerId = 1L;
        Long otherCustomerId = 2L;
        Long requestId = 100L;

        User otherCustomer = mock(User.class);
        given(otherCustomer.getId()).willReturn(otherCustomerId);

        AsRequest asRequest = mock(AsRequest.class);
        given(asRequest.getCustomer()).willReturn(otherCustomer);
        given(asRequestRepository.findById(requestId)).willReturn(Optional.of(asRequest));

        // When & Then
        assertThatThrownBy(() -> reviewService.getMyReview(customerId, requestId))
                .isInstanceOf(IllegalAccessException.class);
    }
}