package com.careflow.admin.service;

import com.careflow.admin.dto.response.AdminSettlementDetailResponse;
import com.careflow.admin.dto.response.AdminSettlementSummaryResponse;
import com.careflow.agency.entity.Agencies;
import com.careflow.agency.repository.AgenciesRepository;
import com.careflow.agency_bank_account.entity.AgencyBankAccount;
import com.careflow.agency_bank_account.repository.AgencyBankAccountRepository;
import com.careflow.appliance.entity.Appliance;
import com.careflow.appliance.entity.ApplianceCategory;
import com.careflow.as_request.entity.AsRequest;
import com.careflow.auth.security.CustomUserDetails;
import com.careflow.settlement.entity.PlatformSettlement;
import com.careflow.settlement.entity.Settlement;
import com.careflow.settlement.repository.PlatformSettlementRepository;
import com.careflow.settlement.repository.SettlementRepository;
import com.careflow.settlement.repository.SettlementRepository.AdminAgencySettlementProjection;
import com.careflow.user.entity.User;
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

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("AdminSettlementService 단위 테스트")
class AdminSettlementServiceTest {

    @InjectMocks
    private AdminSettlementService adminSettlementService;

    @Mock
    private SettlementRepository settlementRepository;

    @Mock
    private AgenciesRepository agenciesRepository;

    @Mock
    private PlatformSettlementRepository platformSettlementRepository;

    @Mock
    private AgencyBankAccountRepository agencyBankAccountRepository;

    private static final Long AGENCY_ID = 100L;

    private CustomUserDetails adminUser;
    private CustomUserDetails agencyUser;

    @BeforeEach
    void setUp() {
        adminUser = new CustomUserDetails(1L, "admin@test.com", "", "ADMIN", null);
        agencyUser = new CustomUserDetails(2L, "agency@test.com", "", "AGENCY", AGENCY_ID);
    }

    // ── [⑦] GET /api/admin/settlements ────────────────────────────

    @Nested
    @DisplayName("⑦ 월별 전체 대행사 정산 현황 조회")
    class GetMonthlySummary {

        @Test
        @DisplayName("TC-1. 정상 조회 — 대행사 2곳 → agencies size 2, summary 정상 합산")
        void 정상_조회시_대행사_목록과_합산값_정상매핑() throws Exception {
            AdminAgencySettlementProjection p1 = projection(1L, "한국서비스대행사", 5L, 5200000L, 520000L, 0L);
            AdminAgencySettlementProjection p2 = projection(2L, "미래전자서비스", 3L, 3100000L, 310000L, 3L);
            given(settlementRepository.findAllAgenciesMonthlySummary(any(), any()))
                    .willReturn(List.of(p1, p2));

            AdminSettlementSummaryResponse result =
                    adminSettlementService.getMonthlySummary(adminUser, 2026, 6);

            assertThat(result.agencies()).hasSize(2);
            assertThat(result.summary().totalRevenue()).isEqualTo(8300000L);
            assertThat(result.summary().totalCareflowFee()).isEqualTo(830000L);
            assertThat(result.summary().totalAgencyPay()).isEqualTo(7470000L);
        }

        @Test
        @DisplayName("TC-2. role이 ADMIN이 아닌 경우 → IllegalAccessException")
        void ADMIN이_아니면_예외() {
            assertThatThrownBy(() -> adminSettlementService.getMonthlySummary(agencyUser, 2026, 6))
                    .isInstanceOf(IllegalAccessException.class);
        }

        @Test
        @DisplayName("TC-3. month가 0 또는 13이면 IllegalArgumentException")
        void month_범위초과시_예외() {
            assertThatThrownBy(() -> adminSettlementService.getMonthlySummary(adminUser, 2026, 0))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> adminSettlementService.getMonthlySummary(adminUser, 2026, 13))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("TC-4. asCount=0인 대행사는 status=NONE, 금액 전부 0")
        void asCount_0이면_status_NONE() throws Exception {
            AdminAgencySettlementProjection p = projection(5L, "케어플러스", 0L, 0L, 0L, 0L);
            given(settlementRepository.findAllAgenciesMonthlySummary(any(), any()))
                    .willReturn(List.of(p));

            AdminSettlementSummaryResponse result =
                    adminSettlementService.getMonthlySummary(adminUser, 2026, 6);

            assertThat(result.agencies().get(0).status()).isEqualTo("NONE");
            assertThat(result.agencies().get(0).totalRevenue()).isEqualTo(0L);
        }

