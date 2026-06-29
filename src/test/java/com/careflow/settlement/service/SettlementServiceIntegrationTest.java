package com.careflow.settlement.service;

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
import com.careflow.review.entity.Review;
import com.careflow.review.repository.ReviewRepository;
import com.careflow.settlement.dto.EngineerPerformanceResponse;
import com.careflow.settlement.dto.MonthlySummaryResponse;
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

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.NoSuchElementException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * SettlementService 통합 테스트 (H2 DB 연동)
 *
 * - @SpringBootTest: 전체 애플리케이션 컨텍스트 로드
 * - @ActiveProfiles("local"): H2 인메모리 DB 사용
 * - settlement_cleanup.sql: 각 테스트 전 관련 테이블 전체 초기화
 */
@SpringBootTest
@ActiveProfiles("local")
@Sql(scripts = "/settlement_cleanup.sql", executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
@DisplayName("SettlementService 통합 테스트 (H2 DB 연동)")
class SettlementServiceIntegrationTest {

    @Autowired private SettlementService settlementService;

    @Autowired private UserRepository userRepository;
    @Autowired private AgenciesRepository agencyRepository;
    @Autowired private RegionRepository regionRepository;
    @Autowired private ApplianceCategoryRepository categoryRepository;
    @Autowired private ApplianceRepository applianceRepository;
    @Autowired private SymptomRepository symptomRepository;
    @Autowired private AsRequestRepository asRequestRepository;
    @Autowired private PaymentRepository paymentRepository;
    @Autowired private SettlementRepository settlementRepository;
    @Autowired private ReviewRepository reviewRepository;

    // 공통 픽스처 — 각 테스트 전 H2에 INSERT
    private Agencies agency;
    private User agencyUser;
    private User engineerUser1;
    private User engineerUser2;
    private User customerUser;
    private ApplianceCategory leafCategory;
    private Regions district;

    @BeforeEach
    void setUp() {
        // 1. 대행사 INSERT
        agency = agencyRepository.save(
                Agencies.create("테스트대행사", "123-45-67890", "서울시 강남구", 9.0));

        // 2. 대행사 담당자 (JWT 주체) INSERT
        agencyUser = userRepository.save(User.builder()
                .email("agency@test.com").passwordHash("hashed")
                .name("대행사담당자").phone("010-9999-9999")
                .role(Role.AGENCY).agency(agency).build());

        // 3. 기사 2명 INSERT (동일 대행사 소속)
        engineerUser1 = userRepository.save(User.builder()
                .email("engineer1@test.com").passwordHash("hashed")
                .name("홍길동").phone("010-1111-1111")
                .role(Role.ENGINEER).agency(agency).build());
        engineerUser2 = userRepository.save(User.builder()
                .email("engineer2@test.com").passwordHash("hashed")
                .name("이순신").phone("010-2222-2222")
                .role(Role.ENGINEER).agency(agency).build());

        // 4. 고객 INSERT
        customerUser = userRepository.save(User.builder()
                .email("customer@test.com").passwordHash("hashed")
                .name("김고객").phone("010-3333-3333")
                .role(Role.CUSTOMER).build());

        // 5. 카테고리 INSERT
        ApplianceCategory rootCategory = categoryRepository.save(
                ApplianceCategory.createRoot("가전", 1));
        leafCategory = categoryRepository.save(
                ApplianceCategory.createChild("냉장고", rootCategory, 1));

        // 6. 지역 INSERT
        district = regionRepository.save(Regions.create("강남구", null, 2, 0));
    }

    // ─── 픽스처 헬퍼 ──────────────────────────────────────────────

    /**
     * AsRequest → Payment → Settlement 체인을 한 번에 생성하는 헬퍼
     * @param engineer      담당 기사
     * @param grossAmount   작업 총 금액
     * @param paidAt        결제 완료 일시 (paid_at 기준 월 필터링 대상)
     */
    private Settlement createPaidSettlement(User engineer, int grossAmount, LocalDateTime paidAt) {
        // 증상 INSERT
        Symptom symptom = symptomRepository.save(Symptom.builder()
                .category(leafCategory)
                .symptomCode("COOL_FAIL_" + System.nanoTime()) // 유일성 보장
                .symptomName("냉방불량")
                .build());

        // 가전 INSERT
        Appliance appliance = applianceRepository.save(Appliance.builder()
                .user(customerUser)
                .category(leafCategory)
                .brand("삼성").modelName("냉장고모델")
                .build());

        // A/S 신청 INSERT
        AsRequest asRequest = asRequestRepository.save(AsRequest.builder()
                .customer(customerUser)
                .appliance(appliance)
                .symptom(symptom)
                .visitRegion(district)
                .visitAddressDetail("101호")
                .scheduledDate(LocalDate.now())
                .scheduledTime("14:00")
                .build());

        // 결제 INSERT
        Payment payment = paymentRepository.save(Payment.create(asRequest, customerUser, grossAmount));
        payment.markSuccess();
        paymentRepository.save(payment);

        // 정산 산정: CareFlow 10%, 대행사 9%, 기사 81%
        int platformFee = (int) (grossAmount * 0.10);
        int agencyFee   = (int) (grossAmount * 0.09);
        int engineerNet = grossAmount - platformFee - agencyFee;

        Settlement settlement = Settlement.create(
                payment, asRequest, engineer, agency,
                grossAmount, platformFee, new BigDecimal("10.00"),
                agencyFee, new BigDecimal("9.00"), engineerNet);

        // paid_at 직접 설정을 위해 저장 후 리플렉션 없이 markPaid 호출 후 재저장
        settlement.markPaid();
        settlement = settlementRepository.save(settlement);

        // paid_at 을 지정 일시로 맞추기 위해 JPQL UPDATE 사용
        settlementRepository.updatePaidAt(settlement.getId(), paidAt);

        return settlement;
    }

    // ══════════════════════════════════════════════════════════════
    //  1. 기사별 실적 리포트 조회
    // ══════════════════════════════════════════════════════════════
    @Nested
    @DisplayName("getEngineerPerformance — 기사별 실적 리포트")
    class GetEngineerPerformance {

        @Test
        @DisplayName("성공: 6월 정산 2건 삽입 — 기사별 건수·금액 집계 검증")
        void success_twoSettlements_aggregated() {
            // Given — 6월 정산 2건 (각 기사 1건씩)
            LocalDateTime june = LocalDateTime.of(2026, 6, 15, 10, 0);
            createPaidSettlement(engineerUser1, 200000, june);
            createPaidSettlement(engineerUser2, 300000, june);

            // When
            EngineerPerformanceResponse result =
                    settlementService.getEngineerPerformance(agencyUser.getId(), 2026, 6);

            // Then
            assertThat(result.getEngineers()).hasSize(2);

            var eng1 = result.getEngineers().stream()
                    .filter(e -> e.getEngineerId().equals(engineerUser1.getId())).findFirst().orElseThrow();
            assertThat(eng1.getEngineerName()).isEqualTo("홍길동");
            assertThat(eng1.getCompletedCount()).isEqualTo(1L);
            // engineer_net = 200000 - 20000 - 18000 = 162000
            assertThat(eng1.getTotalEarning()).isEqualTo(162000L);
        }

        @Test
        @DisplayName("성공: 해당 월 데이터 없음 — 빈 리스트 반환")
        void success_emptyList_whenNoData() {
            // Given — 정산 데이터 없음

            // When
            EngineerPerformanceResponse result =
                    settlementService.getEngineerPerformance(agencyUser.getId(), 2026, 6);

            // Then
            assertThat(result.getEngineers()).isEmpty();
        }

        @Test
        @DisplayName("성공: 7월 정산은 6월 조회에서 제외")
        void success_julySettlement_excludedFromJuneQuery() {
            // Given — 7월 정산 1건
            LocalDateTime july = LocalDateTime.of(2026, 7, 1, 10, 0);
            createPaidSettlement(engineerUser1, 200000, july);

            // When — 6월 조회
            EngineerPerformanceResponse result =
                    settlementService.getEngineerPerformance(agencyUser.getId(), 2026, 6);

            // Then — 7월 정산은 포함되지 않음
            assertThat(result.getEngineers()).isEmpty();
        }

        @Test
        @DisplayName("성공: 리뷰 있는 기사 — avgRating 집계 검증")
        void success_avgRating_aggregated() {
            // Given
            LocalDateTime june = LocalDateTime.of(2026, 6, 10, 10, 0);
            Settlement s = createPaidSettlement(engineerUser1, 200000, june);

            // 리뷰 INSERT — 평점 4
            reviewRepository.save(Review.create(
                    s.getAsRequest(), customerUser, engineerUser1, 4, "좋아요"));

            // When
            EngineerPerformanceResponse result =
                    settlementService.getEngineerPerformance(agencyUser.getId(), 2026, 6);

            // Then
            var eng1 = result.getEngineers().get(0);
            assertThat(eng1.getAvgRating()).isEqualTo(4.0);
        }

        @Test
        @DisplayName("성공: 리뷰 없는 기사 — avgRating null 반환")
        void success_avgRatingNull_whenNoReview() {
            // Given
            LocalDateTime june = LocalDateTime.of(2026, 6, 10, 10, 0);
            createPaidSettlement(engineerUser1, 200000, june);

            // When
            EngineerPerformanceResponse result =
                    settlementService.getEngineerPerformance(agencyUser.getId(), 2026, 6);

            // Then
            assertThat(result.getEngineers().get(0).getAvgRating()).isNull();
        }

        @Test
        @DisplayName("실패: 소속 대행사 없는 유저 — NoSuchElementException")
        void fail_noAgency_throwsException() {
            // Given — 대행사 없는 유저 INSERT
            User noAgencyUser = userRepository.save(User.builder()
                    .email("noagency@test.com").passwordHash("hashed")
                    .name("무소속").phone("010-0000-0000")
                    .role(Role.AGENCY).build());

            // When & Then
            assertThatThrownBy(() ->
                    settlementService.getEngineerPerformance(noAgencyUser.getId(), 2026, 6))
                    .isInstanceOf(NoSuchElementException.class)
                    .hasMessage("소속 대행사 정보가 없습니다.");
        }
    }

    // ══════════════════════════════════════════════════════════════
    //  2. 월별 합산 내역 조회
    // ══════════════════════════════════════════════════════════════
    @Nested
    @DisplayName("getMonthlySummary — 월별 합산 내역")
    class GetMonthlySummary {

        @Test
        @DisplayName("성공: 6월 정산 2건 합산 검증")
        void success_twoSettlements_summedCorrectly() {
            // Given — 기사 2명, 각 200000 / 300000
            LocalDateTime june = LocalDateTime.of(2026, 6, 15, 10, 0);
            createPaidSettlement(engineerUser1, 200000, june);
            createPaidSettlement(engineerUser2, 300000, june);

            // When
            MonthlySummaryResponse result =
                    settlementService.getMonthlySummary(agencyUser.getId(), 2026, 6);

            // Then
            assertThat(result.getTotalCount()).isEqualTo(2L);
            // gross_amount 합 = 500000
            assertThat(result.getTotalGrossAmount()).isEqualTo(500000L);
            // platform_fee 합: 200000*0.10 + 300000*0.10 = 20000 + 30000 = 50000
            assertThat(result.getTotalPlatformFee()).isEqualTo(50000L);
            // agency_fee 합: 200000*0.09 + 300000*0.09 = 18000 + 27000 = 45000
            assertThat(result.getTotalAgencyFee()).isEqualTo(45000L);
        }

        @Test
        @DisplayName("성공: 데이터 없는 월 — 모든 금액 필드 0 반환")
        void success_allZero_whenNoData() {
            MonthlySummaryResponse result =
                    settlementService.getMonthlySummary(agencyUser.getId(), 2026, 6);

            assertThat(result.getTotalCount()).isZero();
            assertThat(result.getTotalGrossAmount()).isZero();
            assertThat(result.getTotalPlatformFee()).isZero();
        }

        @Test
        @DisplayName("성공: 7월 정산은 6월 합산에 포함되지 않음")
        void success_julyExcludedFromJuneSummary() {
            // Given — 7월 정산
            LocalDateTime july = LocalDateTime.of(2026, 7, 5, 10, 0);
            createPaidSettlement(engineerUser1, 500000, july);

            // When — 6월 조회
            MonthlySummaryResponse result =
                    settlementService.getMonthlySummary(agencyUser.getId(), 2026, 6);

            // Then
            assertThat(result.getTotalCount()).isZero();
        }
    }
}
