package com.careflow.agency.scheduler;

import com.careflow.agency.entity.Agencies;
import com.careflow.as_request.entity.AsRequest;
import com.careflow.assignment.entity.AsAssignment;
import com.careflow.assignment.repository.AsAssignmentRepository;
import com.careflow.common.enums.AssignType;
import com.careflow.payment.entity.Payment;
import com.careflow.payment.repository.PaymentRepository;
import com.careflow.settlement.entity.PlatformSettlement;
import com.careflow.settlement.entity.Settlement;
import com.careflow.settlement.repository.PlatformSettlementRepository;
import com.careflow.settlement.repository.SettlementRepository;
import com.careflow.user.entity.User;
import org.junit.jupiter.api.BeforeEach;
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
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
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
 * - 정산(Settlement) 건별 생성 로직은 기존 동작 그대로이며,
 *   신규 추가된 platform_settlements 집계(generatePlatformSettlements) 로직 검증에 집중한다.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("SettlementGenerationService 단위 테스트")
class SettlementGenerationServiceTest {

    @InjectMocks
    private SettlementGenerationService settlementGenerationService;

    @Mock private PaymentRepository paymentRepository;
    @Mock private SettlementRepository settlementRepository;
    @Mock private AsAssignmentRepository asAssignmentRepository;
    @Mock private PlatformSettlementRepository platformSettlementRepository;

    private static final YearMonth TARGET_MONTH = YearMonth.of(2026, 6);

    private Agencies mockAgency(Long id, String name, double feeRate) {
        Agencies agency = mock(Agencies.class);
        given(agency.getId()).willReturn(id);
        given(agency.getAgencyName()).willReturn(name);
        given(agency.getAgencyFeeRate()).willReturn(feeRate);
        return agency;
    }

    private Payment mockPayment(Long id, Long requestId, int amount) {
        Payment payment = mock(Payment.class);
        AsRequest asRequest = mock(AsRequest.class);
        given(asRequest.getId()).willReturn(requestId);
        given(payment.getId()).willReturn(id);
        given(payment.getAsRequest()).willReturn(asRequest);
        given(payment.getAmount()).willReturn(amount);
        return payment;
    }

    private AsAssignment completedAssignment(Agencies agency, User engineer, LocalDateTime assignedAt) {
        AsAssignment assignment = AsAssignment.create(mock(AsRequest.class), engineer, agency, AssignType.MANUAL);
        ReflectionTestUtils.setField(assignment, "status", "COMPLETED");
        ReflectionTestUtils.setField(assignment, "assignedAt", assignedAt);
        return assignment;
    }

    private User mockEngineer(Long id, String name) {
        User engineer = mock(User.class);
        given(engineer.getId()).willReturn(id);
        given(engineer.getName()).willReturn(name);
        return engineer;
    }

    // save() 호출 시 인자로 받은 Settlement 를 그대로 반환하도록 스텁 (createSettlement 흐름 재현용)
    private void stubSettlementSaveReturnsArgument() {
        given(settlementRepository.save(any(Settlement.class)))
                .willAnswer(invocation -> invocation.getArgument(0));
    }

    @Nested
    @DisplayName("generateForMonth — platform_settlements 집계")
    class GeneratePlatformSettlements {

        @Test
        @DisplayName("성공: 동일 대행사 결제 2건 → platform_settlement 1건에 합계·건수 집계")
        void success_sameAgency_aggregatedIntoOnePlatformSettlement() {
            // Given
            Agencies agency = mockAgency(1L, "케어플로우 서울대행사", 10.0);
            User engineer = mockEngineer(10L, "홍길동");

            Payment payment1 = mockPayment(100L, 1000L, 200000);
            Payment payment2 = mockPayment(101L, 1001L, 300000);

            given(paymentRepository.findSettlementTargets(any(), any()))
                    .willReturn(List.of(payment1, payment2));

            given(asAssignmentRepository.findByAsRequest_Id(1000L))
                    .willReturn(List.of(completedAssignment(agency, engineer, LocalDateTime.now())));
            given(asAssignmentRepository.findByAsRequest_Id(1001L))
                    .willReturn(List.of(completedAssignment(agency, engineer, LocalDateTime.now())));

            stubSettlementSaveReturnsArgument();

            given(platformSettlementRepository.findByAgency_IdAndSettlementYearAndSettlementMonth(1L, 2026, 6))
                    .willReturn(Optional.empty());

            // When
            SettlementGenerationService.Result result = settlementGenerationService.generateForMonth(TARGET_MONTH);

            // Then
            assertThat(result.created()).isEqualTo(2);
            assertThat(result.skipped()).isZero();
            assertThat(result.failed()).isZero();
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
        }

