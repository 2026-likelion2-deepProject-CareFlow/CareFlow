package com.careflow.agency.scheduler;

import com.careflow.agency.entity.Agencies;
import com.careflow.agency.repository.AgenciesRepository;
import com.careflow.appliance.entity.Appliance;
import com.careflow.appliance.entity.ApplianceCategory;
import com.careflow.appliance.repository.ApplianceCategoryRepository;
import com.careflow.appliance.repository.ApplianceRepository;
import com.careflow.as_request.entity.AsRequest;
import com.careflow.as_request.repository.AsRequestRepository;
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
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.jdbc.Sql;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * SettlementGenerationService 통합 테스트 (H2 DB 연동)
 *
 * [결제 즉시 정산 데이터 생성 아키텍처] 이후 Settlement 는 PaymentService.processPayment 시점에
 * 이미 PENDING 상태로 생성되어 있다. 이 서비스(월별 배치)는 그렇게 이미 만들어진 Settlement 를
 * settlementRepository.findUnaggregatedSettlements 로 조회해 platform_settlements/engineer_payouts 로
 * 집계(바인딩)하는 역할만 한다. 따라서 이 테스트는 실제 결제 흐름을 재현하는 대신, PaymentService와
 * 동일한 수수료 계산식으로 Settlement 를 직접 생성해두고 집계 로직만 검증한다.
 *
 * - @SpringBootTest: 전체 애플리케이션 컨텍스트 로드
 * - @ActiveProfiles("local"): H2 인메모리 DB 사용
 * - settlement_cleanup.sql: 각 테스트 전 관련 테이블 전체 초기화 (platform_settlements 포함)
 */
