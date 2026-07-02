package com.careflow.review.service;

import com.careflow.review.dto.EngineerReviewStatsResponse;
import com.careflow.review.repository.ReviewRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

@ExtendWith(MockitoExtension.class)
@DisplayName("ReviewService 단위 테스트")
class ReviewServiceTest {

    @InjectMocks
    private ReviewService reviewService;

    @Mock
    private ReviewRepository reviewRepository;

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
}