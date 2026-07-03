package com.careflow.agency.service;

import com.careflow.agency.dto.request.AgencySettlementSearchRequest;
import com.careflow.agency.dto.response.AgencySettlementListResponse;
import com.careflow.auth.security.CustomUserDetails;
import com.careflow.bank_account.entity.BankAccount;
import com.careflow.bank_account.repository.BankAccountRepository;
import com.careflow.settlement.entity.Settlement;
import com.careflow.settlement.repository.SettlementRepository;
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

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("AgencySettlementService 단위 테스트")
class AgencySettlementServiceTest {

    @InjectMocks
    private AgencySettlementService agencySettlementService;

    @Mock
    private SettlementRepository settlementRepository;

    @Mock
    private BankAccountRepository bankAccountRepository;

    private static final Long AGENCY_ID = 100L;
    private static final Pageable PAGEABLE = PageRequest.of(0, 10);

    private CustomUserDetails agencyUser;
    private CustomUserDetails engineerUser;

    private List<Object[]> statsResult(long count, long gross, long paid, long pending, long disputed) {
        List<Object[]> list = new ArrayList<>();
        list.add(new Object[]{count, gross, paid, pending, disputed});
        return list;
    }

    @BeforeEach
    void setUp() {
        agencyUser  = new CustomUserDetails(1L, "agency@test.com", "", "AGENCY", AGENCY_ID);
        engineerUser = new CustomUserDetails(2L, "eng@test.com",   "", "ENGINEER", AGENCY_ID);
    }

    @Nested
    @DisplayName("TC-1. 정상 조회 — 정산 2건, 필터 없음")
    class NormalCase {

        @Test
        void 정산_2건_존재시_content_size_2_stats_정상매핑() throws Exception {
            stubStats(2L, 200000L, 100000L, 80000L, 20000L);
            Page<Settlement> page = new PageImpl<>(
                    List.of(mockSettlement(1L), mockSettlement(2L)), PAGEABLE, 2);
            given(settlementRepository.findAgencySettlements(
                    eq(AGENCY_ID), isNull(), isNull(), isNull(), isNull(), isNull(), eq(PAGEABLE)))
                    .willReturn(page);

            AgencySettlementListResponse result = agencySettlementService.getSettlements(
                    agencyUser, new AgencySettlementSearchRequest(), PAGEABLE);

            assertThat(result.content()).hasSize(2);
            assertThat(result.stats().thisMonthCount()).isEqualTo(2L);
            assertThat(result.stats().thisMonthGrossAmount()).isEqualTo(200000L);
            assertThat(result.totalElements()).isEqualTo(2L);
        }
    }

    @Nested
    @DisplayName("TC-2. 역할 검증")
    class RoleValidation {

        @Test
        void ENGINEER_역할이면_IllegalAccessException() {
            assertThatThrownBy(() ->
                    agencySettlementService.getSettlements(
                            engineerUser, new AgencySettlementSearchRequest(), PAGEABLE))
                    .isInstanceOf(IllegalAccessException.class);
        }
    }

    @Nested
    @DisplayName("TC-3/4/5. 필터 파라미터 Repository 전달 검증")
    class FilterPropagation {

        @Test
        void status_필터_전달시_Repository에_status_파라미터_전달() throws Exception {
            stubStats(0L, 0L, 0L, 0L, 0L);
            given(settlementRepository.findAgencySettlements(any(), any(), any(), any(), any(), any(), any()))
                    .willReturn(Page.empty(PAGEABLE));
            AgencySettlementSearchRequest filter = new AgencySettlementSearchRequest();
            ReflectionTestUtils.setField(filter, "status", "PAID");

            agencySettlementService.getSettlements(agencyUser, filter, PAGEABLE);

            // nameKeyword=null, settlementId=null
            verify(settlementRepository).findAgencySettlements(
                    eq(AGENCY_ID), eq("PAID"), isNull(), isNull(), isNull(), isNull(), eq(PAGEABLE));
        }

