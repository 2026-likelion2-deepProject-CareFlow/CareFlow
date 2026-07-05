package com.careflow.admin.service;

import com.careflow.admin.dto.response.AdminSettlementDetailResponse;
import com.careflow.admin.dto.response.AdminSettlementSummaryResponse;
import com.careflow.agency.entity.Agencies;
import com.careflow.agency.repository.AgenciesRepository;
import com.careflow.agency_bank_account.entity.AgencyBankAccount;
import com.careflow.agency_bank_account.repository.AgencyBankAccountRepository;
import com.careflow.appliance.entity.Appliance;
import com.careflow.appliance.entity.ApplianceCategory;
import com.careflow.appliance.repository.ApplianceCategoryRepository;
import com.careflow.appliance.repository.ApplianceRepository;
import com.careflow.as_request.entity.AsRequest;
import com.careflow.as_request.repository.AsRequestRepository;
import com.careflow.auth.security.CustomUserDetails;
import com.careflow.common.enums.AgencyStatus;
import com.careflow.common.enums.Role;
import com.careflow.payment.entity.Payment;
import com.careflow.payment.repository.PaymentRepository;
import com.careflow.region.entity.Regions;
import com.careflow.region.repository.RegionRepository;
import com.careflow.settlement.entity.PlatformSettlement;
import com.careflow.settlement.entity.Settlement;
import com.careflow.settlement.repository.PlatformSettlementRepository;
import com.careflow.settlement.repository.SettlementRepository;
import com.careflow.symptom.entity.Symptom;
import com.careflow.symptom.repository.SymptomRepository;
import com.careflow.user.entity.User;
import com.careflow.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * AdminSettlementService 통합 테스트 (H2 DB 연동)
 */
