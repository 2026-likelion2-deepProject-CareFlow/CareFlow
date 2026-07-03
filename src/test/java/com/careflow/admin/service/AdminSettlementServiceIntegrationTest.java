package com.careflow.admin.service;

import com.careflow.admin.dto.response.AdminSettlementDetailResponse;
import com.careflow.admin.dto.response.AdminSettlementSummaryResponse;
import com.careflow.agency.entity.Agencies;
import com.careflow.agency.repository.AgenciesRepository;
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
import com.careflow.settlement.entity.Settlement;
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

    @Nested
    @DisplayName("TC-I. ⑨ 단일 대행사 지급 승인")
    class ApproveAgency {

        @Test
        @DisplayName("PENDING 2건 승인 → 두 건 모두 status=PAID, paidAt not null")
        void PENDING_2건_승인() throws Exception {
            Settlement s1 = createSettlement(engineerUser, agency, 95000, "PENDING", JUNE_1.plusDays(4), "김철수");
            Settlement s2 = createSettlement(engineerUser, agency, 72000, "PENDING", JUNE_1.plusDays(10), "이영희");

            adminSettlementService.approveAgency(adminUserDetails(), agency.getId(), 2026, 6);

            Settlement reloaded1 = settlementRepository.findById(s1.getId()).orElseThrow();
            Settlement reloaded2 = settlementRepository.findById(s2.getId()).orElseThrow();
            assertThat(reloaded1.getStatus()).isEqualTo("PAID");
            assertThat(reloaded1.getPaidAt()).isNotNull();
            assertThat(reloaded2.getStatus()).isEqualTo("PAID");
            assertThat(reloaded2.getPaidAt()).isNotNull();
        }

        @Test
        @DisplayName("타 대행사 정산의 status는 변경되지 않는다")
        void 타대행사_정산불변() throws Exception {
            Settlement otherSettlement = createSettlement(
                    otherEngineerUser, otherAgency, 200000, "PENDING", JUNE_1.plusDays(4), "이영희");

            adminSettlementService.approveAgency(adminUserDetails(), agency.getId(), 2026, 6);

            Settlement reloaded = settlementRepository.findById(otherSettlement.getId()).orElseThrow();
            assertThat(reloaded.getStatus()).isEqualTo("PENDING");
        }

        @Test
        @DisplayName("대상 월이 아닌 정산은 변경되지 않는다")
        void 타월_정산불변() throws Exception {
            Settlement mayS = createSettlement(
                    engineerUser, agency, 95000, "PENDING", LocalDateTime.of(2026, 5, 20, 0, 0), "김철수");

            adminSettlementService.approveAgency(adminUserDetails(), agency.getId(), 2026, 6);

            Settlement reloaded = settlementRepository.findById(mayS.getId()).orElseThrow();
            assertThat(reloaded.getStatus()).isEqualTo("PENDING");
        }

        @Test
        @DisplayName("이미 PAID인 건 재승인 요청 — 에러 없이 정상 종료, paidAt 변경 없음")
        void 이미_PAID인_건_재승인시_변경없음() throws Exception {
            LocalDateTime originalPaidAt = LocalDateTime.of(2026, 6, 10, 9, 0);
            Settlement paidSettlement = createSettlement(
                    engineerUser, agency, 95000, "PAID", JUNE_1.plusDays(4), "김철수");
            settlementRepository.updatePaidAt(paidSettlement.getId(), originalPaidAt);

            adminSettlementService.approveAgency(adminUserDetails(), agency.getId(), 2026, 6);

            Settlement reloaded = settlementRepository.findById(paidSettlement.getId()).orElseThrow();
            assertThat(reloaded.getPaidAt()).isEqualTo(originalPaidAt);
        }

        @Test
        @DisplayName("DISPUTED 건도 승인 대상에 포함되어 PAID로 전이된다")
        void DISPUTED_건도_PAID로_전이() throws Exception {
            Settlement disputed = createSettlement(
                    engineerUser, agency, 95000, "DISPUTED", JUNE_1.plusDays(4), "김철수");

            adminSettlementService.approveAgency(adminUserDetails(), agency.getId(), 2026, 6);

            Settlement reloaded = settlementRepository.findById(disputed.getId()).orElseThrow();
            assertThat(reloaded.getStatus()).isEqualTo("PAID");
        }
    }

    // ── [⑩] 미지급 전체 일괄 승인 ────────────────────────────────

    @Nested
    @DisplayName("TC-I. ⑩ 미지급 전체 일괄 승인")
    class ApproveAll {

        @Test
        @DisplayName("대행사 여러 곳에 걸친 미지급 정산이 전부 PAID로 전이된다")
        void 전체_미지급_승인() throws Exception {
            Settlement s1 = createSettlement(engineerUser, agency, 95000, "PENDING", JUNE_1.plusDays(4), "김철수");
            Settlement s2 = createSettlement(otherEngineerUser, otherAgency, 72000, "PENDING", JUNE_1.plusDays(10), "이영희");

            adminSettlementService.approveAll(adminUserDetails(), 2026, 6);

            assertThat(settlementRepository.findById(s1.getId()).orElseThrow().getStatus()).isEqualTo("PAID");
            assertThat(settlementRepository.findById(s2.getId()).orElseThrow().getStatus()).isEqualTo("PAID");
        }

        @Test
        @DisplayName("이미 PAID인 건은 영향받지 않는다 (paidAt 불변)")
        void 이미_PAID인_건_불변() throws Exception {
            LocalDateTime originalPaidAt = LocalDateTime.of(2026, 6, 10, 9, 0);
            Settlement paidSettlement = createSettlement(
                    engineerUser, agency, 95000, "PAID", JUNE_1.plusDays(4), "김철수");
            settlementRepository.updatePaidAt(paidSettlement.getId(), originalPaidAt);

            adminSettlementService.approveAll(adminUserDetails(), 2026, 6);

            Settlement reloaded = settlementRepository.findById(paidSettlement.getId()).orElseThrow();
            assertThat(reloaded.getPaidAt()).isEqualTo(originalPaidAt);
        }

        @Test
        @DisplayName("대상 월이 아닌 정산은 변경되지 않는다")
        void 타월_정산불변() throws Exception {
            Settlement julyS = createSettlement(
                    engineerUser, agency, 95000, "PENDING", LocalDateTime.of(2026, 7, 5, 0, 0), "김철수");

            adminSettlementService.approveAll(adminUserDetails(), 2026, 6);

            Settlement reloaded = settlementRepository.findById(julyS.getId()).orElseThrow();
            assertThat(reloaded.getStatus()).isEqualTo("PENDING");
        }

        @Test
        @DisplayName("정산이 하나도 없는 월 요청은 에러 없이 정상 종료된다")
        void 정산없는_월_정상종료() throws Exception {
            adminSettlementService.approveAll(adminUserDetails(), 2026, 6);
            // 예외 없이 종료되면 성공
        }
    }
}
