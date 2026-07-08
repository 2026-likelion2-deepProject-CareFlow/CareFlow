package com.careflow.agency.scheduler;

import com.careflow.agency.entity.Agencies;
import com.careflow.as_request.entity.AsRequest;
import com.careflow.payment.entity.Payment;
import com.careflow.settlement.entity.PlatformSettlement;
import com.careflow.settlement.entity.Settlement;
import com.careflow.settlement.repository.EngineerPayoutRepository;
import com.careflow.settlement.repository.PlatformSettlementRepository;
import com.careflow.settlement.repository.SettlementRepository;
import com.careflow.user.entity.User;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.YearMonth;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

/**
 * SettlementGenerationService 단위 테스트
 *
 * [결제 즉시 정산 데이터 생성 아키텍처] 이후 Settlement 는 PaymentService.processPayment 시점에
 * 이미 생성되어 있으므로, 이 서비스(월별 배치)는 settlementRepository.findUnaggregatedSettlements 로
 * 조회된 기존 Settlement 목록을 platform_settlements/engineer_payouts 로 집계하는 역할만 검증한다.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("SettlementGenerationService 단위 테스트")
class SettlementGenerationServiceTest {

    @InjectMocks
    private SettlementGenerationService settlementGenerationService;

    @Mock private SettlementRepository settlementRepository;
    @Mock private PlatformSettlementRepository platformSettlementRepository;
    @Mock private EngineerPayoutRepository engineerPayoutRepository;

    private static final YearMonth TARGET_MONTH = YearMonth.of(2026, 6);
    private static final BigDecimal PLATFORM_FEE_RATE = BigDecimal.valueOf(0.10);

    private Agencies mockAgency(Long id, String name) {
        Agencies agency = mock(Agencies.class);
        given(agency.getId()).willReturn(id);
        given(agency.getAgencyName()).willReturn(name);
        return agency;
    }

    private User mockEngineer(Long id, String name) {
        User engineer = mock(User.class);
        given(engineer.getId()).willReturn(id);
        given(engineer.getName()).willReturn(name);
        return engineer;
    }

    // PaymentService.processPayment 과 동일한 수수료 계산식으로 Settlement 픽스처를 만든다
    // (platformFee=gross*10%, agencyFee=gross*agencyFeeRate, engineerNet=gross-platformFee-agencyFee)
    private Settlement settlementFor(Agencies agency, User engineer, int gross, double agencyFeeRate) {
        BigDecimal agencyRate = BigDecimal.valueOf(agencyFeeRate);
        int platformFee = PLATFORM_FEE_RATE.multiply(BigDecimal.valueOf(gross))
                .setScale(0, RoundingMode.HALF_UP).intValue();
        int agencyFee = agencyRate.multiply(BigDecimal.valueOf(gross))
                .setScale(0, RoundingMode.HALF_UP).intValue();
        int engineerNet = gross - platformFee - agencyFee;

        return Settlement.create(mock(Payment.class), mock(AsRequest.class), engineer, agency,
                gross, platformFee, PLATFORM_FEE_RATE, agencyFee, agencyRate, engineerNet);
    }

    private void stubNoExistingPlatformSettlement() {
        given(platformSettlementRepository.findByAgency_IdAndSettlementYearAndSettlementMonth(anyLong(), eq(2026), eq(6)))
                .willReturn(Optional.empty());
    }

    private void stubNoExistingEngineerPayout() {
        given(engineerPayoutRepository.findByAgency_IdAndEngineer_IdAndPayoutYearAndPayoutMonth(anyLong(), anyLong(), eq(2026), eq(6)))
                .willReturn(Optional.empty());
    }

    @Nested
    @DisplayName("generateForMonth — platform_settlements 집계")
    class GeneratePlatformSettlements {

        @Test
        @DisplayName("성공: 동일 대행사 정산 2건 → platform_settlement 1건에 합계·건수 집계")
        void success_sameAgency_aggregatedIntoOnePlatformSettlement() {
            // Given
            Agencies agency = mockAgency(1L, "케어플로우 서울대행사");
            User engineer = mockEngineer(10L, "홍길동");

            Settlement settlement1 = settlementFor(agency, engineer, 200000, 0.10);
            Settlement settlement2 = settlementFor(agency, engineer, 300000, 0.10);

            given(settlementRepository.findUnaggregatedSettlements(any(), any()))
                    .willReturn(List.of(settlement1, settlement2));
            stubNoExistingPlatformSettlement();
            stubNoExistingEngineerPayout();

            // When
            SettlementGenerationService.Result result = settlementGenerationService.generateForMonth(TARGET_MONTH);

            // Then
            assertThat(result.created()).isEqualTo(2);
            assertThat(result.platformSettlementsCreated()).isEqualTo(1);

            ArgumentCaptor<PlatformSettlement> captor = ArgumentCaptor.forClass(PlatformSettlement.class);
            verify(platformSettlementRepository).save(captor.capture());
            PlatformSettlement saved = captor.getValue();

            // gross: 200000 + 300000 = 500000, fee(10%): 20000 + 30000 = 50000
            assertThat(saved.getTotalGrossAmount()).isEqualTo(500000);
            assertThat(saved.getTotalPlatformFee()).isEqualTo(50000);
            assertThat(saved.getSettlementCount()).isEqualTo(2);
            assertThat(saved.getSettlementYear()).isEqualTo(2026);
            assertThat(saved.getSettlementMonth()).isEqualTo(6);
            // payoutAmountSum = gross - platformFee = 500000 - 50000
            assertThat(saved.getPayoutAmountSum()).isEqualTo(450000);
        }

        @Test
        @DisplayName("성공: 서로 다른 대행사 정산 각 1건 → platform_settlement 각각 1건씩 생성")
        void success_differentAgencies_createSeparatePlatformSettlements() {
            // Given
            Agencies agencyA = mockAgency(1L, "A대행사");
            Agencies agencyB = mockAgency(2L, "B대행사");
            User engineer = mockEngineer(10L, "홍길동");

            Settlement settlement1 = settlementFor(agencyA, engineer, 200000, 0.10);
            Settlement settlement2 = settlementFor(agencyB, engineer, 100000, 0.08);

            given(settlementRepository.findUnaggregatedSettlements(any(), any()))
                    .willReturn(List.of(settlement1, settlement2));
            stubNoExistingPlatformSettlement();
            stubNoExistingEngineerPayout();

            // When
            SettlementGenerationService.Result result = settlementGenerationService.generateForMonth(TARGET_MONTH);

            // Then
            assertThat(result.created()).isEqualTo(2);
            assertThat(result.platformSettlementsCreated()).isEqualTo(2);
            verify(platformSettlementRepository, times(2)).save(any(PlatformSettlement.class));
        }

        @Test
        @DisplayName("성공: 집계 대상 정산 0건 — platform_settlement 생성 없음")
        void success_noTargets_noPlatformSettlementCreated() {
            // Given
            given(settlementRepository.findUnaggregatedSettlements(any(), any())).willReturn(List.of());

            // When
            SettlementGenerationService.Result result = settlementGenerationService.generateForMonth(TARGET_MONTH);

            // Then
            assertThat(result.created()).isZero();
            assertThat(result.platformSettlementsCreated()).isZero();
            verify(platformSettlementRepository, never()).save(any());
        }

        @Test
        @DisplayName("성공: 기존 PENDING platform_settlement 존재 시 신규 생성 대신 기존 합계에 누적")
        void success_existingPendingPlatformSettlement_accumulatesInsteadOfCreating() {
            // Given
            Agencies agency = mockAgency(1L, "케어플로우 서울대행사");
            User engineer = mockEngineer(10L, "홍길동");

            Settlement settlement = settlementFor(agency, engineer, 200000, 0.10);
            given(settlementRepository.findUnaggregatedSettlements(any(), any()))
                    .willReturn(List.of(settlement));
            stubNoExistingEngineerPayout();

            PlatformSettlement existing = PlatformSettlement.create(agency, 2026, 6, 100000, 10000, 90000, 1);
            given(platformSettlementRepository.findByAgency_IdAndSettlementYearAndSettlementMonth(1L, 2026, 6))
                    .willReturn(Optional.of(existing));

            // When
            SettlementGenerationService.Result result = settlementGenerationService.generateForMonth(TARGET_MONTH);

            // Then — 신규 저장(save) 없이 기존 레코드에 누적만 발생, 반환 카운트는 "신규 생성" 기준이라 0
            assertThat(result.platformSettlementsCreated()).isZero();
            verify(platformSettlementRepository, never()).save(any());

            // gross: 100000 + 200000 = 300000, fee: 10000 + 20000 = 30000, count: 1 + 1 = 2
            assertThat(existing.getTotalGrossAmount()).isEqualTo(300000);
            assertThat(existing.getTotalPlatformFee()).isEqualTo(30000);
            assertThat(existing.getSettlementCount()).isEqualTo(2);
            // payoutAmountSum: 90000 + (200000 - 20000) = 270000
            assertThat(existing.getPayoutAmountSum()).isEqualTo(270000);
        }

        @Test
        @DisplayName("성공: 기존 platform_settlement이 이미 PAID면 누적하지 않고 스킵")
        void success_existingPaidPlatformSettlement_skipsWithoutMutating() {
            // Given
            Agencies agency = mockAgency(1L, "케어플로우 서울대행사");
            User engineer = mockEngineer(10L, "홍길동");

            Settlement settlement = settlementFor(agency, engineer, 200000, 0.10);
            given(settlementRepository.findUnaggregatedSettlements(any(), any()))
                    .willReturn(List.of(settlement));
            stubNoExistingEngineerPayout();

            PlatformSettlement paidSettlement = PlatformSettlement.create(agency, 2026, 6, 100000, 10000, 90000, 1);
            paidSettlement.markPaid(null);
            given(platformSettlementRepository.findByAgency_IdAndSettlementYearAndSettlementMonth(1L, 2026, 6))
                    .willReturn(Optional.of(paidSettlement));

            // When
            SettlementGenerationService.Result result = settlementGenerationService.generateForMonth(TARGET_MONTH);

            // Then — 이미 지급 완료된 집계는 절대 변경되지 않아야 함
            assertThat(result.platformSettlementsCreated()).isZero();
            verify(platformSettlementRepository, never()).save(any());
            assertThat(paidSettlement.getTotalGrossAmount()).isEqualTo(100000);
            assertThat(paidSettlement.getSettlementCount()).isEqualTo(1);
        }
    }

    @Nested
    @DisplayName("generateForMonth — engineer_payouts 집계")
    class GenerateEngineerPayouts {

        @Test
        @DisplayName("성공: 동일 대행사+기사 정산 2건 → engineer_payout 1건에 합계·건수 집계")
        void success_sameAgencyAndEngineer_aggregatedIntoOneEngineerPayout() {
            Agencies agency = mockAgency(1L, "케어플로우 서울대행사");
            User engineer = mockEngineer(10L, "홍길동");

            Settlement settlement1 = settlementFor(agency, engineer, 200000, 0.10);
            Settlement settlement2 = settlementFor(agency, engineer, 300000, 0.10);

            given(settlementRepository.findUnaggregatedSettlements(any(), any()))
                    .willReturn(List.of(settlement1, settlement2));
            stubNoExistingPlatformSettlement();
            stubNoExistingEngineerPayout();

            SettlementGenerationService.Result result = settlementGenerationService.generateForMonth(TARGET_MONTH);

            assertThat(result.engineerPayoutsCreated()).isEqualTo(1);

            ArgumentCaptor<com.careflow.settlement.entity.EngineerPayout> captor =
                    ArgumentCaptor.forClass(com.careflow.settlement.entity.EngineerPayout.class);
            verify(engineerPayoutRepository).save(captor.capture());
            com.careflow.settlement.entity.EngineerPayout saved = captor.getValue();

            // agencyFeeRate=10%, platformFee=10%(고정) → net = gross*0.8: 200000*0.8+300000*0.8=400000
            assertThat(saved.getNetAmountSum()).isEqualTo(400000);
            assertThat(saved.getCaseCount()).isEqualTo(2);
            assertThat(saved.getPayoutYear()).isEqualTo(2026);
            assertThat(saved.getPayoutMonth()).isEqualTo(6);
        }

        @Test
        @DisplayName("성공: 같은 대행사 소속 서로 다른 기사 정산 각 1건 → engineer_payout 각각 생성")
        void success_differentEngineers_createSeparateEngineerPayouts() {
            Agencies agency = mockAgency(1L, "케어플로우 서울대행사");
            User engineerA = mockEngineer(10L, "홍길동");
            User engineerB = mockEngineer(20L, "김철수");

            Settlement settlement1 = settlementFor(agency, engineerA, 200000, 0.10);
            Settlement settlement2 = settlementFor(agency, engineerB, 100000, 0.10);

            given(settlementRepository.findUnaggregatedSettlements(any(), any()))
                    .willReturn(List.of(settlement1, settlement2));
            stubNoExistingPlatformSettlement();
            stubNoExistingEngineerPayout();

            SettlementGenerationService.Result result = settlementGenerationService.generateForMonth(TARGET_MONTH);

            assertThat(result.engineerPayoutsCreated()).isEqualTo(2);
            verify(engineerPayoutRepository, times(2)).save(any());
        }

        @Test
        @DisplayName("성공: 기존 PENDING engineer_payout 존재 시 신규 생성 대신 기존 합계에 누적")
        void success_existingPendingEngineerPayout_accumulatesInsteadOfCreating() {
            Agencies agency = mockAgency(1L, "케어플로우 서울대행사");
            User engineer = mockEngineer(10L, "홍길동");

            Settlement settlement = settlementFor(agency, engineer, 200000, 0.10);
            given(settlementRepository.findUnaggregatedSettlements(any(), any()))
                    .willReturn(List.of(settlement));
            stubNoExistingPlatformSettlement();

            com.careflow.settlement.entity.EngineerPayout existing =
                    com.careflow.settlement.entity.EngineerPayout.create(agency, engineer, 2026, 6, 100000, 1);
            given(engineerPayoutRepository.findByAgency_IdAndEngineer_IdAndPayoutYearAndPayoutMonth(1L, 10L, 2026, 6))
                    .willReturn(Optional.of(existing));

            SettlementGenerationService.Result result = settlementGenerationService.generateForMonth(TARGET_MONTH);

            assertThat(result.engineerPayoutsCreated()).isZero();
            verify(engineerPayoutRepository, never()).save(any());

            // net: 기존 100000 + 신규(200000*0.8=160000) = 260000, count: 1+1=2
            assertThat(existing.getNetAmountSum()).isEqualTo(260000);
            assertThat(existing.getCaseCount()).isEqualTo(2);
        }

        @Test
        @DisplayName("성공: 기존 engineer_payout이 이미 PAID면 누적하지 않고 스킵")
        void success_existingPaidEngineerPayout_skipsWithoutMutating() {
            Agencies agency = mockAgency(1L, "케어플로우 서울대행사");
            User engineer = mockEngineer(10L, "홍길동");

            Settlement settlement = settlementFor(agency, engineer, 200000, 0.10);
            given(settlementRepository.findUnaggregatedSettlements(any(), any()))
                    .willReturn(List.of(settlement));
            stubNoExistingPlatformSettlement();

            com.careflow.settlement.entity.EngineerPayout paid =
                    com.careflow.settlement.entity.EngineerPayout.create(agency, engineer, 2026, 6, 100000, 1);
            paid.markPaid();
            given(engineerPayoutRepository.findByAgency_IdAndEngineer_IdAndPayoutYearAndPayoutMonth(1L, 10L, 2026, 6))
                    .willReturn(Optional.of(paid));

            SettlementGenerationService.Result result = settlementGenerationService.generateForMonth(TARGET_MONTH);

            assertThat(result.engineerPayoutsCreated()).isZero();
            verify(engineerPayoutRepository, never()).save(any());
            assertThat(paid.getNetAmountSum()).isEqualTo(100000);
            assertThat(paid.getCaseCount()).isEqualTo(1);
        }
    }
}