        @Test
        @DisplayName("TC-5. 전부 PAID면 status=PAID")
        void 전부_PAID면_status_PAID() throws Exception {
            AdminAgencySettlementProjection p = projection(4L, "프리미엄AS", 2L, 980000L, 98000L, 0L);
            given(settlementRepository.findAllAgenciesMonthlySummary(any(), any()))
                    .willReturn(List.of(p));

            AdminSettlementSummaryResponse result =
                    adminSettlementService.getMonthlySummary(adminUser, 2026, 6);

            assertThat(result.agencies().get(0).status()).isEqualTo("PAID");
        }

        @Test
        @DisplayName("TC-6. 미지급 건이 1건 이상이면 status=PENDING")
        void 미지급건_존재시_status_PENDING() throws Exception {
            AdminAgencySettlementProjection p = projection(1L, "한국서비스대행사", 5L, 5200000L, 520000L, 1L);
            given(settlementRepository.findAllAgenciesMonthlySummary(any(), any()))
                    .willReturn(List.of(p));

            AdminSettlementSummaryResponse result =
                    adminSettlementService.getMonthlySummary(adminUser, 2026, 6);

            assertThat(result.agencies().get(0).status()).isEqualTo("PENDING");
        }

        @Test
        @DisplayName("TC-7. pendingCount는 status=PENDING인 대행사 수만 카운트")
        void pendingCount는_PENDING_대행사만_카운트() throws Exception {
            AdminAgencySettlementProjection p1 = projection(1L, "A", 5L, 100L, 10L, 1L);   // PENDING
            AdminAgencySettlementProjection p2 = projection(2L, "B", 3L, 100L, 10L, 0L);   // PAID
            AdminAgencySettlementProjection p3 = projection(3L, "C", 0L, 0L, 0L, 0L);      // NONE
            given(settlementRepository.findAllAgenciesMonthlySummary(any(), any()))
                    .willReturn(List.of(p1, p2, p3));

            AdminSettlementSummaryResponse result =
                    adminSettlementService.getMonthlySummary(adminUser, 2026, 6);

            assertThat(result.summary().pendingCount()).isEqualTo(1L);
        }

        @Test
        @DisplayName("TC-8. agencyPay = totalRevenue - careflowFee")
        void agencyPay_산출공식_검증() throws Exception {
            AdminAgencySettlementProjection p = projection(1L, "A", 5L, 5200000L, 520000L, 0L);
            given(settlementRepository.findAllAgenciesMonthlySummary(any(), any()))
                    .willReturn(List.of(p));

            AdminSettlementSummaryResponse result =
                    adminSettlementService.getMonthlySummary(adminUser, 2026, 6);

            assertThat(result.agencies().get(0).agencyPay()).isEqualTo(4680000L);
        }

        @Test
        @DisplayName("TC-9. platform_settlements 배치가 없으면 platformSettlementStatus/paidBankAccount는 null")
        void 배치없으면_platformSettlementStatus_null() throws Exception {
            AdminAgencySettlementProjection p = projection(1L, "A", 5L, 5200000L, 520000L, 0L);
            given(settlementRepository.findAllAgenciesMonthlySummary(any(), any()))
                    .willReturn(List.of(p));
            given(platformSettlementRepository.findBySettlementYearAndSettlementMonth(2026, 6))
                    .willReturn(List.of());

            AdminSettlementSummaryResponse result =
                    adminSettlementService.getMonthlySummary(adminUser, 2026, 6);

            assertThat(result.agencies().get(0).platformSettlementStatus()).isNull();
            assertThat(result.agencies().get(0).paidBankAccount()).isNull();
        }

        @Test
        @DisplayName("TC-10. 배치가 DISPUTED 상태면 platformSettlementStatus에 그대로 노출된다")
        void 배치_DISPUTED_상태_그대로_노출() throws Exception {
            AdminAgencySettlementProjection p = projection(1L, "A", 5L, 5200000L, 520000L, 5L);
            given(settlementRepository.findAllAgenciesMonthlySummary(any(), any()))
                    .willReturn(List.of(p));

            com.careflow.agency.entity.Agencies agency = mock(com.careflow.agency.entity.Agencies.class);
            given(agency.getId()).willReturn(1L);
            PlatformSettlement batch = mock(PlatformSettlement.class);
            given(batch.getAgency()).willReturn(agency);
            given(batch.getStatus()).willReturn("DISPUTED");
            given(batch.getPaidBankAccountId()).willReturn(null);
            given(platformSettlementRepository.findBySettlementYearAndSettlementMonth(2026, 6))
                    .willReturn(List.of(batch));

            AdminSettlementSummaryResponse result =
                    adminSettlementService.getMonthlySummary(adminUser, 2026, 6);

            // settlements 기준 파생 status는 여전히 PENDING(모두 미지급)이지만, 실제 배치 상태는 DISPUTED로 구분되어 노출된다
            assertThat(result.agencies().get(0).status()).isEqualTo("PENDING");
            assertThat(result.agencies().get(0).platformSettlementStatus()).isEqualTo("DISPUTED");
        }