@SpringBootTest
@ActiveProfiles("local")
@Sql(scripts = "/cleanup.sql", executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
@DisplayName("AdminSettlementService 통합 테스트 (H2 DB 연동)")
class AdminSettlementServiceIntegrationTest {

    @Autowired private AdminSettlementService adminSettlementService;

    @Autowired private UserRepository userRepository;
    @Autowired private AgenciesRepository agencyRepository;
    @Autowired private ApplianceCategoryRepository categoryRepository;
    @Autowired private RegionRepository regionRepository;
    @Autowired private ApplianceRepository applianceRepository;
    @Autowired private SymptomRepository symptomRepository;
    @Autowired private AsRequestRepository asRequestRepository;
    @Autowired private PaymentRepository paymentRepository;
    @Autowired private SettlementRepository settlementRepository;
    @Autowired private PlatformSettlementRepository platformSettlementRepository;
    @Autowired private AgencyBankAccountRepository agencyBankAccountRepository;

    private Agencies agency;
    private Agencies otherAgency;
    private User engineerUser;
    private User otherEngineerUser;
    private ApplianceCategory leafCategory;
    private Regions district;
    private Symptom symptom;

    private static final LocalDateTime JUNE_1 = LocalDateTime.of(2026, 6, 1, 0, 0);

    @BeforeEach
    void setUp() {
        agency      = agencyRepository.save(Agencies.create("한국서비스대행사", "123-45-67890", "서울시 강남구", 10.0));
        otherAgency = agencyRepository.save(Agencies.create("미래전자서비스", "999-99-99999", "서울시 서초구", 10.0));
        // 승인된 대행사만 [⑦] 집계 대상이므로 APPROVED로 세팅
        ReflectionTestUtils.setField(agency, "approvalStatus", AgencyStatus.APPROVED);
        ReflectionTestUtils.setField(otherAgency, "approvalStatus", AgencyStatus.APPROVED);
        agencyRepository.save(agency);
        agencyRepository.save(otherAgency);

        engineerUser = userRepository.save(User.builder()
                .email("engineer@test.com").passwordHash("hashed")
                .name("김현수").phone("010-1234-5678")
                .role(Role.ENGINEER).agency(agency).build());

        otherEngineerUser = userRepository.save(User.builder()
                .email("other@test.com").passwordHash("hashed")
                .name("타기사").phone("010-0000-0000")
                .role(Role.ENGINEER).agency(otherAgency).build());

        ApplianceCategory root = categoryRepository.save(ApplianceCategory.createRoot("가전", 1));
        leafCategory = categoryRepository.save(ApplianceCategory.createChild("냉장고", root, 1));
        district = regionRepository.save(Regions.create("강남구", null, 2, 0));
        symptom  = symptomRepository.save(Symptom.builder()
                .category(leafCategory).symptomCode("TEST").symptomName("테스트증상").build());
    }

    private CustomUserDetails adminUserDetails() {
        return new CustomUserDetails(999L, "admin@test.com", "pw", "ADMIN", null);
    }

    /** Settlement 한 건 생성 헬퍼 */
    private Settlement createSettlement(User engineer, Agencies targetAgency, int gross,
                                        String status, LocalDateTime createdAt, String customerName) {
        User customer = userRepository.save(User.builder()
                .email("c" + System.nanoTime() + "@t.com").passwordHash("hashed")
                .name(customerName).phone("010-1111-2222").role(Role.CUSTOMER).build());

        Appliance appliance = applianceRepository.save(
                Appliance.create(customer, leafCategory, "삼성", "MODEL", null, null, null, null));

        AsRequest req = AsRequest.builder()
                .customer(customer).appliance(appliance).symptom(symptom)
                .visitRegion(district).visitAddressDetail("주소")
                .scheduledDate(LocalDate.now()).scheduledTime("10:00").build();
        req.processAssignment(targetAgency);
        AsRequest savedReq = asRequestRepository.save(req);

        Payment payment = Payment.create(savedReq, customer, gross);
        Payment savedPayment = paymentRepository.save(payment);

        Settlement s = Settlement.create(
                savedPayment, savedReq, engineer, targetAgency,
                gross,
                (int) (gross * 0.1),
                BigDecimal.valueOf(10),
                (int) (gross * 0.1),
                BigDecimal.valueOf(10),
                (int) (gross * 0.8));
        ReflectionTestUtils.setField(s, "status", status);
        if (createdAt != null) ReflectionTestUtils.setField(s, "createdAt", createdAt);
        return settlementRepository.save(s);
    }

    /**
     * platform_settlement 배치 1건을 생성하고 주어진 settlements를 전부 이 배치에 연결한다.
     * (실제 배치 Job이 하던 "GROUP BY 집계 + FK 연결"을 테스트에서 직접 재현)
     */
    private PlatformSettlement createPlatformSettlementAndLink(
            Agencies targetAgency, int year, int month, String status, List<Settlement> settlements) {

        int gross  = settlements.stream().mapToInt(Settlement::getGrossAmount).sum();
        int fee    = settlements.stream().mapToInt(Settlement::getPlatformFee).sum();
        int payout = gross - fee;

        PlatformSettlement ps = PlatformSettlement.create(targetAgency, year, month, gross, fee, payout, settlements.size());
        ps = platformSettlementRepository.save(ps);

        if (!"PENDING".equals(status)) {
            ReflectionTestUtils.setField(ps, "status", status);
            ps = platformSettlementRepository.save(ps);
        }

        for (Settlement s : settlements) {
            s.assignPlatformSettlement(ps);
            settlementRepository.save(s);
        }
        return ps;
    }

    /** 대행사 정산금 수취 계좌 등록 헬퍼 */
    private AgencyBankAccount registerBankAccount(Agencies targetAgency) {
        return agencyBankAccountRepository.save(
                AgencyBankAccount.create(targetAgency.getId(), "국민은행", "123-456-789", targetAgency.getAgencyName()));
    }

    // ── [⑦] 월별 전체 대행사 정산 현황 조회 ─────────────────────────

    @Nested
    @DisplayName("TC-I. ⑦ 월별 전체 대행사 정산 현황 조회")
    class MonthlySummary {

        @Test
        @DisplayName("승인된 대행사만 목록 포함 — PENDING/REJECTED 대행사는 제외")
        void 미승인_대행사는_제외된다() throws Exception {
            Agencies pendingAgency = agencyRepository.save(
                    Agencies.create("승인대기대행사", "111-11-11111", "주소", 10.0));
            // approvalStatus 기본값 PENDING 유지

            createSettlement(engineerUser, agency, 100000, "PAID", JUNE_1.plusDays(4), "김철수");

            AdminSettlementSummaryResponse result =
                    adminSettlementService.getMonthlySummary(adminUserDetails(), 2026, 6);

            assertThat(result.agencies())
                    .extracting(AdminSettlementSummaryResponse.AgencySettlementItem::agencyId)
                    .doesNotContain(pendingAgency.getId());
        }

        @Test
        @DisplayName("정산 없는 대행사도 목록에 포함 — asCount=0, status=NONE")
        void 정산없는_대행사도_포함() throws Exception {
            createSettlement(engineerUser, agency, 100000, "PAID", JUNE_1.plusDays(4), "김철수");
            // otherAgency는 정산 없음

            AdminSettlementSummaryResponse result =
                    adminSettlementService.getMonthlySummary(adminUserDetails(), 2026, 6);

            AdminSettlementSummaryResponse.AgencySettlementItem otherItem = result.agencies().stream()
                    .filter(a -> a.agencyId().equals(otherAgency.getId()))
                    .findFirst().orElseThrow();

            assertThat(otherItem.asCount()).isEqualTo(0L);
            assertThat(otherItem.status()).isEqualTo("NONE");
        }

        @Test
        @DisplayName("다수 대행사에 걸친 정산이 대행사별로 정확히 분리 집계된다")
        void 대행사별로_정확히_분리집계() throws Exception {
            createSettlement(engineerUser, agency, 100000, "PAID", JUNE_1.plusDays(4), "김철수");
            createSettlement(otherEngineerUser, otherAgency, 200000, "PAID", JUNE_1.plusDays(4), "이영희");

            AdminSettlementSummaryResponse result =
                    adminSettlementService.getMonthlySummary(adminUserDetails(), 2026, 6);

            AdminSettlementSummaryResponse.AgencySettlementItem item1 = result.agencies().stream()
                    .filter(a -> a.agencyId().equals(agency.getId())).findFirst().orElseThrow();
            AdminSettlementSummaryResponse.AgencySettlementItem item2 = result.agencies().stream()
                    .filter(a -> a.agencyId().equals(otherAgency.getId())).findFirst().orElseThrow();

            assertThat(item1.totalRevenue()).isEqualTo(100000L);
            assertThat(item2.totalRevenue()).isEqualTo(200000L);
        }

        @Test
        @DisplayName("월 범위 필터 — 전월/익월 생성된 정산은 집계에서 제외")
        void 월범위밖_정산은_제외() throws Exception {
            createSettlement(engineerUser, agency, 100000, "PAID", LocalDateTime.of(2026, 5, 15, 0, 0), "김철수");
            createSettlement(engineerUser, agency, 200000, "PAID", LocalDateTime.of(2026, 7, 15, 0, 0), "이영희");

            AdminSettlementSummaryResponse result =
                    adminSettlementService.getMonthlySummary(adminUserDetails(), 2026, 6);

            AdminSettlementSummaryResponse.AgencySettlementItem item = result.agencies().stream()
                    .filter(a -> a.agencyId().equals(agency.getId())).findFirst().orElseThrow();

            assertThat(item.asCount()).isEqualTo(0L);
        }

        @Test
        @DisplayName("PAID 1건 + PENDING 1건 → status=PENDING, asCount=2")
        void 미지급건_존재시_PENDING() throws Exception {
            createSettlement(engineerUser, agency, 100000, "PAID", JUNE_1.plusDays(4), "김철수");
            createSettlement(engineerUser, agency, 80000, "PENDING", JUNE_1.plusDays(10), "이영희");

            AdminSettlementSummaryResponse result =
                    adminSettlementService.getMonthlySummary(adminUserDetails(), 2026, 6);

            AdminSettlementSummaryResponse.AgencySettlementItem item = result.agencies().stream()
                    .filter(a -> a.agencyId().equals(agency.getId())).findFirst().orElseThrow();

            assertThat(item.asCount()).isEqualTo(2L);
            assertThat(item.status()).isEqualTo("PENDING");
        }

        @Test
        @DisplayName("전부 PAID → status=PAID")
        void 전부_PAID면_status_PAID() throws Exception {
            createSettlement(engineerUser, agency, 100000, "PAID", JUNE_1.plusDays(4), "김철수");
            createSettlement(engineerUser, agency, 80000, "PAID", JUNE_1.plusDays(10), "이영희");

            AdminSettlementSummaryResponse result =
                    adminSettlementService.getMonthlySummary(adminUserDetails(), 2026, 6);

            AdminSettlementSummaryResponse.AgencySettlementItem item = result.agencies().stream()
                    .filter(a -> a.agencyId().equals(agency.getId())).findFirst().orElseThrow();

            assertThat(item.status()).isEqualTo("PAID");
        }

        @Test
        @DisplayName("platform_settlements 배치가 없으면 platformSettlementStatus/paidBankAccount는 null")
        void 배치없으면_platformSettlementStatus_null() throws Exception {
            createSettlement(engineerUser, agency, 100000, "PAID", JUNE_1.plusDays(4), "김철수");

            AdminSettlementSummaryResponse result =
                    adminSettlementService.getMonthlySummary(adminUserDetails(), 2026, 6);

            AdminSettlementSummaryResponse.AgencySettlementItem item = result.agencies().stream()
                    .filter(a -> a.agencyId().equals(agency.getId())).findFirst().orElseThrow();

            assertThat(item.platformSettlementStatus()).isNull();
            assertThat(item.paidBankAccount()).isNull();
        }

        @Test
        @DisplayName("배치가 지급 완료되면 platformSettlementStatus=PAID + 지급 계좌 스냅샷이 노출된다")
        void 배치_지급완료시_상태와_계좌_노출() throws Exception {
            Settlement s = createSettlement(engineerUser, agency, 100000, "PENDING", JUNE_1.plusDays(4), "김철수");
            createPlatformSettlementAndLink(agency, 2026, 6, "PENDING", List.of(s));
            registerBankAccount(agency);

            adminSettlementService.approveAgency(adminUserDetails(), agency.getId(), 2026, 6);

            AdminSettlementSummaryResponse result =
                    adminSettlementService.getMonthlySummary(adminUserDetails(), 2026, 6);

            AdminSettlementSummaryResponse.AgencySettlementItem item = result.agencies().stream()
                    .filter(a -> a.agencyId().equals(agency.getId())).findFirst().orElseThrow();

            assertThat(item.platformSettlementStatus()).isEqualTo("PAID");
            assertThat(item.paidBankAccount()).isEqualTo("국민은행 123-456-789");
        }

        @Test
        @DisplayName("summary 총합이 대행사별 값의 합과 정확히 일치한다")
        void summary_총합_정합성() throws Exception {
            createSettlement(engineerUser, agency, 100000, "PAID", JUNE_1.plusDays(4), "김철수");
            createSettlement(otherEngineerUser, otherAgency, 200000, "PAID", JUNE_1.plusDays(4), "이영희");

            AdminSettlementSummaryResponse result =
                    adminSettlementService.getMonthlySummary(adminUserDetails(), 2026, 6);

            long sumRevenue = result.agencies().stream()
                    .mapToLong(AdminSettlementSummaryResponse.AgencySettlementItem::totalRevenue).sum();

            assertThat(result.summary().totalRevenue()).isEqualTo(sumRevenue);
            assertThat(result.summary().totalRevenue()).isEqualTo(300000L);
            assertThat(result.summary().totalCareflowFee()).isEqualTo(30000L);
            assertThat(result.summary().totalAgencyPay()).isEqualTo(270000L);
        }
    }

    // ── [⑧] 특정 대행사 건별 정산 내역 조회 ─────────────────────────

    @Nested
    @DisplayName("TC-I. ⑧ 특정 대행사 건별 정산 내역 조회")
    class AgencyDetails {

        @Test
        @DisplayName("타 대행사 정산은 결과에서 제외된다")
        void 타대행사_정산제외() throws Exception {
            createSettlement(engineerUser, agency, 95000, "PAID", JUNE_1.plusDays(4), "김철수");
            createSettlement(otherEngineerUser, otherAgency, 200000, "PAID", JUNE_1.plusDays(4), "이영희");

            List<AdminSettlementDetailResponse> result =
                    adminSettlementService.getAgencyDetails(adminUserDetails(), agency.getId(), 2026, 6);

            assertThat(result).hasSize(1);
            assertThat(result.get(0).customerName()).isEqualTo("김철수");
        }

        @Test
        @DisplayName("월 범위 밖 정산은 제외된다")
        void 월범위밖_정산제외() throws Exception {
            createSettlement(engineerUser, agency, 95000, "PAID", LocalDateTime.of(2026, 5, 20, 0, 0), "김철수");

            List<AdminSettlementDetailResponse> result =
                    adminSettlementService.getAgencyDetails(adminUserDetails(), agency.getId(), 2026, 6);

            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("applianceName/customerName이 실제 카테고리명/고객명으로 매핑된다")
        void 이름필드_정상매핑() throws Exception {
            createSettlement(engineerUser, agency, 95000, "PAID", JUNE_1.plusDays(4), "김철수");

            List<AdminSettlementDetailResponse> result =
                    adminSettlementService.getAgencyDetails(adminUserDetails(), agency.getId(), 2026, 6);

            assertThat(result.get(0).applianceName()).isEqualTo("냉장고");
            assertThat(result.get(0).customerName()).isEqualTo("김철수");
        }

        @Test
        @DisplayName("createdAt ASC 순서로 반환된다")
        void 정렬순서_ASC() throws Exception {
            createSettlement(engineerUser, agency, 95000, "PAID", JUNE_1.plusDays(20), "나중건");
            createSettlement(engineerUser, agency, 72000, "PAID", JUNE_1.plusDays(5), "먼저건");

            List<AdminSettlementDetailResponse> result =
                    adminSettlementService.getAgencyDetails(adminUserDetails(), agency.getId(), 2026, 6);

            assertThat(result.get(0).customerName()).isEqualTo("먼저건");
            assertThat(result.get(1).customerName()).isEqualTo("나중건");
        }

        @Test
        @DisplayName("정산 0건인 대행사·월 조합은 빈 배열을 반환한다")
        void 정산없으면_빈배열() throws Exception {
            List<AdminSettlementDetailResponse> result =
                    adminSettlementService.getAgencyDetails(adminUserDetails(), agency.getId(), 2026, 6);

            assertThat(result).isEmpty();
        }
    }

    // ── [⑨] 단일 대행사 지급 승인 ────────────────────────────────
    // [D 수정] settlements 건별 조회·markPaid() → platform_settlements 배치 단위 승인으로 전환

    @Nested
    @DisplayName("TC-I. ⑨ 단일 대행사 지급 승인")
    class ApproveAgency {

        @Test
        @DisplayName("PENDING 배치 승인 → 배치·하위 settlements 모두 PAID, 계좌 스냅샷 확정")
        void PENDING_배치_승인() throws Exception {
            Settlement s1 = createSettlement(engineerUser, agency, 95000, "PENDING", JUNE_1.plusDays(4), "김철수");
            Settlement s2 = createSettlement(engineerUser, agency, 72000, "PENDING", JUNE_1.plusDays(10), "이영희");
            PlatformSettlement ps = createPlatformSettlementAndLink(agency, 2026, 6, "PENDING", List.of(s1, s2));
            AgencyBankAccount account = registerBankAccount(agency);

            adminSettlementService.approveAgency(adminUserDetails(), agency.getId(), 2026, 6);

            Settlement reloaded1 = settlementRepository.findById(s1.getId()).orElseThrow();
            Settlement reloaded2 = settlementRepository.findById(s2.getId()).orElseThrow();
            assertThat(reloaded1.getStatus()).isEqualTo("PAID");
            assertThat(reloaded1.getPaidAt()).isNotNull();
            assertThat(reloaded2.getStatus()).isEqualTo("PAID");
            assertThat(reloaded2.getPaidAt()).isNotNull();

            PlatformSettlement reloadedPs = platformSettlementRepository.findById(ps.getId()).orElseThrow();
            assertThat(reloadedPs.getStatus()).isEqualTo("PAID");
            assertThat(reloadedPs.getPaidBankAccountId()).isEqualTo(account.getId());
            assertThat(reloadedPs.getPaidAt()).isNotNull();
        }

        @Test
        @DisplayName("타 대행사 배치의 settlement는 변경되지 않는다")
        void 타대행사_정산불변() throws Exception {
            Settlement otherSettlement = createSettlement(
                    otherEngineerUser, otherAgency, 200000, "PENDING", JUNE_1.plusDays(4), "이영희");
            createPlatformSettlementAndLink(otherAgency, 2026, 6, "PENDING", List.of(otherSettlement));
            registerBankAccount(otherAgency);

            Settlement mySettlement = createSettlement(engineerUser, agency, 95000, "PENDING", JUNE_1.plusDays(4), "김철수");
            createPlatformSettlementAndLink(agency, 2026, 6, "PENDING", List.of(mySettlement));
            registerBankAccount(agency);

            adminSettlementService.approveAgency(adminUserDetails(), agency.getId(), 2026, 6);

            Settlement reloaded = settlementRepository.findById(otherSettlement.getId()).orElseThrow();
            assertThat(reloaded.getStatus()).isEqualTo("PENDING");
        }

        @Test
        @DisplayName("배치에 연결되지 않은(다른 달 소속) settlement는 변경되지 않는다")
        void 배치미연결_정산불변() throws Exception {
            // 5월분 settlement — 이번 배치(6월)에 연결되지 않은 채로 남아있는 상황 재현
            Settlement mayS = createSettlement(
                    engineerUser, agency, 95000, "PENDING", LocalDateTime.of(2026, 5, 20, 0, 0), "김철수");

            Settlement juneS = createSettlement(engineerUser, agency, 72000, "PENDING", JUNE_1.plusDays(4), "이영희");
            createPlatformSettlementAndLink(agency, 2026, 6, "PENDING", List.of(juneS));
            registerBankAccount(agency);

            adminSettlementService.approveAgency(adminUserDetails(), agency.getId(), 2026, 6);

            Settlement reloaded = settlementRepository.findById(mayS.getId()).orElseThrow();
            assertThat(reloaded.getStatus()).isEqualTo("PENDING");
        }

        @Test
        @DisplayName("이미 PAID인 배치 재승인 요청 — 에러 없이 정상 종료, paidAt/계좌 변경 없음")
        void 이미_PAID인_배치_재승인시_변경없음() throws Exception {
            LocalDateTime originalPaidAt = LocalDateTime.of(2026, 6, 10, 9, 0);
            Settlement paidSettlement = createSettlement(
                    engineerUser, agency, 95000, "PAID", JUNE_1.plusDays(4), "김철수");
            PlatformSettlement ps = createPlatformSettlementAndLink(agency, 2026, 6, "PAID", List.of(paidSettlement));
            AgencyBankAccount originalAccount = registerBankAccount(agency);
            ReflectionTestUtils.setField(ps, "paidAt", originalPaidAt);
            ReflectionTestUtils.setField(ps, "paidBankAccountId", originalAccount.getId());
            platformSettlementRepository.save(ps);
            settlementRepository.updatePaidAt(paidSettlement.getId(), originalPaidAt);

            adminSettlementService.approveAgency(adminUserDetails(), agency.getId(), 2026, 6);

            PlatformSettlement reloadedPs = platformSettlementRepository.findById(ps.getId()).orElseThrow();
            assertThat(reloadedPs.getPaidAt()).isEqualTo(originalPaidAt);
            Settlement reloaded = settlementRepository.findById(paidSettlement.getId()).orElseThrow();
            assertThat(reloaded.getPaidAt()).isEqualTo(originalPaidAt);
        }

        @Test
        @DisplayName("DISPUTED 건은 배치에 묶여 있어도 일괄 승인 대상에서 제외되고 PENDING 건만 PAID로 전이된다")
        void DISPUTED_건은_제외되고_PENDING_건만_PAID로_전이() throws Exception {
            Settlement disputed = createSettlement(
                    engineerUser, agency, 95000, "DISPUTED", JUNE_1.plusDays(4), "김철수");
            Settlement pending = createSettlement(
                    engineerUser, agency, 72000, "PENDING", JUNE_1.plusDays(10), "이영희");
            createPlatformSettlementAndLink(agency, 2026, 6, "PENDING", List.of(disputed, pending));
            registerBankAccount(agency);

            adminSettlementService.approveAgency(adminUserDetails(), agency.getId(), 2026, 6);

            Settlement reloadedDisputed = settlementRepository.findById(disputed.getId()).orElseThrow();
            assertThat(reloadedDisputed.getStatus()).isEqualTo("DISPUTED");
            assertThat(reloadedDisputed.getPaidAt()).isNull();

            Settlement reloadedPending = settlementRepository.findById(pending.getId()).orElseThrow();
            assertThat(reloadedPending.getStatus()).isEqualTo("PAID");
            assertThat(reloadedPending.getPaidAt()).isNotNull();
        }

        @Test
        @DisplayName("해당 기간 정산 배치가 없으면 NoSuchElementException")
        void 배치없으면_예외() {
            assertThatThrownBy(() ->
                    adminSettlementService.approveAgency(adminUserDetails(), agency.getId(), 2026, 6))
                    .isInstanceOf(java.util.NoSuchElementException.class);
        }

        @Test
        @DisplayName("정산금 수취 계좌 미등록 → IllegalStateException, 배치·하위 정산 상태 변경 없음")
        void 계좌미등록시_예외() throws Exception {
            Settlement s = createSettlement(engineerUser, agency, 95000, "PENDING", JUNE_1.plusDays(4), "김철수");
            PlatformSettlement ps = createPlatformSettlementAndLink(agency, 2026, 6, "PENDING", List.of(s));
            // 계좌 미등록 상태로 승인 시도

            assertThatThrownBy(() ->
                    adminSettlementService.approveAgency(adminUserDetails(), agency.getId(), 2026, 6))
                    .isInstanceOf(IllegalStateException.class);

            assertThat(platformSettlementRepository.findById(ps.getId()).orElseThrow().getStatus()).isEqualTo("PENDING");
            assertThat(settlementRepository.findById(s.getId()).orElseThrow().getStatus()).isEqualTo("PENDING");
        }
    }

    // ── [⑩] 미지급 전체 일괄 승인 ────────────────────────────────
    // [D 수정] settlements 건별 조회·markPaid() → platform_settlements 배치 단위 일괄 승인으로 전환

    @Nested
    @DisplayName("TC-I. ⑩ 미지급 전체 일괄 승인")
    class ApproveAll {

        @Test
        @DisplayName("대행사 여러 곳에 걸친 미지급 배치가 전부 PAID로 전이된다")
        void 전체_미지급_승인() throws Exception {
            Settlement s1 = createSettlement(engineerUser, agency, 95000, "PENDING", JUNE_1.plusDays(4), "김철수");
            createPlatformSettlementAndLink(agency, 2026, 6, "PENDING", List.of(s1));
            registerBankAccount(agency);

            Settlement s2 = createSettlement(otherEngineerUser, otherAgency, 72000, "PENDING", JUNE_1.plusDays(10), "이영희");
            createPlatformSettlementAndLink(otherAgency, 2026, 6, "PENDING", List.of(s2));
            registerBankAccount(otherAgency);

            adminSettlementService.approveAll(adminUserDetails(), 2026, 6);

            assertThat(settlementRepository.findById(s1.getId()).orElseThrow().getStatus()).isEqualTo("PAID");
            assertThat(settlementRepository.findById(s2.getId()).orElseThrow().getStatus()).isEqualTo("PAID");
        }

        @Test
        @DisplayName("이미 PAID인 배치는 영향받지 않는다 (paidAt 불변)")
        void 이미_PAID인_배치_불변() throws Exception {
            LocalDateTime originalPaidAt = LocalDateTime.of(2026, 6, 10, 9, 0);
            Settlement paidSettlement = createSettlement(
                    engineerUser, agency, 95000, "PAID", JUNE_1.plusDays(4), "김철수");
            PlatformSettlement ps = createPlatformSettlementAndLink(agency, 2026, 6, "PAID", List.of(paidSettlement));
            ReflectionTestUtils.setField(ps, "paidAt", originalPaidAt);
            platformSettlementRepository.save(ps);
            settlementRepository.updatePaidAt(paidSettlement.getId(), originalPaidAt);

            adminSettlementService.approveAll(adminUserDetails(), 2026, 6);

            PlatformSettlement reloadedPs = platformSettlementRepository.findById(ps.getId()).orElseThrow();
            assertThat(reloadedPs.getPaidAt()).isEqualTo(originalPaidAt);
        }

        @Test
        @DisplayName("대상 월이 아닌 배치는 변경되지 않는다")
        void 타월_배치불변() throws Exception {
            Settlement julyS = createSettlement(
                    engineerUser, agency, 95000, "PENDING", LocalDateTime.of(2026, 7, 5, 0, 0), "김철수");
            createPlatformSettlementAndLink(agency, 2026, 7, "PENDING", List.of(julyS));
            registerBankAccount(agency);

            adminSettlementService.approveAll(adminUserDetails(), 2026, 6);

            Settlement reloaded = settlementRepository.findById(julyS.getId()).orElseThrow();
            assertThat(reloaded.getStatus()).isEqualTo("PENDING");
        }

        @Test
        @DisplayName("정산 배치가 하나도 없는 월 요청은 에러 없이 정상 종료된다")
        void 정산없는_월_정상종료() throws Exception {
            adminSettlementService.approveAll(adminUserDetails(), 2026, 6);
            // 예외 없이 종료되면 성공
        }

        @Test
        @DisplayName("계좌 미등록 대행사는 스킵되고, 나머지 대행사는 정상 처리된다")
        void 계좌미등록대행사는_스킵하고_나머지는_처리() throws Exception {
            Settlement noAccountSettlement = createSettlement(
                    engineerUser, agency, 95000, "PENDING", JUNE_1.plusDays(4), "김철수");
            createPlatformSettlementAndLink(agency, 2026, 6, "PENDING", List.of(noAccountSettlement));
            // agency는 계좌 미등록 상태로 둠

            Settlement okSettlement = createSettlement(
                    otherEngineerUser, otherAgency, 72000, "PENDING", JUNE_1.plusDays(10), "이영희");
            createPlatformSettlementAndLink(otherAgency, 2026, 6, "PENDING", List.of(okSettlement));
            registerBankAccount(otherAgency);

            adminSettlementService.approveAll(adminUserDetails(), 2026, 6);

            assertThat(settlementRepository.findById(noAccountSettlement.getId()).orElseThrow().getStatus())
                    .isEqualTo("PENDING");
            assertThat(settlementRepository.findById(okSettlement.getId()).orElseThrow().getStatus())
                    .isEqualTo("PAID");
        }
    }

    // ── [⑪] 건별 정산 상태 변경 (보류/재검토) ────────────────────────

    @Nested
    @DisplayName("TC-I. ⑪ 건별 정산 상태 변경 (ADMIN 전용 보류/재검토)")
    class UpdateItemStatus {

        @Test
        @DisplayName("PENDING → DISPUTED 전이 성공")
        void PENDING에서_DISPUTED로_전이() throws Exception {
            Settlement s = createSettlement(engineerUser, agency, 95000, "PENDING", JUNE_1.plusDays(4), "김철수");

            adminSettlementService.updateItemStatus(adminUserDetails(), s.getId(), "DISPUTED");

            assertThat(settlementRepository.findById(s.getId()).orElseThrow().getStatus()).isEqualTo("DISPUTED");
        }

        @Test
        @DisplayName("DISPUTED → PENDING 재검토 성공")
        void DISPUTED에서_PENDING으로_복귀() throws Exception {
            Settlement s = createSettlement(engineerUser, agency, 95000, "DISPUTED", JUNE_1.plusDays(4), "김철수");

            adminSettlementService.updateItemStatus(adminUserDetails(), s.getId(), "PENDING");

            assertThat(settlementRepository.findById(s.getId()).orElseThrow().getStatus()).isEqualTo("PENDING");
        }

        @Test
        @DisplayName("이미 PAID인 건은 어떤 변경도 거부된다")
        void 이미_PAID인건_변경거부() throws Exception {
            Settlement s = createSettlement(engineerUser, agency, 95000, "PAID", JUNE_1.plusDays(4), "김철수");

            assertThatThrownBy(() -> adminSettlementService.updateItemStatus(adminUserDetails(), s.getId(), "DISPUTED"))
                    .isInstanceOf(IllegalStateException.class);

            assertThat(settlementRepository.findById(s.getId()).orElseThrow().getStatus()).isEqualTo("PAID");
        }

        @Test
        @DisplayName("ADMIN이 아닌 role → IllegalAccessException")
        void ADMIN이_아니면_예외() throws Exception {
            Settlement s = createSettlement(engineerUser, agency, 95000, "PENDING", JUNE_1.plusDays(4), "김철수");
            CustomUserDetails agencyUserDetails =
                    new CustomUserDetails(1L, "agency@test.com", "pw", "AGENCY", agency.getId());

            assertThatThrownBy(() -> adminSettlementService.updateItemStatus(agencyUserDetails, s.getId(), "DISPUTED"))
                    .isInstanceOf(IllegalAccessException.class);
        }

        @Test
        @DisplayName("존재하지 않는 정산 → NoSuchElementException")
        void 존재하지않는_정산_예외() {
            assertThatThrownBy(() -> adminSettlementService.updateItemStatus(adminUserDetails(), 999999L, "DISPUTED"))
                    .isInstanceOf(java.util.NoSuchElementException.class);
        }
    }

    // ── [⑫] 배치 단위 정산 상태 변경 (보류/재검토) ────────────────────

    @Nested
    @DisplayName("TC-I. ⑫ 배치 단위 정산 상태 변경 (platform_settlements 보류/재검토)")
    class UpdateBatchStatus {

        @Test
        @DisplayName("PENDING 배치 → DISPUTED 전이 성공")
        void PENDING에서_DISPUTED로_전이() throws Exception {
            Settlement s = createSettlement(engineerUser, agency, 95000, "PENDING", JUNE_1.plusDays(4), "김철수");
            PlatformSettlement ps = createPlatformSettlementAndLink(agency, 2026, 6, "PENDING", List.of(s));

            adminSettlementService.updateBatchStatus(adminUserDetails(), agency.getId(), 2026, 6, "DISPUTED");

            assertThat(platformSettlementRepository.findById(ps.getId()).orElseThrow().getStatus()).isEqualTo("DISPUTED");
        }

        @Test
        @DisplayName("DISPUTED 배치 → PENDING 재검토 성공")
        void DISPUTED에서_PENDING으로_복귀() throws Exception {
            Settlement s = createSettlement(engineerUser, agency, 95000, "PENDING", JUNE_1.plusDays(4), "김철수");
            PlatformSettlement ps = createPlatformSettlementAndLink(agency, 2026, 6, "DISPUTED", List.of(s));

            adminSettlementService.updateBatchStatus(adminUserDetails(), agency.getId(), 2026, 6, "PENDING");

            assertThat(platformSettlementRepository.findById(ps.getId()).orElseThrow().getStatus()).isEqualTo("PENDING");
        }

        @Test
        @DisplayName("이미 PAID인 배치는 어떤 변경도 거부된다")
        void 이미_PAID인배치_변경거부() throws Exception {
            Settlement s = createSettlement(engineerUser, agency, 95000, "PAID", JUNE_1.plusDays(4), "김철수");
            PlatformSettlement ps = createPlatformSettlementAndLink(agency, 2026, 6, "PAID", List.of(s));

            assertThatThrownBy(() ->
                    adminSettlementService.updateBatchStatus(adminUserDetails(), agency.getId(), 2026, 6, "DISPUTED"))
                    .isInstanceOf(IllegalStateException.class);

            assertThat(platformSettlementRepository.findById(ps.getId()).orElseThrow().getStatus()).isEqualTo("PAID");
        }

        @Test
        @DisplayName("ADMIN이 아닌 role → IllegalAccessException")
        void ADMIN이_아니면_예외() throws Exception {
            Settlement s = createSettlement(engineerUser, agency, 95000, "PENDING", JUNE_1.plusDays(4), "김철수");
            createPlatformSettlementAndLink(agency, 2026, 6, "PENDING", List.of(s));
            CustomUserDetails agencyUserDetails =
                    new CustomUserDetails(1L, "agency@test.com", "pw", "AGENCY", agency.getId());

            assertThatThrownBy(() ->
                    adminSettlementService.updateBatchStatus(agencyUserDetails, agency.getId(), 2026, 6, "DISPUTED"))
                    .isInstanceOf(IllegalAccessException.class);
        }

        @Test
        @DisplayName("해당 기간 배치가 없으면 NoSuchElementException")
        void 배치없으면_예외() {
            assertThatThrownBy(() ->
                    adminSettlementService.updateBatchStatus(adminUserDetails(), agency.getId(), 2026, 6, "DISPUTED"))
                    .isInstanceOf(java.util.NoSuchElementException.class);
        }

        @Test
        @DisplayName("존재하지 않는 대행사 → NoSuchElementException")
        void 존재하지않는_대행사_예외() {
            assertThatThrownBy(() ->
                    adminSettlementService.updateBatchStatus(adminUserDetails(), 999999L, 2026, 6, "DISPUTED"))
                    .isInstanceOf(java.util.NoSuchElementException.class);
        }
    }
}