@SpringBootTest
@ActiveProfiles("local")
@Sql(scripts = "/settlement_cleanup.sql", executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
@DisplayName("SettlementGenerationService 통합 테스트 (H2 DB 연동)")
class SettlementGenerationServiceIntegrationTest {

    @Autowired private SettlementGenerationService settlementGenerationService;

    @Autowired private UserRepository userRepository;
    @Autowired private AgenciesRepository agencyRepository;
    @Autowired private RegionRepository regionRepository;
    @Autowired private ApplianceCategoryRepository categoryRepository;
    @Autowired private ApplianceRepository applianceRepository;
    @Autowired private SymptomRepository symptomRepository;
    @Autowired private AsRequestRepository asRequestRepository;
    @Autowired private PaymentRepository paymentRepository;
    @Autowired private SettlementRepository settlementRepository;
    @Autowired private PlatformSettlementRepository platformSettlementRepository;
    @Autowired private com.careflow.settlement.repository.EngineerPayoutRepository engineerPayoutRepository;

    private static final BigDecimal PLATFORM_FEE_RATE = BigDecimal.valueOf(0.10);

    private Agencies agency;
    private User engineerUser;
    private User customerUser;
    private ApplianceCategory leafCategory;
    private Regions district;

    // findUnaggregatedSettlements 는 settlements.created_at 을 기준으로 targetMonth 범위를 필터링하므로,
    // Settlement 를 지금 이 시각(now)에 생성하고 targetMonth 도 이번 달로 맞춰 별도의 날짜 조작 없이 자연스럽게 매칭시킨다.
    private YearMonth targetMonth;

    @BeforeEach
    void setUp() {
        targetMonth = YearMonth.now();

        // agencyFeeRate는 DDL v14 기준 소수 형태로 저장(예: 0.1 = 10%) — gross_amount * agencyFeeRate로 바로 곱해 쓰는 값
        agency = agencyRepository.save(
                Agencies.create("테스트대행사", "123-45-67890", "서울시 강남구", 0.1));

        engineerUser = userRepository.save(User.builder()
                .email("engineer@test.com").passwordHash("hashed")
                .name("홍길동").phone("010-1111-1111")
                .role(Role.ENGINEER).agency(agency).build());

        customerUser = userRepository.save(User.builder()
                .email("customer@test.com").passwordHash("hashed")
                .name("김고객").phone("010-3333-3333")
                .role(Role.CUSTOMER).build());

        ApplianceCategory rootCategory = categoryRepository.save(
                ApplianceCategory.createRoot("가전", 1));
        leafCategory = categoryRepository.save(
                ApplianceCategory.createChild("냉장고", rootCategory, 1));

        district = regionRepository.save(Regions.create("강남구", null, 2, 0));
    }

    // ─── 픽스처 헬퍼 ──────────────────────────────────────────────

    /**
     * PaymentService.processPayment과 동일한 수수료 계산식(platformFee=gross*10%,
     * agencyFee=gross*agencyFeeRate, engineerNet=gross-platformFee-agencyFee)으로
     * PENDING 상태의 Settlement 1건을 직접 생성·저장한다 (결제 즉시 생성 아키텍처 재현).
     */
    private Settlement createSettlement(Agencies targetAgency, User engineer, int gross, double agencyFeeRate) {
        Appliance appliance = applianceRepository.save(Appliance.builder()
                .user(customerUser).category(leafCategory)
                .brand("삼성").modelName("냉장고모델").build());

        Symptom symptom = symptomRepository.save(Symptom.builder()
                .category(leafCategory)
                .symptomCode("COOL_FAIL_" + System.nanoTime())
                .symptomName("냉방불량").build());

        AsRequest asRequest = AsRequest.builder()
                .customer(customerUser).appliance(appliance).symptom(symptom)
                .visitRegion(district).visitAddressDetail("101호")
                .scheduledDate(LocalDate.now()).scheduledTime("14:00").build();
        asRequest.processAssignment(targetAgency);
        asRequest = asRequestRepository.save(asRequest);

        Payment payment = paymentRepository.save(Payment.create(asRequest, customerUser, gross));
        payment.markSuccess();
        payment = paymentRepository.save(payment);

        BigDecimal agencyRate = BigDecimal.valueOf(agencyFeeRate);
        int platformFee = PLATFORM_FEE_RATE.multiply(BigDecimal.valueOf(gross))
                .setScale(0, RoundingMode.HALF_UP).intValue();
        int agencyFee = agencyRate.multiply(BigDecimal.valueOf(gross))
                .setScale(0, RoundingMode.HALF_UP).intValue();
        int engineerNet = gross - platformFee - agencyFee;

        Settlement settlement = Settlement.create(payment, asRequest, engineer, targetAgency,
                gross, platformFee, PLATFORM_FEE_RATE, agencyFee, agencyRate, engineerNet);
        return settlementRepository.save(settlement);
    }

    @Test
    @DisplayName("성공: 동일 대행사 정산 2건 존재 — platform_settlement 1건에 합계·건수 집계, settlement에 FK 연결")
    void success_generatesPlatformSettlement_aggregatedFromExistingSettlements() {
        // Given
        createSettlement(agency, engineerUser, 200000, 0.10);
        createSettlement(agency, engineerUser, 300000, 0.10);

        // When
        SettlementGenerationService.Result result = settlementGenerationService.generateForMonth(targetMonth);

        // Then — 생성 결과 요약
        assertThat(result.created()).isEqualTo(2);
        assertThat(result.platformSettlementsCreated()).isEqualTo(1);

        // platform_settlements 집계 검증
        PlatformSettlement platformSettlement = platformSettlementRepository
                .findByAgency_IdAndSettlementYearAndSettlementMonth(
                        agency.getId(), targetMonth.getYear(), targetMonth.getMonthValue())
                .orElseThrow();

        // CareFlow 10% 수수료 기준: gross 200000+300000=500000, fee 20000+30000=50000
        assertThat(platformSettlement.getTotalGrossAmount()).isEqualTo(500000);
        assertThat(platformSettlement.getTotalPlatformFee()).isEqualTo(50000);
        assertThat(platformSettlement.getSettlementCount()).isEqualTo(2);
        assertThat(platformSettlement.getStatus()).isEqualTo("PENDING");
        // payoutAmountSum = gross - platformFee (agencyFee + engineerNetAmount 합) = 500000 - 50000
        assertThat(platformSettlement.getPayoutAmountSum()).isEqualTo(450000);

        // settlements.platform_settlement_id FK 연결 검증
        List<Settlement> settlements = settlementRepository.findAll();
        assertThat(settlements).hasSize(2);
        assertThat(settlements)
                .allSatisfy(s -> assertThat(s.getPlatformSettlement().getId())
                        .isEqualTo(platformSettlement.getId()));
    }

    @Test
    @DisplayName("성공: 동일 대행사+기사 정산 2건 존재 — engineer_payout 1건에 합계·건수 집계, settlement에 FK 연결")
    void success_generatesEngineerPayout_aggregatedFromExistingSettlements() {
        // Given
        createSettlement(agency, engineerUser, 200000, 0.10);
        createSettlement(agency, engineerUser, 300000, 0.10);

        // When
        SettlementGenerationService.Result result = settlementGenerationService.generateForMonth(targetMonth);

        // Then
        assertThat(result.engineerPayoutsCreated()).isEqualTo(1);

        com.careflow.settlement.entity.EngineerPayout engineerPayout = engineerPayoutRepository
                .findByAgency_IdAndEngineer_IdAndPayoutYearAndPayoutMonth(
                        agency.getId(), engineerUser.getId(), targetMonth.getYear(), targetMonth.getMonthValue())
                .orElseThrow();

        // agencyFeeRate=0.1(10%), platformFee=10%(고정) → net = gross*0.8: 200000*0.8+300000*0.8=400000
        assertThat(engineerPayout.getNetAmountSum()).isEqualTo(400000);
        assertThat(engineerPayout.getCaseCount()).isEqualTo(2);
        assertThat(engineerPayout.getStatus()).isEqualTo("PENDING");

        List<Settlement> settlements = settlementRepository.findAll();
        assertThat(settlements)
                .allSatisfy(s -> assertThat(s.getEngineerPayout().getId())
                        .isEqualTo(engineerPayout.getId()));
    }

    @Test
    @DisplayName("성공: 동일 기간 engineer_payout이 이미 PENDING으로 존재하면 신규 생성 대신 누적")
    void success_accumulatesIntoExistingPendingEngineerPayout() {
        com.careflow.settlement.entity.EngineerPayout existing =
                com.careflow.settlement.entity.EngineerPayout.create(agency, engineerUser,
                        targetMonth.getYear(), targetMonth.getMonthValue(), 100000, 1);
        engineerPayoutRepository.save(existing);

        createSettlement(agency, engineerUser, 200000, 0.10);

        SettlementGenerationService.Result result = settlementGenerationService.generateForMonth(targetMonth);

        assertThat(result.created()).isEqualTo(1);
        assertThat(result.engineerPayoutsCreated()).isZero();
        assertThat(engineerPayoutRepository.findAll()).hasSize(1);

        com.careflow.settlement.entity.EngineerPayout accumulated = engineerPayoutRepository
                .findByAgency_IdAndEngineer_IdAndPayoutYearAndPayoutMonth(
                        agency.getId(), engineerUser.getId(), targetMonth.getYear(), targetMonth.getMonthValue())
                .orElseThrow();

        // net: 100000(기존) + 200000*0.8(신규 160000) = 260000, count: 1+1=2
        assertThat(accumulated.getNetAmountSum()).isEqualTo(260000);
        assertThat(accumulated.getCaseCount()).isEqualTo(2);
    }

    @Test
    @DisplayName("성공: 동일 기간 engineer_payout이 이미 PAID면 합계를 변경하지 않고 스킵")
    void success_doesNotMutatePaidEngineerPayout() {
        com.careflow.settlement.entity.EngineerPayout paid =
                com.careflow.settlement.entity.EngineerPayout.create(agency, engineerUser,
                        targetMonth.getYear(), targetMonth.getMonthValue(), 100000, 1);
        paid.markPaid();
        engineerPayoutRepository.save(paid);

        createSettlement(agency, engineerUser, 200000, 0.10);

        SettlementGenerationService.Result result = settlementGenerationService.generateForMonth(targetMonth);

        assertThat(result.created()).isEqualTo(1);
        assertThat(result.engineerPayoutsCreated()).isZero();

        com.careflow.settlement.entity.EngineerPayout unchanged = engineerPayoutRepository
                .findByAgency_IdAndEngineer_IdAndPayoutYearAndPayoutMonth(
                        agency.getId(), engineerUser.getId(), targetMonth.getYear(), targetMonth.getMonthValue())
                .orElseThrow();
        assertThat(unchanged.getNetAmountSum()).isEqualTo(100000);
        assertThat(unchanged.getCaseCount()).isEqualTo(1);
        assertThat(unchanged.getStatus()).isEqualTo("PAID");

        Settlement newSettlement = settlementRepository.findAll().stream()
                .filter(s -> s.getGrossAmount() == 200000)
                .findFirst().orElseThrow();
        assertThat(newSettlement.getEngineerPayout()).isNull();
    }

    @Test
    @DisplayName("성공: 집계 대상 정산이 하나도 없으면 아무것도 생성되지 않는다")
    void success_noSettlements_nothingGenerated() {
        // When
        SettlementGenerationService.Result result = settlementGenerationService.generateForMonth(targetMonth);

        // Then
        assertThat(result.created()).isZero();
        assertThat(result.platformSettlementsCreated()).isZero();
        assertThat(result.engineerPayoutsCreated()).isZero();
        assertThat(platformSettlementRepository.findAll()).isEmpty();
        assertThat(engineerPayoutRepository.findAll()).isEmpty();
    }

    @Test
    @DisplayName("성공: 동일 기간 platform_settlement이 이미 PENDING으로 존재하면 신규 생성 대신 누적")
    void success_accumulatesIntoExistingPendingPlatformSettlement() {
        // Given — 이전 배치 실행으로 이미 만들어진 PENDING 상태의 platform_settlement 1건 선(先) 존재
        PlatformSettlement existing = PlatformSettlement.create(
                agency, targetMonth.getYear(), targetMonth.getMonthValue(), 100000, 10000, 90000, 1);
        platformSettlementRepository.save(existing);

        createSettlement(agency, engineerUser, 200000, 0.10);

        // When
        SettlementGenerationService.Result result = settlementGenerationService.generateForMonth(targetMonth);

        // Then — 신규 생성이 아니라 기존 레코드에 누적되어야 함 (DB에는 여전히 1건만 존재)
        assertThat(result.created()).isEqualTo(1);
        assertThat(result.platformSettlementsCreated()).isZero();
        assertThat(platformSettlementRepository.findAll()).hasSize(1);

        PlatformSettlement accumulated = platformSettlementRepository
                .findByAgency_IdAndSettlementYearAndSettlementMonth(
                        agency.getId(), targetMonth.getYear(), targetMonth.getMonthValue())
                .orElseThrow();

        // gross: 100000 + 200000 = 300000, fee: 10000 + 20000 = 30000, count: 1 + 1 = 2
        assertThat(accumulated.getTotalGrossAmount()).isEqualTo(300000);
        assertThat(accumulated.getTotalPlatformFee()).isEqualTo(30000);
        assertThat(accumulated.getSettlementCount()).isEqualTo(2);
        // payoutAmountSum: 90000 + (200000 - 20000) = 270000
        assertThat(accumulated.getPayoutAmountSum()).isEqualTo(270000);
    }

    @Test
    @DisplayName("성공: 동일 기간 platform_settlement이 이미 PAID면 합계를 변경하지 않고 스킵")
    void success_doesNotMutatePaidPlatformSettlement() {
        // Given — 이미 지급 완료(PAID) 처리된 platform_settlement 1건 선(先) 존재
        PlatformSettlement paid = PlatformSettlement.create(
                agency, targetMonth.getYear(), targetMonth.getMonthValue(), 100000, 10000, 90000, 1);
        paid.markPaid(null);
        platformSettlementRepository.save(paid);

        createSettlement(agency, engineerUser, 200000, 0.10);

        // When
        SettlementGenerationService.Result result = settlementGenerationService.generateForMonth(targetMonth);

        // Then — Settlement 자체는 존재하지만 이미 PAID인 집계는 절대 변경되지 않음
        assertThat(result.created()).isEqualTo(1);
        assertThat(result.platformSettlementsCreated()).isZero();

        PlatformSettlement unchanged = platformSettlementRepository
                .findByAgency_IdAndSettlementYearAndSettlementMonth(
                        agency.getId(), targetMonth.getYear(), targetMonth.getMonthValue())
                .orElseThrow();
        assertThat(unchanged.getTotalGrossAmount()).isEqualTo(100000);
        assertThat(unchanged.getSettlementCount()).isEqualTo(1);
        assertThat(unchanged.getStatus()).isEqualTo("PAID");

        // 새로 생성된 Settlement는 platform_settlement 미할당(NULL) 상태로 남아야 함
        Settlement newSettlement = settlementRepository.findAll().stream()
                .filter(s -> s.getGrossAmount() == 200000)
                .findFirst().orElseThrow();
        assertThat(newSettlement.getPlatformSettlement()).isNull();
    }
}
