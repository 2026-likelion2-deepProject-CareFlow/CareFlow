package com.careflow.settlement.service;

import com.careflow.agency.entity.Agencies;
import com.careflow.review.repository.ReviewRepository;
import com.careflow.review.repository.ReviewRepository.EngineerAvgRating;
import com.careflow.settlement.dto.EngineerPerformanceResponse;
import com.careflow.settlement.dto.EngineerSettlementListResponse;
import com.careflow.settlement.dto.EngineerSettlementSummary;
import com.careflow.settlement.dto.MonthlySummaryResponse;
import com.careflow.settlement.entity.Settlement;
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
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
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

    private MonthlySummaryProjection stubProjection(long count, long gross,
                                                    long paid, long pending, long disputed,
                                                    long platform, long agency, long engineer) {
        MonthlySummaryProjection p = mock(MonthlySummaryProjection.class);
        given(p.getTotalCount()).willReturn(count);
        given(p.getTotalGrossAmount()).willReturn(gross);
        given(p.getPaidAmount()).willReturn(paid);
        given(p.getPendingAmount()).willReturn(pending);
        given(p.getDisputedAmount()).willReturn(disputed);
        given(p.getTotalPlatformFee()).willReturn(platform);
        given(p.getTotalAgencyFee()).willReturn(agency);
        given(p.getTotalEngineerPayout()).willReturn(engineer);
        return p;
    }

    /** Settlement mock — engineer / agency 연관 포함 */
    private Settlement mockSettlement(Long id) {
        Settlement s = mock(Settlement.class);
        User engineer = mock(User.class);
        Agencies agency = mock(Agencies.class);

        given(s.getId()).willReturn(id);
        given(s.getEngineer()).willReturn(engineer);
        given(s.getAgency()).willReturn(agency);
        given(engineer.getId()).willReturn(10L);
        given(engineer.getName()).willReturn("김현수");
        given(engineer.getPhone()).willReturn("010-0000-0000");
        given(agency.getAgencyName()).willReturn("퀵케어 서비스");
        given(s.getGrossAmount()).willReturn(100000);
        given(s.getFeeRate()).willReturn(BigDecimal.valueOf(10));
        given(s.getPlatformFee()).willReturn(10000);
        given(s.getAgencyFeeRate()).willReturn(BigDecimal.valueOf(10));
        given(s.getAgencyFee()).willReturn(10000);
        given(s.getEngineerNetAmount()).willReturn(80000);
        given(s.getStatus()).willReturn("PAID");
        given(s.getPaidAt()).willReturn(LocalDateTime.of(2024, 6, 18, 15, 30));
        given(s.getCreatedAt()).willReturn(LocalDateTime.of(2024, 6, 15, 10, 0));
        return s;
    }

    private static final Pageable PAGE = PageRequest.of(0, 10);

    // ══════════════════════════════════════════════════════════════
    //  1. getSettlementList — 기사별 정산 내역 목록 (동적 필터)
    // ══════════════════════════════════════════════════════════════
    @Nested
    @DisplayName("getSettlementList — 기사별 정산 내역 목록")
    class GetSettlementList {

        @Test
        @DisplayName("TC-1: 필터 없음 — 전체 조회, settlements 크기·totalElements 검증")
        void success_noFilter_returnsList() {
            stubUser(1L, 10L);
            Settlement s1 = mockSettlement(1L);
            Settlement s2 = mockSettlement(2L);
            Page<Settlement> page = new PageImpl<>(List.of(s1, s2), PAGE, 2);
            given(settlementRepository.findSettlementListByAgency(
                    eq(10L), any(), any(), isNull(), isNull(), isNull(), eq(PAGE)))
                    .willReturn(page);

            EngineerSettlementListResponse result =
                    settlementService.getSettlementList(1L, 2024, 6, null, null, null, null, null, PAGE);

            assertThat(result.settlements()).hasSize(2);
            assertThat(result.totalElements()).isEqualTo(2L);
            assertThat(result.year()).isEqualTo(2024);
            assertThat(result.month()).isEqualTo(6);
        }

        @Test
        @DisplayName("TC-2: status=PAID 필터 전달 — Repository에 status 파라미터 전달 검증")
        void success_statusFilter_propagatedToRepository() {
            stubUser(1L, 10L);
            given(settlementRepository.findSettlementListByAgency(any(), any(), any(), any(), any(), any(), any()))
                    .willReturn(Page.empty(PAGE));

            settlementService.getSettlementList(1L, 2024, 6, "PAID", null, null, null, null, PAGE);

            verify(settlementRepository).findSettlementListByAgency(
                    eq(10L), any(), any(), eq("PAID"), isNull(), isNull(), eq(PAGE));
        }

        @Test
        @DisplayName("TC-3: keyword 필터 전달 — Repository에 keyword 파라미터 전달 검증")
        void success_keywordFilter_propagatedToRepository() {
            stubUser(1L, 10L);
            given(settlementRepository.findSettlementListByAgency(any(), any(), any(), any(), any(), any(), any()))
                    .willReturn(Page.empty(PAGE));

            settlementService.getSettlementList(1L, 2024, 6, null, "김현수", null, null, null, PAGE);

            verify(settlementRepository).findSettlementListByAgency(
                    eq(10L), any(), any(), isNull(), eq("김현수"), isNull(), eq(PAGE));
        }

        @Test
        @DisplayName("TC-4: settlementId 필터 전달 — Repository에 settlementId 파라미터 전달 검증")
        void success_settlementIdFilter_propagatedToRepository() {
            stubUser(1L, 10L);
            given(settlementRepository.findSettlementListByAgency(any(), any(), any(), any(), any(), any(), any()))
                    .willReturn(Page.empty(PAGE));

            settlementService.getSettlementList(1L, 2024, 6, null, null, 99L, null, null, PAGE);

            verify(settlementRepository).findSettlementListByAgency(
                    eq(10L), any(), any(), isNull(), isNull(), eq(99L), eq(PAGE));
        }

        @Test
        @DisplayName("TC-5: dateFrom/dateTo 정상 파싱 — LocalDateTime 변환 후 Repository 호출")
        void success_dateParsed_propagatedToRepository() {
            stubUser(1L, 10L);
            given(settlementRepository.findSettlementListByAgency(any(), any(), any(), any(), any(), any(), any()))
                    .willReturn(Page.empty(PAGE));

            settlementService.getSettlementList(1L, 2024, 6, null, null, null, "2024-06-01", "2024-06-15", PAGE);

            verify(settlementRepository).findSettlementListByAgency(
                    eq(10L),
                    eq(LocalDateTime.of(2024, 6, 1, 0, 0)),
                    eq(LocalDateTime.of(2024, 6, 16, 0, 0)),   // dateTo +1일
                    isNull(), isNull(), isNull(), eq(PAGE));
        }

        @Test
        @DisplayName("TC-6: 잘못된 dateFrom 형식 — IllegalArgumentException 발생")
        void fail_invalidDateFormat_throwsIllegalArgument() {
            stubUser(1L, 10L);

            assertThatThrownBy(() ->
                    settlementService.getSettlementList(1L, 2024, 6, null, null, null, "2024-13-99", null, PAGE))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("날짜 형식");
        }

        @Test
        @DisplayName("TC-7: 유효하지 않은 month=0 — IllegalArgumentException 발생")
        void fail_invalidMonth_zero() {
            assertThatThrownBy(() ->
                    settlementService.getSettlementList(1L, 2024, 0, null, null, null, null, null, PAGE))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("월은 1~12 사이여야 합니다.");
        }

        @Test
        @DisplayName("TC-8: 결과 0건 — 빈 리스트, totalElements=0 반환")
        void success_emptyResult() {
            stubUser(1L, 10L);
            given(settlementRepository.findSettlementListByAgency(any(), any(), any(), any(), any(), any(), any()))
                    .willReturn(Page.empty(PAGE));

            EngineerSettlementListResponse result =
                    settlementService.getSettlementList(1L, 2024, 6, null, null, null, null, null, PAGE);

            assertThat(result.settlements()).isEmpty();
            assertThat(result.totalElements()).isZero();
        }
    }

    // ══════════════════════════════════════════════════════════════
    //  2. getMonthlySummary — 월별 합산 내역
    // ══════════════════════════════════════════════════════════════
    @Nested
    @DisplayName("getMonthlySummary — 월별 합산 내역")
    class GetMonthlySummary {

        @Test
        @DisplayName("정상 집계: 여러 건의 합산값 + 상태별 금액 분리 검증")
        void success_returnsSummary() {
            stubUser(1L, 10L);

            // 픽스처를 given() 체인 바깥에서 먼저 준비
            // PAID 3200000 + PENDING 500000 + DISPUTED 300000 = totalGross 4000000
            MonthlySummaryProjection proj = stubProjection(
                    20L, 4000000L, 3200000L, 500000L, 300000L, 400000L, 360000L, 3240000L);
            given(settlementRepository.findMonthlySummary(eq(10L), any(), any()))
                    .willReturn(proj);

            MonthlySummaryResponse result = settlementService.getMonthlySummary(1L, 2026, 6);

            assertThat(result.getTotalCount()).isEqualTo(20L);
            assertThat(result.getTotalGrossAmount()).isEqualTo(4000000L);
            assertThat(result.getPaidAmount()).isEqualTo(3200000L);
            assertThat(result.getPendingAmount()).isEqualTo(500000L);
            assertThat(result.getDisputedAmount()).isEqualTo(300000L);
            assertThat(result.getTotalPlatformFee()).isEqualTo(400000L);
            assertThat(result.getTotalAgencyFee()).isEqualTo(360000L);
            assertThat(result.getTotalEngineerPayout()).isEqualTo(3240000L);
        }

        @Test
        @DisplayName("빈 결과: 데이터 없는 월 — 모든 금액 필드 0 반환")
        void success_allZero_whenNoData() {
            stubUser(1L, 10L);

            MonthlySummaryProjection proj = stubProjection(0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L);
            given(settlementRepository.findMonthlySummary(eq(10L), any(), any()))
                    .willReturn(proj);

            MonthlySummaryResponse result = settlementService.getMonthlySummary(1L, 2026, 6);

            assertThat(result.getTotalCount()).isZero();
            assertThat(result.getTotalGrossAmount()).isZero();
            assertThat(result.getPaidAmount()).isZero();
            assertThat(result.getPendingAmount()).isZero();
            assertThat(result.getDisputedAmount()).isZero();
        }

        @Test
        @DisplayName("단일 건(PAID): 상태별 금액 분리 — paidAmount = totalGrossAmount")
        void success_singleRecord() {
            stubUser(1L, 10L);

            // 1건 PAID: paidAmount = totalGrossAmount, pending/disputed = 0
            MonthlySummaryProjection proj = stubProjection(1L, 200000L, 200000L, 0L, 0L, 20000L, 18000L, 162000L);
            given(settlementRepository.findMonthlySummary(eq(10L), any(), any()))
                    .willReturn(proj);

            MonthlySummaryResponse result = settlementService.getMonthlySummary(1L, 2026, 6);

            assertThat(result.getTotalCount()).isEqualTo(1L);
            assertThat(result.getTotalGrossAmount()).isEqualTo(200000L);
            assertThat(result.getPaidAmount()).isEqualTo(200000L);
            assertThat(result.getPendingAmount()).isZero();
            assertThat(result.getDisputedAmount()).isZero();
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