        @Test
        @DisplayName("TC-11. 지급 완료된 배치는 지급 계좌 스냅샷을 함께 노출한다")
        void 지급완료_배치_지급계좌_노출() throws Exception {
            AdminAgencySettlementProjection p = projection(1L, "A", 5L, 5200000L, 520000L, 0L);
            given(settlementRepository.findAllAgenciesMonthlySummary(any(), any()))
                    .willReturn(List.of(p));

            com.careflow.agency.entity.Agencies agency = mock(com.careflow.agency.entity.Agencies.class);
            given(agency.getId()).willReturn(1L);
            PlatformSettlement batch = mock(PlatformSettlement.class);
            given(batch.getAgency()).willReturn(agency);
            given(batch.getStatus()).willReturn("PAID");
            given(batch.getPaidBankAccountId()).willReturn(777L);
            given(platformSettlementRepository.findBySettlementYearAndSettlementMonth(2026, 6))
                    .willReturn(List.of(batch));

            com.careflow.agency_bank_account.entity.AgencyBankAccount bankAccount =
                    mock(com.careflow.agency_bank_account.entity.AgencyBankAccount.class);
            given(bankAccount.formatBankAccount()).willReturn("국민은행 123-456-789");
            given(agencyBankAccountRepository.findById(777L)).willReturn(java.util.Optional.of(bankAccount));

            AdminSettlementSummaryResponse result =
                    adminSettlementService.getMonthlySummary(adminUser, 2026, 6);

            assertThat(result.agencies().get(0).platformSettlementStatus()).isEqualTo("PAID");
            assertThat(result.agencies().get(0).paidBankAccount()).isEqualTo("국민은행 123-456-789");
        }
    }

    // ── [⑧] GET /api/admin/settlements/{agencyId}/details ─────────

    @Nested
    @DisplayName("⑧ 특정 대행사 건별 정산 내역 조회")
    class GetAgencyDetails {

        @Test
        @DisplayName("TC-1. 정상 조회 — 정산 1건 → 필드 매핑 정상")
        void 정상_조회시_필드매핑() throws Exception {
            given(agenciesRepository.existsById(AGENCY_ID)).willReturn(true);
            Settlement s = mockSettlement(1L, "냉장고", "김철수", 95000);
            given(settlementRepository.findAgencySettlementDetails(any(), any(), any()))
                    .willReturn(List.of(s));

            List<AdminSettlementDetailResponse> result =
                    adminSettlementService.getAgencyDetails(adminUser, AGENCY_ID, 2026, 6);

            assertThat(result).hasSize(1);
            assertThat(result.get(0).applianceName()).isEqualTo("냉장고");
            assertThat(result.get(0).customerName()).isEqualTo("김철수");
            assertThat(result.get(0).totalAmount()).isEqualTo(95000L);
            assertThat(result.get(0).careflowFee()).isEqualTo(9500L);
        }

        @Test
        @DisplayName("TC-2. role이 ADMIN이 아닌 경우 → IllegalAccessException")
        void ADMIN이_아니면_예외() {
            assertThatThrownBy(() -> adminSettlementService.getAgencyDetails(agencyUser, AGENCY_ID, 2026, 6))
                    .isInstanceOf(IllegalAccessException.class);
        }

        @Test
        @DisplayName("TC-3. 존재하지 않는 agencyId → NoSuchElementException")
        void 존재하지않는_대행사면_예외() {
            given(agenciesRepository.existsById(anyLong())).willReturn(false);

            assertThatThrownBy(() -> adminSettlementService.getAgencyDetails(adminUser, 999L, 2026, 6))
                    .isInstanceOf(java.util.NoSuchElementException.class);
        }