        @Test
        void 문자열_keyword_전달시_nameKeyword로_전달되고_settlementId는_null() throws Exception {
            stubStats(0L, 0L, 0L, 0L, 0L);
            given(settlementRepository.findAgencySettlements(any(), any(), any(), any(), any(), any(), any()))
                    .willReturn(Page.empty(PAGEABLE));
            AgencySettlementSearchRequest filter = new AgencySettlementSearchRequest();
            ReflectionTestUtils.setField(filter, "keyword", "김현수");

            agencySettlementService.getSettlements(agencyUser, filter, PAGEABLE);

            // 숫자가 아닌 문자열 → nameKeyword 전달, settlementId=null
            verify(settlementRepository).findAgencySettlements(
                    eq(AGENCY_ID), isNull(), isNull(), isNull(), eq("김현수"), isNull(), eq(PAGEABLE));
        }

        @Test
        void 숫자_keyword_전달시_settlementId로_전달되고_nameKeyword는_null() throws Exception {
            stubStats(0L, 0L, 0L, 0L, 0L);
            given(settlementRepository.findAgencySettlements(any(), any(), any(), any(), any(), any(), any()))
                    .willReturn(Page.empty(PAGEABLE));
            AgencySettlementSearchRequest filter = new AgencySettlementSearchRequest();
            ReflectionTestUtils.setField(filter, "keyword", "42");

            agencySettlementService.getSettlements(agencyUser, filter, PAGEABLE);

            // 순수 숫자 → settlementId=42L 전달, nameKeyword=null
            verify(settlementRepository).findAgencySettlements(
                    eq(AGENCY_ID), isNull(), isNull(), isNull(), isNull(), eq(42L), eq(PAGEABLE));
        }
    }

    @Nested
    @DisplayName("TC-5/6. 날짜 파싱")
    class DateParsing {

        @Test
        void 정상_날짜_문자열_LocalDateTime으로_변환되어_Repository_호출() throws Exception {
            stubStats(0L, 0L, 0L, 0L, 0L);
            given(settlementRepository.findAgencySettlements(any(), any(), any(), any(), any(), any(), any()))
                    .willReturn(Page.empty(PAGEABLE));
            AgencySettlementSearchRequest filter = new AgencySettlementSearchRequest();
            ReflectionTestUtils.setField(filter, "dateFrom", "2024-06-01");
            ReflectionTestUtils.setField(filter, "dateTo",   "2024-06-30");

            agencySettlementService.getSettlements(agencyUser, filter, PAGEABLE);

            // nameKeyword=null, settlementId=null
            verify(settlementRepository).findAgencySettlements(
                    eq(AGENCY_ID), isNull(),
                    eq(LocalDateTime.of(2024, 6, 1, 0, 0)),
                    eq(LocalDateTime.of(2024, 7, 1, 0, 0)),  // dateTo+1일
                    isNull(), isNull(), eq(PAGEABLE));
        }

        @Test
        void 잘못된_날짜_형식이면_IllegalArgumentException() {
            AgencySettlementSearchRequest filter = new AgencySettlementSearchRequest();
            ReflectionTestUtils.setField(filter, "dateFrom", "2024-13-99");

            assertThatThrownBy(() ->
                    agencySettlementService.getSettlements(agencyUser, filter, PAGEABLE))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("날짜 형식");
        }
    }

    @Nested
    @DisplayName("TC-7. 정산 0건")
    class EmptyResult {

        @Test
        void 정산_0건이면_stats_전부_0_content_빈리스트() throws Exception {
            stubStats(0L, 0L, 0L, 0L, 0L);
            given(settlementRepository.findAgencySettlements(any(), any(), any(), any(), any(), any(), any()))
                    .willReturn(Page.empty(PAGEABLE));

            AgencySettlementListResponse result = agencySettlementService.getSettlements(
                    agencyUser, new AgencySettlementSearchRequest(), PAGEABLE);

            assertThat(result.stats().thisMonthCount()).isEqualTo(0L);
            assertThat(result.stats().thisMonthGrossAmount()).isEqualTo(0L);
            assertThat(result.content()).isEmpty();
        }
    }

