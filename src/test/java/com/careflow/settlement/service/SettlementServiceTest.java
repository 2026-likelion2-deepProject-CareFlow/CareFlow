package com.careflow.settlement.service;

import com.careflow.agency.entity.Agencies;
import com.careflow.review.repository.ReviewRepository;
import com.careflow.review.repository.ReviewRepository.EngineerAvgRating;
import com.careflow.settlement.dto.EngineerPerformanceResponse;
import com.careflow.settlement.dto.EngineerSettlementSummary;
import com.careflow.settlement.dto.MonthlySummaryResponse;
import com.careflow.settlement.repository.SettlementRepository;
import com.careflow.settlement.repository.SettlementRepository.MonthlySummaryProjection;
import com.careflow.user.entity.User;
import com.careflow.user.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

@ExtendWith(MockitoExtension.class)
@DisplayName("SettlementService 단위 테스트")
class SettlementServiceTest {

    @Mock private SettlementRepository settlementRepository;
    @Mock private ReviewRepository reviewRepository;
    @Mock private UserRepository userRepository;
    @Mock private SettlementCsvGenerator csvGenerator;

    @InjectMocks private SettlementService settlementService;

    // ─── 픽스처 헬퍼 ──────────────────────────────────────────────
    // 주의: 각 헬퍼는 반드시 given() 체인 바깥에서 호출해야 함.
    // given(mock.method()).willReturn(helper()) 형태로 인라인 호출 시
    // 내부 given() 이 외부 given() 보다 먼저 평가되어 UnfinishedStubbingException 발생.

    private void stubUser(Long userId, Long agencyId) {
        User user = mock(User.class);
        Agencies agency = mock(Agencies.class);
        given(agency.getId()).willReturn(agencyId);
        given(user.getAgency()).willReturn(agency);
        given(userRepository.findById(userId)).willReturn(Optional.of(user));
    }

    private EngineerSettlementSummary stubSummary(Long engineerId, String name, long count, long earning) {
        EngineerSettlementSummary s = mock(EngineerSettlementSummary.class);
        given(s.getEngineerId()).willReturn(engineerId);
        given(s.getEngineerName()).willReturn(name);
        given(s.getCompletedCount()).willReturn(count);
        given(s.getTotalEarning()).willReturn(earning);
        return s;
    }

    private EngineerAvgRating stubRating(Long engineerId, Double avg) {
        EngineerAvgRating r = mock(EngineerAvgRating.class);
        given(r.getEngineerId()).willReturn(engineerId);
        given(r.getAvgRating()).willReturn(avg);
        return r;
    }

    private MonthlySummaryProjection stubProjection(long count, long gross, long platform,
                                                    long agency, long engineer) {
        MonthlySummaryProjection p = mock(MonthlySummaryProjection.class);
        given(p.getTotalCount()).willReturn(count);
        given(p.getTotalGrossAmount()).willReturn(gross);
        given(p.getTotalPlatformFee()).willReturn(platform);
        given(p.getTotalAgencyFee()).willReturn(agency);
        given(p.getTotalEngineerPayout()).willReturn(engineer);
        return p;
    }

    // ══════════════════════════════════════════════════════════════
    //  1. getEngineerPerformance — 기사별 실적 리포트
    // ══════════════════════════════════════════════════════════════
    @Nested
    @DisplayName("getEngineerPerformance — 기사별 실적 리포트")
    class GetEngineerPerformance {

        @Test
        @DisplayName("정상 조회: 기사 2명 — 건수·금액·평점 집계 검증")
        void success_returnsTwoEngineers() {
            // Given — 픽스처를 given() 체인 바깥에서 먼저 준비
            stubUser(1L, 10L);

            EngineerSettlementSummary sum1 = stubSummary(101L, "홍길동", 12L, 960000L);
            EngineerSettlementSummary sum2 = stubSummary(102L, "이순신", 8L, 640000L);
            given(settlementRepository.findEngineerPerformance(eq(10L), any(), any()))
                    .willReturn(List.of(sum1, sum2));

            EngineerAvgRating r1 = stubRating(101L, 4.75);
            EngineerAvgRating r2 = stubRating(102L, 4.25);
            given(reviewRepository.findAvgRatingByEngineers(anyList(), any(), any()))
                    .willReturn(List.of(r1, r2));

            // When
            EngineerPerformanceResponse result = settlementService.getEngineerPerformance(1L, 2026, 6);

            // Then
            assertThat(result.getYear()).isEqualTo(2026);
            assertThat(result.getMonth()).isEqualTo(6);
            assertThat(result.getEngineers()).hasSize(2);

            var first = result.getEngineers().get(0);
            assertThat(first.getEngineerId()).isEqualTo(101L);
            assertThat(first.getEngineerName()).isEqualTo("홍길동");
            assertThat(first.getCompletedCount()).isEqualTo(12L);
            assertThat(first.getAvgRating()).isEqualTo(4.75);
            assertThat(first.getTotalEarning()).isEqualTo(960000L);
        }

