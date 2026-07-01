package com.careflow.agency.service;

import com.careflow.agency.dto.request.AgencyReviewSearchRequest;
import com.careflow.agency.dto.response.AgencyReviewListResponse;
import com.careflow.auth.security.CustomUserDetails;
import com.careflow.review.repository.ReviewRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("AgencyReviewService 단위 테스트")
class AgencyReviewServiceTest {

    @InjectMocks
    private AgencyReviewService agencyReviewService;

    @Mock
    private ReviewRepository reviewRepository;

    private static final Long AGENCY_ID = 100L;
    private static final Pageable PAGEABLE = PageRequest.of(0, 10);

    private CustomUserDetails agencyUser;
    private CustomUserDetails engineerUser;

    // stats 반환용 더미 List<Object[]> 헬퍼
    private List<Object[]> statsResult(long count, double avg, long fiveStarCount) {
        List<Object[]> list = new java.util.ArrayList<>();
        list.add(new Object[]{count, avg, fiveStarCount});
        return list;
    }

    private List<Object[]> periodResult(long count, double avg) {
        List<Object[]> list = new java.util.ArrayList<>();
        list.add(new Object[]{count, avg});
        return list;
    }

    @BeforeEach
    void setUp() {
        agencyUser  = new CustomUserDetails(1L, "agency@test.com", "", "AGENCY", AGENCY_ID);
        engineerUser = new CustomUserDetails(2L, "eng@test.com", "", "ENGINEER", AGENCY_ID);
    }

    @Nested
    @DisplayName("TC-1. 정상 조회 — 리뷰 2건, 필터 없음")
    class NormalCase {

        @Test
        void 리뷰_2건_존재시_content_size_2_stats_정상매핑() throws Exception {
            // given
            given(reviewRepository.findAgencyReviewStats(AGENCY_ID))
                    .willReturn(statsResult(2L, 4.5, 1L));
            given(reviewRepository.findAgencyReviewStatsByPeriod(eq(AGENCY_ID), any(), any()))
                    .willReturn(periodResult(1L, 4.5));

            AgencyReviewListResponse.ReviewSummary s1 = makeSummary(1L, 5);
            AgencyReviewListResponse.ReviewSummary s2 = makeSummary(2L, 4);
            Page<AgencyReviewListResponse.ReviewSummary> page =
                    new PageImpl<>(List.of(s1, s2), PAGEABLE, 2);
            given(reviewRepository.findAgencyReviews(
                    eq(AGENCY_ID), isNull(), isNull(), isNull(), isNull(), isNull(), isNull(), eq(PAGEABLE)))
                    .willReturn(page);

            // when
            AgencyReviewListResponse result = agencyReviewService.getReviews(
                    agencyUser, new AgencyReviewSearchRequest(), PAGEABLE);

            // then
            assertThat(result.content()).hasSize(2);
            assertThat(result.stats().totalCount()).isEqualTo(2L);
            assertThat(result.stats().avgRating()).isEqualTo(4.5);
            assertThat(result.totalElements()).isEqualTo(2L);
            assertThat(result.currentPage()).isEqualTo(0);
            assertThat(result.size()).isEqualTo(10);
        }
    }

    @Nested
    @DisplayName("TC-2. 역할 검증")
    class RoleValidation {

        @Test
        void ENGINEER_역할이면_IllegalAccessException() {
            assertThatThrownBy(() ->
                    agencyReviewService.getReviews(engineerUser, new AgencyReviewSearchRequest(), PAGEABLE))
                    .isInstanceOf(IllegalAccessException.class);
        }
    }

    @Nested
    @DisplayName("TC-3/4. 필터 파라미터 Repository 전달 검증")
    class FilterPropagation {

        @Test
        void rating_필터_전달시_Repository에_rating_파라미터_전달() throws Exception {
            stubStats();
            stubPage(PAGEABLE);
            AgencyReviewSearchRequest filter = new AgencyReviewSearchRequest();
            ReflectionTestUtils.setField(filter, "rating", 5);

            agencyReviewService.getReviews(agencyUser, filter, PAGEABLE);

            verify(reviewRepository).findAgencyReviews(
                    eq(AGENCY_ID), eq(5), isNull(), isNull(), isNull(), isNull(), isNull(), eq(PAGEABLE));
        }

        @Test
        void engineerId_필터_전달시_Repository에_engineerId_파라미터_전달() throws Exception {
            stubStats();
            stubPage(PAGEABLE);
            AgencyReviewSearchRequest filter = new AgencyReviewSearchRequest();
            ReflectionTestUtils.setField(filter, "engineerId", 99L);

            agencyReviewService.getReviews(agencyUser, filter, PAGEABLE);

            verify(reviewRepository).findAgencyReviews(
                    eq(AGENCY_ID), isNull(), eq(99L), isNull(), isNull(), isNull(), isNull(), eq(PAGEABLE));
        }
    }