        @Test
        @DisplayName("성공: 서로 다른 대행사 결제 각 1건 → platform_settlement 각각 1건씩 생성")
        void success_differentAgencies_createSeparatePlatformSettlements() {
            // Given
            Agencies agencyA = mockAgency(1L, "A대행사", 10.0);
            Agencies agencyB = mockAgency(2L, "B대행사", 8.0);
            User engineer = mockEngineer(10L, "홍길동");

            Payment payment1 = mockPayment(100L, 1000L, 200000);
            Payment payment2 = mockPayment(101L, 1001L, 100000);

            given(paymentRepository.findSettlementTargets(any(), any()))
                    .willReturn(List.of(payment1, payment2));

            given(asAssignmentRepository.findByAsRequest_Id(1000L))
                    .willReturn(List.of(completedAssignment(agencyA, engineer, LocalDateTime.now())));
            given(asAssignmentRepository.findByAsRequest_Id(1001L))
                    .willReturn(List.of(completedAssignment(agencyB, engineer, LocalDateTime.now())));

            stubSettlementSaveReturnsArgument();

            given(platformSettlementRepository.findByAgency_IdAndSettlementYearAndSettlementMonth(anyLong(), eq(2026), eq(6)))
                    .willReturn(Optional.empty());

            // When
            SettlementGenerationService.Result result = settlementGenerationService.generateForMonth(TARGET_MONTH);

            // Then
            assertThat(result.created()).isEqualTo(2);
            assertThat(result.platformSettlementsCreated()).isEqualTo(2);
            verify(platformSettlementRepository, times(2)).save(any(PlatformSettlement.class));
        }

        @Test
        @DisplayName("성공: 정산 대상 결제 0건 — platform_settlement 생성 없음")
        void success_noTargets_noPlatformSettlementCreated() {
            // Given
            given(paymentRepository.findSettlementTargets(any(), any())).willReturn(List.of());

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
            Agencies agency = mockAgency(1L, "케어플로우 서울대행사", 10.0);
            User engineer = mockEngineer(10L, "홍길동");

            Payment payment = mockPayment(100L, 1000L, 200000);
            given(paymentRepository.findSettlementTargets(any(), any())).willReturn(List.of(payment));
            given(asAssignmentRepository.findByAsRequest_Id(1000L))
                    .willReturn(List.of(completedAssignment(agency, engineer, LocalDateTime.now())));
            stubSettlementSaveReturnsArgument();

            PlatformSettlement existing = PlatformSettlement.create(agency, 2026, 6, 100000, 10000, 1);
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
        }

        @Test
        @DisplayName("성공: 기존 platform_settlement이 이미 PAID면 누적하지 않고 스킵")
        void success_existingPaidPlatformSettlement_skipsWithoutMutating() {
            // Given
            Agencies agency = mockAgency(1L, "케어플로우 서울대행사", 10.0);
            User engineer = mockEngineer(10L, "홍길동");

            Payment payment = mockPayment(100L, 1000L, 200000);
            given(paymentRepository.findSettlementTargets(any(), any())).willReturn(List.of(payment));
            given(asAssignmentRepository.findByAsRequest_Id(1000L))
                    .willReturn(List.of(completedAssignment(agency, engineer, LocalDateTime.now())));
            stubSettlementSaveReturnsArgument();

            PlatformSettlement paidSettlement = PlatformSettlement.create(agency, 2026, 6, 100000, 10000, 1);
            paidSettlement.markPaid();
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
}