        @Test
        @DisplayName("빈 결과: 해당 월 정산 내역 없음 — 빈 리스트 반환")
        void success_emptyList_whenNoData() {
            stubUser(1L, 10L);
            given(settlementRepository.findEngineerPerformance(eq(10L), any(), any()))
                    .willReturn(List.of());

            EngineerPerformanceResponse result = settlementService.getEngineerPerformance(1L, 2026, 6);

            assertThat(result.getEngineers()).isEmpty();
        }

        @Test
        @DisplayName("리뷰 없는 기사: avgRating 이 null 로 반환")
        void success_avgRatingNull_whenNoReview() {
            stubUser(1L, 10L);

            EngineerSettlementSummary sum1 = stubSummary(101L, "홍길동", 5L, 400000L);
            given(settlementRepository.findEngineerPerformance(eq(10L), any(), any()))
                    .willReturn(List.of(sum1));
            // 리뷰 없음 — 빈 리스트
            given(reviewRepository.findAvgRatingByEngineers(anyList(), any(), any()))
                    .willReturn(List.of());

            EngineerPerformanceResponse result = settlementService.getEngineerPerformance(1L, 2026, 6);

            assertThat(result.getEngineers().get(0).getAvgRating()).isNull();
        }

        @Test
        @DisplayName("유효하지 않은 month=0 — IllegalArgumentException 발생")
        void fail_invalidMonth_zero() {
            assertThatThrownBy(() -> settlementService.getEngineerPerformance(1L, 2026, 0))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("월은 1~12 사이여야 합니다.");
        }

        @Test
        @DisplayName("유효하지 않은 month=13 — IllegalArgumentException 발생")
        void fail_invalidMonth_thirteen() {
            assertThatThrownBy(() -> settlementService.getEngineerPerformance(1L, 2026, 13))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("월은 1~12 사이여야 합니다.");
        }
    }

    // ══════════════════════════════════════════════════════════════
    //  2. getMonthlySummary — 월별 합산 내역
    // ══════════════════════════════════════════════════════════════
    @Nested
    @DisplayName("getMonthlySummary — 월별 합산 내역")
    class GetMonthlySummary {

        @Test
        @DisplayName("정상 집계: 여러 건의 합산값 검증")
        void success_returnsSummary() {
            stubUser(1L, 10L);

            // 픽스처를 given() 체인 바깥에서 먼저 준비
            MonthlySummaryProjection proj = stubProjection(20L, 4000000L, 400000L, 360000L, 3240000L);
            given(settlementRepository.findMonthlySummary(eq(10L), any(), any()))
                    .willReturn(proj);

            MonthlySummaryResponse result = settlementService.getMonthlySummary(1L, 2026, 6);

            assertThat(result.getTotalCount()).isEqualTo(20L);
            assertThat(result.getTotalGrossAmount()).isEqualTo(4000000L);
            assertThat(result.getTotalPlatformFee()).isEqualTo(400000L);
            assertThat(result.getTotalAgencyFee()).isEqualTo(360000L);
            assertThat(result.getTotalEngineerPayout()).isEqualTo(3240000L);
        }

        @Test
        @DisplayName("빈 결과: 데이터 없는 월 — 모든 금액 필드 0 반환")
        void success_allZero_whenNoData() {
            stubUser(1L, 10L);

            MonthlySummaryProjection proj = stubProjection(0L, 0L, 0L, 0L, 0L);
            given(settlementRepository.findMonthlySummary(eq(10L), any(), any()))
                    .willReturn(proj);

            MonthlySummaryResponse result = settlementService.getMonthlySummary(1L, 2026, 6);

            assertThat(result.getTotalCount()).isZero();
            assertThat(result.getTotalGrossAmount()).isZero();
        }

        @Test
        @DisplayName("단일 건: 합산값이 해당 건의 값과 동일")
        void success_singleRecord() {
            stubUser(1L, 10L);

            MonthlySummaryProjection proj = stubProjection(1L, 200000L, 20000L, 18000L, 162000L);
            given(settlementRepository.findMonthlySummary(eq(10L), any(), any()))
                    .willReturn(proj);

            MonthlySummaryResponse result = settlementService.getMonthlySummary(1L, 2026, 6);

            assertThat(result.getTotalCount()).isEqualTo(1L);
            assertThat(result.getTotalGrossAmount()).isEqualTo(200000L);
            assertThat(result.getTotalEngineerPayout()).isEqualTo(162000L);
        }

        @Test
        @DisplayName("유효하지 않은 month=0 — IllegalArgumentException 발생")
        void fail_invalidMonth() {
            assertThatThrownBy(() -> settlementService.getMonthlySummary(1L, 2026, 0))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("월은 1~12 사이여야 합니다.");
        }
    }
}
