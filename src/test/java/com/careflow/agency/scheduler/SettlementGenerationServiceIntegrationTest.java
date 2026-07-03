package com.careflow.agency.scheduler;

import com.careflow.agency.entity.Agencies;
import com.careflow.agency.repository.AgenciesRepository;
import com.careflow.appliance.entity.Appliance;
import com.careflow.appliance.entity.ApplianceCategory;
import com.careflow.appliance.repository.ApplianceCategoryRepository;
import com.careflow.appliance.repository.ApplianceRepository;
import com.careflow.as_request.entity.AsRequest;
import com.careflow.as_request.repository.AsRequestRepository;
import com.careflow.assignment.entity.AsAssignment;
import com.careflow.assignment.repository.AsAssignmentRepository;
import com.careflow.common.enums.AssignType;
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

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * SettlementGenerationService 통합 테스트 (H2 DB 연동)
 *
 * - @SpringBootTest: 전체 애플리케이션 컨텍스트 로드
 * - @ActiveProfiles("local"): H2 인메모리 DB 사용
 * - settlement_cleanup.sql: 각 테스트 전 관련 테이블 전체 초기화 (platform_settlements 포함)
 *
 * generateForMonth() 실행 결과로 settlements 뿐 아니라 platform_settlements 가
 * 대행사·연·월 단위로 올바르게 집계·생성되는지, settlements.platform_settlement_id 가
 * 정확히 연결되는지를 실제 DB 레벨에서 검증한다.
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
    @Autowired private AsAssignmentRepository asAssignmentRepository;
    @Autowired private SettlementRepository settlementRepository;
    @Autowired private PlatformSettlementRepository platformSettlementRepository;

    private Agencies agency;
    private User engineerUser;
    private User customerUser;
    private ApplianceCategory leafCategory;
    private Regions district;

    // findSettlementTargets 는 payment.paidAt 을 기준으로 targetMonth 범위를 필터링하므로,
    // 결제를 지금 이 시각(now)에 생성하고 targetMonth 도 이번 달로 맞춰 별도의 날짜 조작 없이 자연스럽게 매칭시킨다.
    private YearMonth targetMonth;

    @BeforeEach
    void setUp() {
        targetMonth = YearMonth.now();

        agency = agencyRepository.save(
                Agencies.create("테스트대행사", "123-45-67890", "서울시 강남구", 10.0));

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

    /** 결제 완료(SUCCESS) + 배정 완료(COMPLETED) 상태의 Payment 1건 생성 — 정산 생성 대상 */
    private Payment createSettlementTargetPayment(Agencies targetAgency, User engineer, int amount) {
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
        // findSettlementTargets 쿼리가 r.agency 를 INNER JOIN FETCH 하므로 배정 대행사를 반드시 설정해야 함
        asRequest.processAssignment(targetAgency);
        asRequest = asRequestRepository.save(asRequest);

        Payment payment = paymentRepository.save(Payment.create(asRequest, customerUser, amount));
        payment.markSuccess();
        payment = paymentRepository.save(payment);

        AsAssignment assignment = asAssignmentRepository.save(
                AsAssignment.create(asRequest, engineer, targetAgency, AssignType.MANUAL));
        asAssignmentRepository.updateStatus(assignment.getId(), "COMPLETED");

        return payment;
    }

    /** 배정이 전혀 없는(정산 생성 불가) Payment 1건 생성 — 스킵 대상 */
    private Payment createUnassignedPayment(int amount) {
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
        // 배정 자체는 없지만, findSettlementTargets 쿼리의 INNER JOIN FETCH r.agency 통과를 위해 대행사만 설정
        // (실제 배정 완료 없이 "결제는 됐지만 담당 기사가 없는" 케이스를 재현)
        asRequest.processAssignment(agency);
        asRequest = asRequestRepository.save(asRequest);

        Payment payment = paymentRepository.save(Payment.create(asRequest, customerUser, amount));
        payment.markSuccess();
        return paymentRepository.save(payment);
    }

    @Test
    @DisplayName("성공: 동일 대행사 정산 2건 생성 — platform_settlement 1건에 합계·건수 집계, settlement에 FK 연결")
    void success_generatesPlatformSettlement_aggregatedFromCreatedSettlements() {
        // Given
        createSettlementTargetPayment(agency, engineerUser, 200000);
        createSettlementTargetPayment(agency, engineerUser, 300000);

        // When
        SettlementGenerationService.Result result = settlementGenerationService.generateForMonth(targetMonth);

        // Then — 생성 결과 요약
        assertThat(result.created()).isEqualTo(2);
        assertThat(result.skipped()).isZero();
        assertThat(result.failed()).isZero();
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

        // settlements.platform_settlement_id FK 연결 검증
        List<Settlement> settlements = settlementRepository.findAll();
        assertThat(settlements).hasSize(2);
        assertThat(settlements)
                .allSatisfy(s -> assertThat(s.getPlatformSettlement().getId())
                        .isEqualTo(platformSettlement.getId()));
    }

    @Test
    @DisplayName("성공: 배정 없는 결제 건 — Settlement 스킵, platform_settlement도 생성되지 않음")
    void success_skipsUnassignedPayment_noPlatformSettlementCreated() {
        // Given
        createUnassignedPayment(150000);

        // When
        SettlementGenerationService.Result result = settlementGenerationService.generateForMonth(targetMonth);

        // Then
        assertThat(result.created()).isZero();
        assertThat(result.skipped()).isEqualTo(1);
        assertThat(result.platformSettlementsCreated()).isZero();
        assertThat(settlementRepository.findAll()).isEmpty();
        assertThat(platformSettlementRepository.findAll()).isEmpty();
    }

    @Test
    @DisplayName("성공: 동일 기간 platform_settlement이 이미 PENDING으로 존재하면 신규 생성 대신 누적")
    void success_accumulatesIntoExistingPendingPlatformSettlement() {
        // Given — 이전 배치 실행으로 이미 만들어진 PENDING 상태의 platform_settlement 1건 선(先) 존재
        PlatformSettlement existing = PlatformSettlement.create(
                agency, targetMonth.getYear(), targetMonth.getMonthValue(), 100000, 10000, 1);
        platformSettlementRepository.save(existing);

        createSettlementTargetPayment(agency, engineerUser, 200000);

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
    }

    @Test
    @DisplayName("성공: 동일 기간 platform_settlement이 이미 PAID면 합계를 변경하지 않고 스킵")
    void success_doesNotMutatePaidPlatformSettlement() {
        // Given — 이미 지급 완료(PAID) 처리된 platform_settlement 1건 선(先) 존재
        PlatformSettlement paid = PlatformSettlement.create(
                agency, targetMonth.getYear(), targetMonth.getMonthValue(), 100000, 10000, 1);
        paid.markPaid();
        platformSettlementRepository.save(paid);

        createSettlementTargetPayment(agency, engineerUser, 200000);

        // When
        SettlementGenerationService.Result result = settlementGenerationService.generateForMonth(targetMonth);

        // Then — Settlement 자체는 생성되지만 이미 PAID인 집계는 절대 변경되지 않음
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
        Optional<Settlement> newSettlement = settlementRepository.findAll().stream()
                .filter(s -> s.getGrossAmount() == 200000)
                .findFirst();
        assertThat(newSettlement).isPresent();
        assertThat(newSettlement.get().getPlatformSettlement()).isNull();
    }
}