    @Nested
    @DisplayName("TC-8. periodStart/periodEnd 파생")
    class PeriodDerivation {

        @Test
        void createdAt_6월15일이면_periodStart_6월1일_periodEnd_6월30일() throws Exception {
            stubStats(1L, 100000L, 100000L, 0L, 0L);
            Settlement s = mockSettlement(1L);
            ReflectionTestUtils.setField(s, "createdAt", LocalDateTime.of(2024, 6, 15, 12, 0));
            Page<Settlement> page = new PageImpl<>(List.of(s), PAGEABLE, 1);
            given(settlementRepository.findAgencySettlements(any(), any(), any(), any(), any(), any(), any()))
                    .willReturn(page);

            AgencySettlementListResponse result = agencySettlementService.getSettlements(
                    agencyUser, new AgencySettlementSearchRequest(), PAGEABLE);

            assertThat(result.content().get(0).periodStart()).isEqualTo("2024-06-01");
            assertThat(result.content().get(0).periodEnd()).isEqualTo("2024-06-30");
        }
    }

    @Nested
    @DisplayName("TC-9. payMethod / bankAccount 매핑")
    class BankAccountMapping {

        @Test
        void 계좌_등록된_기사는_payMethod_bankAccount_채워서_반환() throws Exception {
            stubStats(1L, 88000L, 88000L, 0L, 0L);
            Settlement s = mockSettlement(1L);  // engineer.getId() = 10L
            Page<Settlement> page = new PageImpl<>(List.of(s), PAGEABLE, 1);
            given(settlementRepository.findAgencySettlements(any(), any(), any(), any(), any(), any(), any()))
                    .willReturn(page);

            // 기사 ID 10L 에 대한 bank_account stub
            BankAccount ba = mock(BankAccount.class);
            given(ba.getEngineerId()).willReturn(10L);
            given(ba.getPayMethod()).willReturn(BankAccount.PayMethod.BANK_TRANSFER);
            given(ba.formatBankAccount()).willReturn("신한은행 110-123-456789");
            given(bankAccountRepository.findByEngineerIdIn(List.of(10L))).willReturn(List.of(ba));

            AgencySettlementListResponse result = agencySettlementService.getSettlements(
                    agencyUser, new AgencySettlementSearchRequest(), PAGEABLE);

            assertThat(result.content().get(0).payMethod()).isEqualTo("계좌이체");
            assertThat(result.content().get(0).bankAccount()).isEqualTo("신한은행 110-123-456789");
        }

        @Test
        void 계좌_미등록_기사는_payMethod_bankAccount_null() throws Exception {
            stubStats(1L, 88000L, 88000L, 0L, 0L);
            Settlement s = mockSettlement(1L);
            Page<Settlement> page = new PageImpl<>(List.of(s), PAGEABLE, 1);
            given(settlementRepository.findAgencySettlements(any(), any(), any(), any(), any(), any(), any()))
                    .willReturn(page);

            // bank_account 없음
            given(bankAccountRepository.findByEngineerIdIn(any())).willReturn(List.of());

            AgencySettlementListResponse result = agencySettlementService.getSettlements(
                    agencyUser, new AgencySettlementSearchRequest(), PAGEABLE);

            assertThat(result.content().get(0).payMethod()).isNull();
            assertThat(result.content().get(0).bankAccount()).isNull();
        }
    }

    @Nested
    @DisplayName("TC-10. stats 필터 기준 집계 / 전월 대비 diff")
    class StatsFilterBehavior {