        @Test
        @DisplayName("TC-4. month가 0 또는 13이면 IllegalArgumentException")
        void month_범위초과시_예외() {
            given(agenciesRepository.existsById(AGENCY_ID)).willReturn(true);

            assertThatThrownBy(() -> adminSettlementService.getAgencyDetails(adminUser, AGENCY_ID, 2026, 13))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("TC-5. 정산 0건이면 빈 리스트 반환")
        void 정산_0건이면_빈리스트() throws Exception {
            given(agenciesRepository.existsById(AGENCY_ID)).willReturn(true);
            given(settlementRepository.findAgencySettlementDetails(any(), any(), any()))
                    .willReturn(List.of());

            List<AdminSettlementDetailResponse> result =
                    adminSettlementService.getAgencyDetails(adminUser, AGENCY_ID, 2026, 6);

            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("TC-6. settlementId 포맷 — PK=1 -> SET-001, PK=1234 -> SET-1234")
        void settlementId_포맷_검증() throws Exception {
            given(agenciesRepository.existsById(AGENCY_ID)).willReturn(true);
            Settlement s1 = mockSettlement(1L, "냉장고", "김철수", 95000);
            Settlement s2 = mockSettlement(1234L, "세탁기", "이영희", 72000);
            given(settlementRepository.findAgencySettlementDetails(any(), any(), any()))
                    .willReturn(List.of(s1, s2));

            List<AdminSettlementDetailResponse> result =
                    adminSettlementService.getAgencyDetails(adminUser, AGENCY_ID, 2026, 6);

            assertThat(result.get(0).settlementCode()).isEqualTo("SET-001");
            assertThat(result.get(1).settlementCode()).isEqualTo("SET-1234");
        }

        @Test
        @DisplayName("TC-7. agencyPay = totalAmount - careflowFee")
        void agencyPay_산출공식_검증() throws Exception {
            given(agenciesRepository.existsById(AGENCY_ID)).willReturn(true);
            Settlement s = mockSettlement(1L, "냉장고", "김철수", 95000);
            given(settlementRepository.findAgencySettlementDetails(any(), any(), any()))
                    .willReturn(List.of(s));

            List<AdminSettlementDetailResponse> result =
                    adminSettlementService.getAgencyDetails(adminUser, AGENCY_ID, 2026, 6);

            assertThat(result.get(0).agencyPay()).isEqualTo(85500L);
        }
    }

    // ── [⑨] PATCH /api/admin/settlements/{agencyId}/approve ───────
    // [D 수정] settlements 건별 markPaid() → platform_settlements 배치 단위 승인으로 전환

    @Nested
    @DisplayName("⑨ 단일 대행사 지급 승인")
    class ApproveAgency {

        private Agencies mockAgency(Long id) {
            Agencies agency = mock(Agencies.class);
            given(agency.getId()).willReturn(id);
            return agency;
        }

        private AgencyBankAccount mockBankAccount(Long id) {
            AgencyBankAccount bankAccount = mock(AgencyBankAccount.class);
            given(bankAccount.getId()).willReturn(id);
            return bankAccount;
        }

        @Test
        @DisplayName("TC-1. PENDING 배치 + 계좌 등록됨 → markPaid(계좌ID) 및 하위 settlements 일괄 PAID 캐스케이드")
        void 정상승인_배치와하위settlements_PAID전이() throws Exception {
            given(agenciesRepository.existsById(AGENCY_ID)).willReturn(true);
            PlatformSettlement platformSettlement =
                    PlatformSettlement.create(mockAgency(AGENCY_ID), 2026, 6, 500000, 50000, 450000, 2);
            given(platformSettlementRepository.findByAgency_IdAndSettlementYearAndSettlementMonth(AGENCY_ID, 2026, 6))
                    .willReturn(Optional.of(platformSettlement));
            AgencyBankAccount bankAccount = mockBankAccount(777L);
            given(agencyBankAccountRepository.findByAgencyId(AGENCY_ID))
                    .willReturn(Optional.of(bankAccount));

            adminSettlementService.approveAgency(adminUser, AGENCY_ID, 2026, 6);

            assertThat(platformSettlement.getStatus()).isEqualTo("PAID");
            assertThat(platformSettlement.getPaidBankAccountId()).isEqualTo(777L);
            assertThat(platformSettlement.getPaidAt()).isNotNull();
            verify(settlementRepository).markPaidByPlatformSettlementId(eq(platformSettlement.getId()), any());
        }

        @Test
        @DisplayName("TC-2. role이 ADMIN이 아닌 경우 → IllegalAccessException")
        void ADMIN이_아니면_예외() {
            assertThatThrownBy(() -> adminSettlementService.approveAgency(agencyUser, AGENCY_ID, 2026, 6))
                    .isInstanceOf(IllegalAccessException.class);
        }

        @Test
        @DisplayName("TC-3. 존재하지 않는 agencyId → NoSuchElementException")
        void 존재하지않는_대행사면_예외() {
            given(agenciesRepository.existsById(anyLong())).willReturn(false);

            assertThatThrownBy(() -> adminSettlementService.approveAgency(adminUser, 999L, 2026, 6))
                    .isInstanceOf(java.util.NoSuchElementException.class);
        }

        @Test
        @DisplayName("TC-4. month가 0 또는 13이면 IllegalArgumentException")
        void month_범위초과시_예외() {
            given(agenciesRepository.existsById(AGENCY_ID)).willReturn(true);

            assertThatThrownBy(() -> adminSettlementService.approveAgency(adminUser, AGENCY_ID, 2026, 0))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("TC-5. 해당 기간 정산 배치가 없으면 NoSuchElementException")
        void 배치없으면_예외() {
            given(agenciesRepository.existsById(AGENCY_ID)).willReturn(true);
            given(platformSettlementRepository.findByAgency_IdAndSettlementYearAndSettlementMonth(AGENCY_ID, 2026, 6))
                    .willReturn(Optional.empty());

            assertThatThrownBy(() -> adminSettlementService.approveAgency(adminUser, AGENCY_ID, 2026, 6))
                    .isInstanceOf(java.util.NoSuchElementException.class);
            verify(settlementRepository, never()).markPaidByPlatformSettlementId(any(), any());
        }

        @Test
        @DisplayName("TC-6. 이미 PAID인 배치 재승인 요청 — 에러 없이 정상 종료, 캐스케이드 미호출")
        void 이미PAID인배치_재승인시_변경없음() throws Exception {
            given(agenciesRepository.existsById(AGENCY_ID)).willReturn(true);
            PlatformSettlement paidSettlement =
                    PlatformSettlement.create(mockAgency(AGENCY_ID), 2026, 6, 100000, 10000, 90000, 1);
            paidSettlement.markPaid(999L);
            given(platformSettlementRepository.findByAgency_IdAndSettlementYearAndSettlementMonth(AGENCY_ID, 2026, 6))
                    .willReturn(Optional.of(paidSettlement));

            adminSettlementService.approveAgency(adminUser, AGENCY_ID, 2026, 6);

            assertThat(paidSettlement.getPaidBankAccountId()).isEqualTo(999L); // 변경되지 않음
            verify(agencyBankAccountRepository, never()).findByAgencyId(any());
            verify(settlementRepository, never()).markPaidByPlatformSettlementId(any(), any());
        }

        @Test
        @DisplayName("TC-7. 정산금 수취 계좌 미등록 → IllegalStateException, 배치 상태 변경 없음")
        void 계좌미등록시_예외() {
            given(agenciesRepository.existsById(AGENCY_ID)).willReturn(true);
            PlatformSettlement platformSettlement =
                    PlatformSettlement.create(mockAgency(AGENCY_ID), 2026, 6, 500000, 50000, 450000, 2);
            given(platformSettlementRepository.findByAgency_IdAndSettlementYearAndSettlementMonth(AGENCY_ID, 2026, 6))
                    .willReturn(Optional.of(platformSettlement));
            given(agencyBankAccountRepository.findByAgencyId(AGENCY_ID)).willReturn(Optional.empty());

            assertThatThrownBy(() -> adminSettlementService.approveAgency(adminUser, AGENCY_ID, 2026, 6))
                    .isInstanceOf(IllegalStateException.class);

            assertThat(platformSettlement.getStatus()).isEqualTo("PENDING");
            verify(settlementRepository, never()).markPaidByPlatformSettlementId(any(), any());
        }
    }

    // ── [⑩] PATCH /api/admin/settlements/approve-all ──────────────
    // [D 수정] settlements 건별 markPaid() → platform_settlements 배치 단위 일괄 승인으로 전환

    @Nested
    @DisplayName("⑩ 미지급 전체 일괄 승인")
    class ApproveAll {

        private Agencies mockAgency(Long id) {
            Agencies agency = mock(Agencies.class);
            given(agency.getId()).willReturn(id);
            return agency;
        }

        @Test
        @DisplayName("TC-1. 대행사 2곳의 미지급 배치 → 계좌 등록된 곳 전부 PAID 전이")
        void 전체_미지급배치_PAID전이() throws Exception {
            PlatformSettlement batch1 = PlatformSettlement.create(mockAgency(1L), 2026, 6, 100000, 10000, 90000, 1);
            PlatformSettlement batch2 = PlatformSettlement.create(mockAgency(2L), 2026, 6, 200000, 20000, 180000, 1);
            given(platformSettlementRepository.findBySettlementYearAndSettlementMonthAndStatusNot(2026, 6, "PAID"))
                    .willReturn(List.of(batch1, batch2));

            AgencyBankAccount account1 = mock(AgencyBankAccount.class);
            given(account1.getId()).willReturn(11L);
            AgencyBankAccount account2 = mock(AgencyBankAccount.class);
            given(account2.getId()).willReturn(22L);
            given(agencyBankAccountRepository.findByAgencyId(1L)).willReturn(Optional.of(account1));
            given(agencyBankAccountRepository.findByAgencyId(2L)).willReturn(Optional.of(account2));

            adminSettlementService.approveAll(adminUser, 2026, 6);

            assertThat(batch1.getStatus()).isEqualTo("PAID");
            assertThat(batch1.getPaidBankAccountId()).isEqualTo(11L);
            assertThat(batch2.getStatus()).isEqualTo("PAID");
            assertThat(batch2.getPaidBankAccountId()).isEqualTo(22L);
        }

        @Test
        @DisplayName("TC-2. role이 ADMIN이 아닌 경우 → IllegalAccessException")
        void ADMIN이_아니면_예외() {
            assertThatThrownBy(() -> adminSettlementService.approveAll(agencyUser, 2026, 6))
                    .isInstanceOf(IllegalAccessException.class);
        }

        @Test
        @DisplayName("TC-3. month가 0 또는 13이면 IllegalArgumentException")
        void month_범위초과시_예외() {
            assertThatThrownBy(() -> adminSettlementService.approveAll(adminUser, 2026, 13))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("TC-4. 미지급 배치 0개면 정상 종료")
        void 미지급배치_없으면_아무일도_없음() throws Exception {
            given(platformSettlementRepository.findBySettlementYearAndSettlementMonthAndStatusNot(2026, 6, "PAID"))
                    .willReturn(List.of());

            adminSettlementService.approveAll(adminUser, 2026, 6);
        }

        @Test
        @DisplayName("TC-5. 계좌 미등록 대행사는 스킵되고, 나머지 대행사는 정상 처리된다")
        void 계좌미등록대행사는_스킵하고_나머지는_처리() throws Exception {
            PlatformSettlement noAccountBatch = PlatformSettlement.create(mockAgency(1L), 2026, 6, 100000, 10000, 90000, 1);
            PlatformSettlement okBatch = PlatformSettlement.create(mockAgency(2L), 2026, 6, 200000, 20000, 180000, 1);
            given(platformSettlementRepository.findBySettlementYearAndSettlementMonthAndStatusNot(2026, 6, "PAID"))
                    .willReturn(List.of(noAccountBatch, okBatch));

            given(agencyBankAccountRepository.findByAgencyId(1L)).willReturn(Optional.empty());
            AgencyBankAccount account2 = mock(AgencyBankAccount.class);
            given(account2.getId()).willReturn(22L);
            given(agencyBankAccountRepository.findByAgencyId(2L)).willReturn(Optional.of(account2));

            adminSettlementService.approveAll(adminUser, 2026, 6);

            assertThat(noAccountBatch.getStatus()).isEqualTo("PENDING"); // 스킵되어 그대로
            assertThat(okBatch.getStatus()).isEqualTo("PAID");
        }
    }

    // ── [⑪] PATCH /api/admin/settlements/{settlementId}/status ────

    @Nested
    @DisplayName("⑪ 건별 정산 상태 변경 (ADMIN 전용 보류/재검토)")
    class UpdateItemStatus {

        private Settlement settlementWithStatus(Long id, String status) {
            Settlement s = mock(Settlement.class);
            given(s.getStatus()).willReturn(status);
            return s;
        }

        @Test
        @DisplayName("TC-1. PENDING → DISPUTED 전이")
        void PENDING에서_DISPUTED로_전이() throws Exception {
            Settlement s = settlementWithStatus(1L, "PENDING");
            given(settlementRepository.findById(1L)).willReturn(Optional.of(s));

            adminSettlementService.updateItemStatus(adminUser, 1L, "DISPUTED");

            verify(s).dispute();
        }

        @Test
        @DisplayName("TC-2. DISPUTED → PENDING 복귀")
        void DISPUTED에서_PENDING으로_복귀() throws Exception {
            Settlement s = settlementWithStatus(1L, "DISPUTED");
            given(settlementRepository.findById(1L)).willReturn(Optional.of(s));

            adminSettlementService.updateItemStatus(adminUser, 1L, "PENDING");

            verify(s).revertToPending();
        }

        @Test
        @DisplayName("TC-3. 이미 PAID인 건은 IllegalStateException")
        void 이미_PAID인건_예외() {
            Settlement s = settlementWithStatus(1L, "PAID");
            given(settlementRepository.findById(1L)).willReturn(Optional.of(s));

            assertThatThrownBy(() -> adminSettlementService.updateItemStatus(adminUser, 1L, "DISPUTED"))
                    .isInstanceOf(IllegalStateException.class);
        }

        @Test
        @DisplayName("TC-4. 유효하지 않은 상태값 → IllegalArgumentException")
        void 유효하지않은값_예외() {
            Settlement s = settlementWithStatus(1L, "PENDING");
            given(settlementRepository.findById(1L)).willReturn(Optional.of(s));

            assertThatThrownBy(() -> adminSettlementService.updateItemStatus(adminUser, 1L, "PAID"))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("TC-5. role이 ADMIN이 아닌 경우 → IllegalAccessException")
        void ADMIN이_아니면_예외() {
            assertThatThrownBy(() -> adminSettlementService.updateItemStatus(agencyUser, 1L, "DISPUTED"))
                    .isInstanceOf(IllegalAccessException.class);
        }

        @Test
        @DisplayName("TC-6. 존재하지 않는 정산 → NoSuchElementException")
        void 존재하지않는_정산_예외() {
            given(settlementRepository.findById(999L)).willReturn(Optional.empty());

            assertThatThrownBy(() -> adminSettlementService.updateItemStatus(adminUser, 999L, "DISPUTED"))
                    .isInstanceOf(java.util.NoSuchElementException.class);
        }
    }

    @Nested
    @DisplayName("⑫ 배치 단위 정산 상태 변경 (platform_settlements 보류/재검토)")
    class UpdateBatchStatus {

        private PlatformSettlement batchWithStatus(String status) {
            PlatformSettlement ps = mock(PlatformSettlement.class);
            given(ps.getStatus()).willReturn(status);
            return ps;
        }

        @Test
        @DisplayName("TC-1. PENDING → DISPUTED 전이")
        void 정상_DISPUTED로전이() throws Exception {
            given(agenciesRepository.existsById(AGENCY_ID)).willReturn(true);
            PlatformSettlement batch = batchWithStatus("PENDING");
            given(platformSettlementRepository.findByAgency_IdAndSettlementYearAndSettlementMonth(AGENCY_ID, 2026, 6))
                    .willReturn(Optional.of(batch));

            adminSettlementService.updateBatchStatus(adminUser, AGENCY_ID, 2026, 6, "DISPUTED");

            verify(batch).dispute();
        }

        @Test
        @DisplayName("TC-2. DISPUTED → PENDING 복귀")
        void 정상_PENDING으로복귀() throws Exception {
            given(agenciesRepository.existsById(AGENCY_ID)).willReturn(true);
            PlatformSettlement batch = batchWithStatus("DISPUTED");
            given(platformSettlementRepository.findByAgency_IdAndSettlementYearAndSettlementMonth(AGENCY_ID, 2026, 6))
                    .willReturn(Optional.of(batch));

            adminSettlementService.updateBatchStatus(adminUser, AGENCY_ID, 2026, 6, "PENDING");

            verify(batch).revertToPending();
        }

        @Test
        @DisplayName("TC-3. 이미 PAID인 배치는 IllegalStateException")
        void 이미_PAID인배치_예외() {
            given(agenciesRepository.existsById(AGENCY_ID)).willReturn(true);
            PlatformSettlement batch = batchWithStatus("PAID");
            given(platformSettlementRepository.findByAgency_IdAndSettlementYearAndSettlementMonth(AGENCY_ID, 2026, 6))
                    .willReturn(Optional.of(batch));

            assertThatThrownBy(() -> adminSettlementService.updateBatchStatus(adminUser, AGENCY_ID, 2026, 6, "DISPUTED"))
                    .isInstanceOf(IllegalStateException.class);
        }

        @Test
        @DisplayName("TC-4. 유효하지 않은 상태값 → IllegalArgumentException")
        void 유효하지않은값_예외() {
            given(agenciesRepository.existsById(AGENCY_ID)).willReturn(true);
            PlatformSettlement batch = batchWithStatus("PENDING");
            given(platformSettlementRepository.findByAgency_IdAndSettlementYearAndSettlementMonth(AGENCY_ID, 2026, 6))
                    .willReturn(Optional.of(batch));

            assertThatThrownBy(() -> adminSettlementService.updateBatchStatus(adminUser, AGENCY_ID, 2026, 6, "PAID"))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("TC-5. role이 ADMIN이 아닌 경우 → IllegalAccessException")
        void ADMIN이_아니면_예외() {
            assertThatThrownBy(() -> adminSettlementService.updateBatchStatus(agencyUser, AGENCY_ID, 2026, 6, "DISPUTED"))
                    .isInstanceOf(IllegalAccessException.class);
        }

        @Test
        @DisplayName("TC-6. 존재하지 않는 대행사 → NoSuchElementException")
        void 존재하지않는_대행사_예외() {
            given(agenciesRepository.existsById(999L)).willReturn(false);

            assertThatThrownBy(() -> adminSettlementService.updateBatchStatus(adminUser, 999L, 2026, 6, "DISPUTED"))
                    .isInstanceOf(java.util.NoSuchElementException.class);
        }

        @Test
        @DisplayName("TC-7. 해당 기간 배치가 없으면 NoSuchElementException")
        void 배치없으면_예외() {
            given(agenciesRepository.existsById(AGENCY_ID)).willReturn(true);
            given(platformSettlementRepository.findByAgency_IdAndSettlementYearAndSettlementMonth(AGENCY_ID, 2026, 6))
                    .willReturn(Optional.empty());

            assertThatThrownBy(() -> adminSettlementService.updateBatchStatus(adminUser, AGENCY_ID, 2026, 6, "DISPUTED"))
                    .isInstanceOf(java.util.NoSuchElementException.class);
        }

        @Test
        @DisplayName("TC-8. month가 0 또는 13이면 IllegalArgumentException")
        void month_범위초과시_예외() {
            given(agenciesRepository.existsById(AGENCY_ID)).willReturn(true);

            assertThatThrownBy(() -> adminSettlementService.updateBatchStatus(adminUser, AGENCY_ID, 2026, 0, "DISPUTED"))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    // ── 헬퍼 ──────────────────────────────────────────────────────

    private AdminAgencySettlementProjection projection(
            Long agencyId, String agencyName, long asCount, long totalRevenue, long careflowFee, long unpaidCount) {
        AdminAgencySettlementProjection p = mock(AdminAgencySettlementProjection.class);
        given(p.getAgencyId()).willReturn(agencyId);
        given(p.getAgencyName()).willReturn(agencyName);
        given(p.getAsCount()).willReturn(asCount);
        given(p.getTotalRevenue()).willReturn(totalRevenue);
        given(p.getCareflowFee()).willReturn(careflowFee);
        given(p.getUnpaidCount()).willReturn(unpaidCount);
        return p;
    }

    /** Settlement mock — asRequest.appliance.category / asRequest.customer 경로가 필요 */
    private Settlement mockSettlement(Long id, String applianceCategoryName, String customerName, int gross) {
        Settlement s = mock(Settlement.class);
        AsRequest asRequest = mock(AsRequest.class);
        Appliance appliance = mock(Appliance.class);
        ApplianceCategory category = mock(ApplianceCategory.class);
        User customer = mock(User.class);

        given(s.getId()).willReturn(id);
        given(s.getAsRequest()).willReturn(asRequest);
        given(asRequest.getAppliance()).willReturn(appliance);
        given(appliance.getCategory()).willReturn(category);
        given(category.getName()).willReturn(applianceCategoryName);
        given(asRequest.getCustomer()).willReturn(customer);
        given(customer.getName()).willReturn(customerName);
        given(s.getGrossAmount()).willReturn(gross);
        given(s.getPlatformFee()).willReturn((int) (gross * 0.1));
        given(s.getCreatedAt()).willReturn(LocalDateTime.of(2026, 6, 5, 10, 0));
        return s;
    }
}
