package com.careflow.admin.service;

import com.careflow.admin.dto.response.AdminSettlementDetailResponse;
import com.careflow.admin.dto.response.AdminSettlementSummaryResponse;
import com.careflow.agency.repository.AgenciesRepository;
import com.careflow.appliance.entity.Appliance;
import com.careflow.appliance.entity.ApplianceCategory;
import com.careflow.as_request.entity.AsRequest;
import com.careflow.auth.security.CustomUserDetails;
import com.careflow.settlement.entity.Settlement;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
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

            assertThat(result.get(0).settlementId()).isEqualTo("SET-001");
            assertThat(result.get(1).settlementId()).isEqualTo("SET-1234");
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

    @Nested
    @DisplayName("⑨ 단일 대행사 지급 승인")
    class ApproveAgency {

        @Test
        @DisplayName("TC-1. 미지급 정산 3건 → 전부 markPaid() 호출")
        void 미지급_전체_markPaid_호출() throws Exception {
            given(agenciesRepository.existsById(AGENCY_ID)).willReturn(true);
            Settlement s1 = mock(Settlement.class);
            Settlement s2 = mock(Settlement.class);
            Settlement s3 = mock(Settlement.class);
            given(settlementRepository.findUnpaidByAgencyAndMonth(any(), any(), any()))
                    .willReturn(List.of(s1, s2, s3));

            adminSettlementService.approveAgency(adminUser, AGENCY_ID, 2026, 6);

            verify(s1, times(1)).markPaid();
            verify(s2, times(1)).markPaid();
            verify(s3, times(1)).markPaid();
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
        @DisplayName("TC-5. 미지급 건 0개면 markPaid() 호출 없이 정상 종료")
        void 미지급건_없으면_아무일도_없음() throws Exception {
            given(agenciesRepository.existsById(AGENCY_ID)).willReturn(true);
            given(settlementRepository.findUnpaidByAgencyAndMonth(any(), any(), any()))
                    .willReturn(List.of());

            adminSettlementService.approveAgency(adminUser, AGENCY_ID, 2026, 6);
            // 예외 없이 종료되면 성공 (verify 대상 없음)
        }
    }

    // ── [⑩] PATCH /api/admin/settlements/approve-all ──────────────

    @Nested
    @DisplayName("⑩ 미지급 전체 일괄 승인")
    class ApproveAll {

        @Test
        @DisplayName("TC-1. 미지급 정산 5건 → 전부 markPaid() 호출")
        void 전체_미지급_markPaid_호출() throws Exception {
            List<Settlement> settlements = List.of(
                    mock(Settlement.class), mock(Settlement.class), mock(Settlement.class),
                    mock(Settlement.class), mock(Settlement.class));
            given(settlementRepository.findUnpaidByMonth(any(), any())).willReturn(settlements);

            adminSettlementService.approveAll(adminUser, 2026, 6);

            settlements.forEach(s -> verify(s, times(1)).markPaid());
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
        @DisplayName("TC-4. 미지급 건 0개면 markPaid() 호출 없이 정상 종료")
        void 미지급건_없으면_아무일도_없음() throws Exception {
            given(settlementRepository.findUnpaidByMonth(any(), any())).willReturn(List.of());

            adminSettlementService.approveAll(adminUser, 2026, 6);
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