    @Nested
    @DisplayName("TC-5/6. 날짜 파싱")
    class DateParsing {

        @Test
        void 정상_날짜_문자열_LocalDateTime으로_변환되어_Repository_호출() throws Exception {
            stubStats();
            stubPage(PAGEABLE);
            AgencyReviewSearchRequest filter = new AgencyReviewSearchRequest();
            ReflectionTestUtils.setField(filter, "dateFrom", "2024-01-01");
            ReflectionTestUtils.setField(filter, "dateTo", "2024-12-31");

            agencyReviewService.getReviews(agencyUser, filter, PAGEABLE);

            verify(reviewRepository).findAgencyReviews(
                    eq(AGENCY_ID), isNull(), isNull(), isNull(),
                    eq(LocalDateTime.of(2024, 1, 1, 0, 0)),
                    eq(LocalDateTime.of(2025, 1, 1, 0, 0)),  // dateTo+1일
                    isNull(), eq(PAGEABLE));
        }

        @Test
        void 잘못된_날짜_형식이면_IllegalArgumentException() {
            AgencyReviewSearchRequest filter = new AgencyReviewSearchRequest();
            ReflectionTestUtils.setField(filter, "dateFrom", "2024-13-99");

            assertThatThrownBy(() ->
                    agencyReviewService.getReviews(agencyUser, filter, PAGEABLE))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("날짜 형식");
        }
    }

    @Nested
    @DisplayName("TC-7. 리뷰 0건")
    class EmptyResult {

        @Test
        void 리뷰_0건이면_stats_0_content_빈리스트() throws Exception {
            given(reviewRepository.findAgencyReviewStats(AGENCY_ID))
                    .willReturn(statsResult(0L, 0.0, 0L));
            given(reviewRepository.findAgencyReviewStatsByPeriod(eq(AGENCY_ID), any(), any()))
                    .willReturn(periodResult(0L, 0.0));
            given(reviewRepository.findAgencyReviews(any(), any(), any(), any(), any(), any(), any(), any()))
                    .willReturn(Page.empty(PAGEABLE));

            AgencyReviewListResponse result = agencyReviewService.getReviews(
                    agencyUser, new AgencyReviewSearchRequest(), PAGEABLE);

            assertThat(result.stats().totalCount()).isEqualTo(0L);
            assertThat(result.stats().avgRating()).isEqualTo(0.0);
            assertThat(result.stats().fiveStarRate()).isEqualTo(0.0);
            assertThat(result.content()).isEmpty();
        }
    }

    @Nested
    @DisplayName("TC-8. fiveStarRate 계산")
    class FiveStarRate {

        @Test
        void totalCount_10_fiveStarCount_7이면_70점() throws Exception {
            given(reviewRepository.findAgencyReviewStats(AGENCY_ID))
                    .willReturn(statsResult(10L, 4.0, 7L));
            given(reviewRepository.findAgencyReviewStatsByPeriod(eq(AGENCY_ID), any(), any()))
                    .willReturn(periodResult(0L, 0.0));
            given(reviewRepository.findAgencyReviews(any(), any(), any(), any(), any(), any(), any(), any()))
                    .willReturn(Page.empty(PAGEABLE));

            AgencyReviewListResponse result = agencyReviewService.getReviews(
                    agencyUser, new AgencyReviewSearchRequest(), PAGEABLE);

            assertThat(result.stats().fiveStarRate()).isEqualTo(70.0);
        }
    }

    // ── 헬퍼 ──────────────────────────────────────────────────────

    private void stubStats() {
        given(reviewRepository.findAgencyReviewStats(AGENCY_ID))
                .willReturn(statsResult(5L, 4.0, 2L));
        given(reviewRepository.findAgencyReviewStatsByPeriod(eq(AGENCY_ID), any(), any()))
                .willReturn(periodResult(2L, 4.0));
    }

    private void stubPage(Pageable pageable) {
        given(reviewRepository.findAgencyReviews(
                eq(AGENCY_ID), any(), any(), any(), any(), any(), any(), eq(pageable)))
                .willReturn(Page.empty(pageable));
    }

    private AgencyReviewListResponse.ReviewSummary makeSummary(Long reviewId, int rating) {
        return new AgencyReviewListResponse.ReviewSummary(
                reviewId, reviewId, "고객명", 10L, "기사명", "대행사",
                "삼성", "ABC123", "2024-06-01", "10:00",
                rating, "내용", true, LocalDateTime.now());
    }
}
