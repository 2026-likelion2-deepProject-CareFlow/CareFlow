package com.careflow.agency.service;

import com.careflow.agency.dto.request.AgencySettlementSearchRequest;
import com.careflow.agency.dto.response.AgencySettlementListResponse;
import com.careflow.agency.entity.Agencies;
import com.careflow.agency.repository.AgenciesRepository;
import com.careflow.appliance.entity.Appliance;
import com.careflow.appliance.entity.ApplianceCategory;
import com.careflow.appliance.repository.ApplianceCategoryRepository;
import com.careflow.appliance.repository.ApplianceRepository;
import com.careflow.as_request.entity.AsRequest;
import com.careflow.as_request.repository.AsRequestRepository;
import com.careflow.auth.security.CustomUserDetails;
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
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * AgencySettlementService 통합 테스트 (H2 DB 연동)
 */
@SpringBootTest
@ActiveProfiles("local")
@Sql(scripts = "/cleanup.sql", executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
@DisplayName("AgencySettlementService 통합 테스트 (H2 DB 연동)")
class AgencySettlementServiceIntegrationTest {

    @Autowired private AgencySettlementService agencySettlementService;

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
    private User agencyUser;
    private User engineerUser;
    private User otherEngineerUser;
    private ApplianceCategory leafCategory;
    private Regions district;
    private Symptom symptom;

    private static final Pageable PAGEABLE = PageRequest.of(0, 10);

    @BeforeEach
    void setUp() {
        agency      = agencyRepository.save(Agencies.create("테스트대행사", "123-45-67890", "서울시 강남구", 5.0));
        otherAgency = agencyRepository.save(Agencies.create("타대행사",     "999-99-99999", "서울시 서초구", 5.0));

        agencyUser = userRepository.save(User.builder()
                .email("agency@test.com").passwordHash("hashed")
                .name("대행사담당자").phone("010-9999-9999")
                .role(Role.AGENCY).agency(agency).build());

        engineerUser = userRepository.save(User.builder()
                .email("engineer@test.com").passwordHash("hashed")
                .name("김현수").phone("010-1234-5678")
                .role(Role.ENGINEER).agency(agency).build());

        otherEngineerUser = userRepository.save(User.builder()
                .email("other@test.com").passwordHash("hashed")
                .name("타기사").phone("010-0000-0000")
                .role(Role.ENGINEER).agency(otherAgency).build());

        ApplianceCategory root = categoryRepository.save(ApplianceCategory.createRoot("가전", 1));
        leafCategory = categoryRepository.save(ApplianceCategory.createChild("에어컨", root, 1));
        district = regionRepository.save(Regions.create("강남구", null, 2, 0));
        symptom  = symptomRepository.save(Symptom.builder()
                .category(leafCategory).symptomCode("TEST").symptomName("테스트증상").build());
    }

    private CustomUserDetails agencyUserDetails() {
        return new CustomUserDetails(agencyUser.getId(), agencyUser.getEmail(), "pw", "AGENCY", agency.getId());
    }

    /** Settlement 한 건 생성 헬퍼 */
    private Settlement createSettlement(User engineer, Agencies targetAgency,
                                        int gross, String status, LocalDateTime createdAt) {
        User customer = userRepository.save(User.builder()
                .email("c" + System.nanoTime() + "@t.com").passwordHash("hashed")
                .name("고객").phone("010-1111-2222").role(Role.CUSTOMER).build());

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
                (int)(gross * 0.1),
                BigDecimal.valueOf(10),
                (int)(gross * 0.1),
                BigDecimal.valueOf(10),
                (int)(gross * 0.8));
        ReflectionTestUtils.setField(s, "status", status);
        if (createdAt != null) ReflectionTestUtils.setField(s, "createdAt", createdAt);
        return settlementRepository.save(s);
    }

    @Nested
    @DisplayName("TC-I-1. 본 대행사 정산만 조회")
    class AgencyIsolation {

        @Test
        void 타대행사_정산은_결과에서_제외된다() throws Exception {
            createSettlement(engineerUser,      agency,      100000, "PAID", null);
            createSettlement(otherEngineerUser, otherAgency, 200000, "PAID", null);

            AgencySettlementListResponse result = agencySettlementService.getSettlements(
                    agencyUserDetails(), new AgencySettlementSearchRequest(), PAGEABLE);

            assertThat(result.content()).hasSize(1);
            assertThat(result.content().get(0).engineerName()).isEqualTo("김현수");
        }
    }

    @Nested
    @DisplayName("TC-I-2. status 필터")
    class StatusFilter {

        @Test
        void PAID_필터시_content_PAID만_포함_stats도_필터결과_기준() throws Exception {
            createSettlement(engineerUser, agency, 100000, "PAID",    null);
            createSettlement(engineerUser, agency, 80000,  "PENDING", null);

            AgencySettlementSearchRequest filter = new AgencySettlementSearchRequest();
            ReflectionTestUtils.setField(filter, "status", "PAID");

            AgencySettlementListResponse result = agencySettlementService.getSettlements(
                    agencyUserDetails(), filter, PAGEABLE);

            assertThat(result.content()).hasSize(1);
            assertThat(result.content().get(0).status()).isEqualTo("PAID");
            // stats도 content와 동일한 필터(PAID)가 적용되어 1건만 집계됨
            assertThat(result.stats().thisMonthCount()).isEqualTo(1L);
            assertThat(result.stats().thisMonthGrossAmount()).isEqualTo(100000L);
            // 필터가 걸려있으므로 전월 대비 diff는 계산되지 않음
            assertThat(result.stats().prevMonthCountDiff()).isNull();
            assertThat(result.stats().prevMonthGrossDiff()).isNull();
        }
    }

    @Nested
    @DisplayName("TC-I-3. keyword 필터")
    class KeywordFilter {

        @Test
        void 기사명_부분일치_검색() throws Exception {
            User otherEngineer2 = userRepository.save(User.builder()
                    .email("eng2@test.com").passwordHash("hashed")
                    .name("이철수").phone("010-5555-6666")
                    .role(Role.ENGINEER).agency(agency).build());

            createSettlement(engineerUser,  agency, 100000, "PAID", null);
            createSettlement(otherEngineer2, agency, 80000, "PAID", null);

            AgencySettlementSearchRequest filter = new AgencySettlementSearchRequest();
            ReflectionTestUtils.setField(filter, "keyword", "김현");

            AgencySettlementListResponse result = agencySettlementService.getSettlements(
                    agencyUserDetails(), filter, PAGEABLE);

            assertThat(result.content()).hasSize(1);
            assertThat(result.content().get(0).engineerName()).isEqualTo("김현수");
        }
    }

    @Nested
    @DisplayName("TC-I-4. dateFrom/dateTo 범위 필터")
    class DateRangeFilter {

        @Test
        void 범위_내_정산만_포함_경계값_포함_stats도_해당범위로_집계() throws Exception {
            createSettlement(engineerUser, agency, 100000, "PAID",
                    LocalDateTime.of(2024, 6, 1, 0, 0));   // 포함 (경계)
            createSettlement(engineerUser, agency, 80000, "PAID",
                    LocalDateTime.of(2024, 6, 30, 12, 0)); // 포함 (범위 내)
            createSettlement(engineerUser, agency, 60000, "PAID",
                    LocalDateTime.of(2024, 7, 1, 0, 0));   // 제외 (경계 밖)

            AgencySettlementSearchRequest filter = new AgencySettlementSearchRequest();
            ReflectionTestUtils.setField(filter, "dateFrom", "2024-06-01");
            ReflectionTestUtils.setField(filter, "dateTo",   "2024-06-30");

            AgencySettlementListResponse result = agencySettlementService.getSettlements(
                    agencyUserDetails(), filter, PAGEABLE);

            assertThat(result.content()).hasSize(2);
            // stats도 dateFrom~dateTo 범위(2건, 180000원)로 집계되고 7월 건은 제외됨
            assertThat(result.stats().thisMonthCount()).isEqualTo(2L);
            assertThat(result.stats().thisMonthGrossAmount()).isEqualTo(180000L);
            assertThat(result.stats().prevMonthCountDiff()).isNull();
            assertThat(result.stats().prevMonthGrossDiff()).isNull();
        }
    }

    @Nested
    @DisplayName("TC-I-5. 페이징")
    class Paging {

        @Test
        void 정산_11건_size_10이면_1페이지_10건_totalPages_2() throws Exception {
            for (int i = 0; i < 11; i++) {
                createSettlement(engineerUser, agency, 100000, "PAID", null);
            }

            AgencySettlementListResponse result = agencySettlementService.getSettlements(
                    agencyUserDetails(), new AgencySettlementSearchRequest(), PAGEABLE);

            assertThat(result.content()).hasSize(10);
            assertThat(result.totalPages()).isEqualTo(2);
            assertThat(result.totalElements()).isEqualTo(11L);
        }
    }

    @Nested
    @DisplayName("TC-I-6. stats 필터 없음 — 전체 기간 집계 + 전월 대비 diff")
    class StatsNoFilter {

        @Test
        void 필터_없으면_이번달_전월_정산_모두_전체기간으로_집계되고_전월대비_diff는_별도_계산된다() throws Exception {
            YearMonth now = YearMonth.now();
            // 이번 달 정산
            createSettlement(engineerUser, agency, 100000, "PAID",
                    now.atDay(1).atStartOfDay());
            // 전월 정산
            createSettlement(engineerUser, agency, 80000, "PAID",
                    now.minusMonths(1).atDay(1).atStartOfDay());

            AgencySettlementListResponse result = agencySettlementService.getSettlements(
                    agencyUserDetails(), new AgencySettlementSearchRequest(), PAGEABLE);

            // 필터가 없으므로 stats 는 전체 기간(이번 달 + 전월) 합산
            assertThat(result.stats().thisMonthCount()).isEqualTo(2L);
            assertThat(result.stats().thisMonthGrossAmount()).isEqualTo(180000L);
            // 전월 대비 diff는 "이번 달(1건/100000원) vs 전월(1건/80000원)" 별도 집계로 계산됨
            assertThat(result.stats().prevMonthCountDiff()).isEqualTo(0L);
            assertThat(result.stats().prevMonthGrossDiff()).isEqualTo(20000L);
        }
    }

    @Nested
    @DisplayName("TC-I-7. periodStart/periodEnd 파생")
    class PeriodDerivation {

        @Test
        void createdAt_6월15일이면_period_6월1일_6월30일() throws Exception {
            createSettlement(engineerUser, agency, 100000, "PAID",
                    LocalDateTime.of(2024, 6, 15, 10, 0));

            AgencySettlementListResponse result = agencySettlementService.getSettlements(
                    agencyUserDetails(), new AgencySettlementSearchRequest(), PAGEABLE);

            assertThat(result.content()).hasSize(1);
            assertThat(result.content().get(0).periodStart()).isEqualTo("2024-06-01");
            assertThat(result.content().get(0).periodEnd()).isEqualTo("2024-06-30");
        }
    }

    @Nested
    @DisplayName("TC-I-8. pendingAmount 합산")
    class PendingAmountStats {

        @Test
        void PENDING_합산이_pendingAmount에_반영() throws Exception {
            // [DDL v11] APPROVED 상태 제거 — pendingAmount는 PENDING 건만 합산
            YearMonth now = YearMonth.now();
            LocalDateTime thisMonth = now.atDay(1).atStartOfDay();

            createSettlement(engineerUser, agency, 50000, "PENDING",  thisMonth);
            createSettlement(engineerUser, agency, 30000, "PENDING",  thisMonth);
            createSettlement(engineerUser, agency, 20000, "DISPUTED", thisMonth);

            AgencySettlementListResponse result = agencySettlementService.getSettlements(
                    agencyUserDetails(), new AgencySettlementSearchRequest(), PAGEABLE);

            assertThat(result.stats().pendingAmount()).isEqualTo(80000L);   // 50000 + 30000
            assertThat(result.stats().disputedAmount()).isEqualTo(20000L);
        }
    }
}