        @Test
        void 필터_없으면_stats는_전체기간_기준이고_전월대비_diff가_계산됨() throws Exception {
            // 필터 없이 조회했을 때의 stats(전체 기간 기준) — findAgencySettlementStatsByFilter 결과
            stubStats(5L, 500000L, 300000L, 150000L, 50000L);
            // 이번 달 / 전월 각각 별도 집계 — 전월 대비 diff 계산용
            given(settlementRepository.findAgencySettlementStatsByPeriod(eq(AGENCY_ID), any(), any()))
                    .willReturn(statsResult(3L, 300000L, 0L, 0L, 0L))  // 이번 달
                    .willReturn(statsResult(1L, 100000L, 0L, 0L, 0L)); // 전월
            given(settlementRepository.findAgencySettlements(any(), any(), any(), any(), any(), any(), any()))
                    .willReturn(Page.empty(PAGEABLE));

            AgencySettlementListResponse result = agencySettlementService.getSettlements(
                    agencyUser, new AgencySettlementSearchRequest(), PAGEABLE);

            // stats 본문은 필터(=없음) 결과인 전체 기간 집계값 그대로
            assertThat(result.stats().thisMonthCount()).isEqualTo(5L);
            assertThat(result.stats().thisMonthGrossAmount()).isEqualTo(500000L);
            // 전월 대비 diff는 이번 달(3건/300000원) - 전월(1건/100000원) 별도 집계로 계산
            assertThat(result.stats().prevMonthCountDiff()).isEqualTo(2L);
            assertThat(result.stats().prevMonthGrossDiff()).isEqualTo(200000L);
        }

        @Test
        void 필터_있으면_stats는_필터결과_기준이고_전월대비_diff는_null() throws Exception {
            stubStats(2L, 120000L, 120000L, 0L, 0L);
            given(settlementRepository.findAgencySettlements(any(), any(), any(), any(), any(), any(), any()))
                    .willReturn(Page.empty(PAGEABLE));

            AgencySettlementSearchRequest filter = new AgencySettlementSearchRequest();
            ReflectionTestUtils.setField(filter, "status", "PAID");

            AgencySettlementListResponse result = agencySettlementService.getSettlements(
                    agencyUser, filter, PAGEABLE);

            assertThat(result.stats().thisMonthCount()).isEqualTo(2L);
            assertThat(result.stats().thisMonthGrossAmount()).isEqualTo(120000L);
            assertThat(result.stats().prevMonthCountDiff()).isNull();
            assertThat(result.stats().prevMonthGrossDiff()).isNull();

            // 필터가 걸린 경우 전월/이번달 별도 집계 쿼리는 아예 호출되지 않아야 함
            verify(settlementRepository, org.mockito.Mockito.never())
                    .findAgencySettlementStatsByPeriod(any(), any(), any());
        }
    }

    // ── 헬퍼 ──────────────────────────────────────────────────────

    private void stubStats(long count, long gross, long paid, long pending, long disputed) {
        List<Object[]> rows = statsResult(count, gross, paid, pending, disputed);
        given(settlementRepository.findAgencySettlementStatsByFilter(
                eq(AGENCY_ID), any(), any(), any(), any(), any()))
                .willReturn(rows);
    }

    /** Settlement mock — engineer/agency가 필요하므로 리플렉션으로 세팅 */
    private Settlement mockSettlement(Long id) {
        Settlement s = mock(Settlement.class);
        com.careflow.user.entity.User engineer = mock(com.careflow.user.entity.User.class);
        com.careflow.agency.entity.Agencies agency = mock(com.careflow.agency.entity.Agencies.class);

        given(s.getId()).willReturn(id);
        given(s.getEngineer()).willReturn(engineer);
        given(s.getAgency()).willReturn(agency);
        given(engineer.getId()).willReturn(10L);
        given(engineer.getName()).willReturn("김현수");
        given(engineer.getPhone()).willReturn("010-0000-0000");
        given(agency.getAgencyName()).willReturn("퀵케어 서비스");
        given(s.getGrossAmount()).willReturn(88000);
        given(s.getFeeRate()).willReturn(BigDecimal.valueOf(10));
        given(s.getPlatformFee()).willReturn(8800);
        given(s.getAgencyFeeRate()).willReturn(BigDecimal.valueOf(10));
        given(s.getAgencyFee()).willReturn(8800);
        given(s.getEngineerNetAmount()).willReturn(70400);
        given(s.getStatus()).willReturn("PAID");
        given(s.getPaidAt()).willReturn(LocalDateTime.of(2024, 6, 18, 15, 30));
        given(s.getCreatedAt()).willReturn(LocalDateTime.of(2024, 6, 15, 10, 0));
        return s;
    }
}
