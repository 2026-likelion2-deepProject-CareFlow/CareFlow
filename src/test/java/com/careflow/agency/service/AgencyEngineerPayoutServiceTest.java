package com.careflow.agency.service;

import com.careflow.agency.dto.response.AgencyEngineerPayoutListResponse;
import com.careflow.agency.entity.Agencies;
import com.careflow.auth.security.CustomUserDetails;
import com.careflow.bank_account.entity.BankAccount;
import com.careflow.bank_account.repository.BankAccountRepository;
import com.careflow.settlement.entity.EngineerPayout;
import com.careflow.settlement.repository.EngineerPayoutRepository;
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
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("AgencyEngineerPayoutService 단위 테스트")
class AgencyEngineerPayoutServiceTest {

    @InjectMocks
    private AgencyEngineerPayoutService agencyEngineerPayoutService;

    @Mock private EngineerPayoutRepository engineerPayoutRepository;
    @Mock private BankAccountRepository bankAccountRepository;

    private static final Long AGENCY_ID = 10L;
    private static final Long ENGINEER_ID = 20L;

    private CustomUserDetails agencyUser;
    private CustomUserDetails engineerUser;
    private Pageable pageable;

    @BeforeEach
    void setUp() {
        agencyUser = mock(CustomUserDetails.class);
        engineerUser = mock(CustomUserDetails.class);
        pageable = PageRequest.of(0, 10);

        given(agencyUser.getRole()).willReturn("AGENCY");
        given(agencyUser.getAgencyId()).willReturn(AGENCY_ID);
        given(engineerUser.getRole()).willReturn("ENGINEER");
    }

    private EngineerPayout buildEngineerPayout(Long id, Long agencyId, Long engineerId, String status) {
        EngineerPayout ep = mock(EngineerPayout.class);
        Agencies agency = mock(Agencies.class);
        User engineer = mock(User.class);
        given(agency.getId()).willReturn(agencyId);
        given(engineer.getId()).willReturn(engineerId);
        given(engineer.getName()).willReturn("홍길동");
        given(engineer.getPhone()).willReturn("010-1234-5678");
        given(ep.getId()).willReturn(id);
        given(ep.getAgency()).willReturn(agency);
        given(ep.getEngineer()).willReturn(engineer);
        given(ep.getNetAmountSum()).willReturn(400000);
        given(ep.getCaseCount()).willReturn(2);
        given(ep.getStatus()).willReturn(status);
        return ep;
    }

    @Nested
    @DisplayName("getEngineerPayouts — 기사별 지급 대상 목록 조회")
    class GetEngineerPayouts {

        @Test
        @DisplayName("성공: 소속 기사 목록 반환")
        void 정상조회_기사목록_반환() throws Exception {
            EngineerPayout ep = buildEngineerPayout(1L, AGENCY_ID, ENGINEER_ID, "PENDING");
            given(engineerPayoutRepository.findByAgency_IdAndPayoutYearAndPayoutMonth(AGENCY_ID, 2026, 6, pageable))
                    .willReturn(new PageImpl<>(List.of(ep), pageable, 1));

            BankAccount bankAccount = mock(BankAccount.class);
            given(bankAccount.getEngineerId()).willReturn(ENGINEER_ID);
            given(bankAccount.getPayMethod()).willReturn(BankAccount.PayMethod.BANK_TRANSFER);
            given(bankAccount.formatBankAccount()).willReturn("국민은행 123-456-789");
            given(bankAccountRepository.findByEngineerIdIn(List.of(ENGINEER_ID))).willReturn(List.of(bankAccount));

            AgencyEngineerPayoutListResponse response =
                    agencyEngineerPayoutService.getEngineerPayouts(agencyUser, 2026, 6, pageable);

            assertThat(response.content()).hasSize(1);
            assertThat(response.content().get(0).engineerName()).isEqualTo("홍길동");
            assertThat(response.content().get(0).payMethod()).isEqualTo("계좌이체");
            assertThat(response.content().get(0).bankAccount()).isEqualTo("국민은행 123-456-789");
        }

        @Test
        @DisplayName("성공: 계좌 미등록 기사는 payMethod/bankAccount가 null")
        void 계좌미등록기사는_payMethod_bankAccount_null() throws Exception {
            EngineerPayout ep = buildEngineerPayout(1L, AGENCY_ID, ENGINEER_ID, "PENDING");
            given(engineerPayoutRepository.findByAgency_IdAndPayoutYearAndPayoutMonth(AGENCY_ID, 2026, 6, pageable))
                    .willReturn(new PageImpl<>(List.of(ep), pageable, 1));
            given(bankAccountRepository.findByEngineerIdIn(anyList())).willReturn(List.of());

            AgencyEngineerPayoutListResponse response =
                    agencyEngineerPayoutService.getEngineerPayouts(agencyUser, 2026, 6, pageable);

            assertThat(response.content().get(0).payMethod()).isNull();
            assertThat(response.content().get(0).bankAccount()).isNull();
        }

        @Test
        @DisplayName("실패: AGENCY가 아닌 role → IllegalAccessException")
        void AGENCY아닌_role_예외발생() {
            assertThatThrownBy(() -> agencyEngineerPayoutService.getEngineerPayouts(engineerUser, 2026, 6, pageable))
                    .isInstanceOf(IllegalAccessException.class);
            verifyNoInteractions(engineerPayoutRepository, bankAccountRepository);
        }

        @Test
        @DisplayName("성공: 배치 없음 → 빈 배열 반환")
        void 배치없음_빈배열반환() throws Exception {
            given(engineerPayoutRepository.findByAgency_IdAndPayoutYearAndPayoutMonth(AGENCY_ID, 2026, 6, pageable))
                    .willReturn(new PageImpl<>(List.of(), pageable, 0));
            given(bankAccountRepository.findByEngineerIdIn(anyList())).willReturn(List.of());

            AgencyEngineerPayoutListResponse response =
                    agencyEngineerPayoutService.getEngineerPayouts(agencyUser, 2026, 6, pageable);

            assertThat(response.content()).isEmpty();
        }
    }

    @Nested
    @DisplayName("payEngineerPayout — 기사 지급 완료 처리")
    class PayEngineerPayout {

        @Test
        @DisplayName("성공: 정상 지급 완료 처리")
        void 정상_지급완료처리() throws Exception {
            EngineerPayout ep = buildEngineerPayout(1L, AGENCY_ID, ENGINEER_ID, "PENDING");
            given(engineerPayoutRepository.findById(1L)).willReturn(Optional.of(ep));

            agencyEngineerPayoutService.payEngineerPayout(agencyUser, 1L);

            verify(ep).markPaid();
        }

        @Test
        @DisplayName("성공: 이미 PAID인 배치 재호출해도 정상 처리(멱등)")
        void 이미PAID_재호출해도_정상처리() throws Exception {
            EngineerPayout ep = buildEngineerPayout(1L, AGENCY_ID, ENGINEER_ID, "PAID");
            given(engineerPayoutRepository.findById(1L)).willReturn(Optional.of(ep));

            assertThatCode(() -> agencyEngineerPayoutService.payEngineerPayout(agencyUser, 1L))
                    .doesNotThrowAnyException();
            verify(ep, never()).markPaid();
        }

        @Test
        @DisplayName("실패: 존재하지 않는 배치 → NoSuchElementException")
        void 존재하지않는배치_404() {
            given(engineerPayoutRepository.findById(999L)).willReturn(Optional.empty());

            assertThatThrownBy(() -> agencyEngineerPayoutService.payEngineerPayout(agencyUser, 999L))
                    .isInstanceOf(NoSuchElementException.class);
        }

        @Test
        @DisplayName("실패: 타 대행사 배치 → IllegalAccessException")
        void 타대행사배치_401() {
            EngineerPayout ep = buildEngineerPayout(1L, 999L, ENGINEER_ID, "PENDING");
            given(engineerPayoutRepository.findById(1L)).willReturn(Optional.of(ep));

            assertThatThrownBy(() -> agencyEngineerPayoutService.payEngineerPayout(agencyUser, 1L))
                    .isInstanceOf(IllegalAccessException.class);
            verify(ep, never()).markPaid();
        }

        @Test
        @DisplayName("실패: AGENCY가 아닌 role → IllegalAccessException")
        void AGENCY아닌_role_401() {
            assertThatThrownBy(() -> agencyEngineerPayoutService.payEngineerPayout(engineerUser, 1L))
                    .isInstanceOf(IllegalAccessException.class);
            verifyNoInteractions(engineerPayoutRepository);
        }
    }

    @Nested
    @DisplayName("updateStatus — 지급 건별 상태 변경 (보류/재검토)")
    class UpdateStatus {

        @Test
        @DisplayName("성공: PENDING → DISPUTED 전이")
        void 정상_DISPUTED로전이() throws Exception {
            EngineerPayout ep = buildEngineerPayout(1L, AGENCY_ID, ENGINEER_ID, "PENDING");
            given(engineerPayoutRepository.findById(1L)).willReturn(Optional.of(ep));

            agencyEngineerPayoutService.updateStatus(agencyUser, 1L, "DISPUTED");

            verify(ep).dispute();
        }

        @Test
        @DisplayName("성공: DISPUTED → PENDING 복귀")
        void 정상_PENDING으로복귀() throws Exception {
            EngineerPayout ep = buildEngineerPayout(1L, AGENCY_ID, ENGINEER_ID, "DISPUTED");
            given(engineerPayoutRepository.findById(1L)).willReturn(Optional.of(ep));

            agencyEngineerPayoutService.updateStatus(agencyUser, 1L, "PENDING");

            verify(ep).revertToPending();
        }

        @Test
        @DisplayName("실패: 이미 PAID인 배치는 IllegalStateException")
        void 이미PAID인배치_예외() {
            EngineerPayout ep = buildEngineerPayout(1L, AGENCY_ID, ENGINEER_ID, "PAID");
            given(engineerPayoutRepository.findById(1L)).willReturn(Optional.of(ep));

            assertThatThrownBy(() -> agencyEngineerPayoutService.updateStatus(agencyUser, 1L, "DISPUTED"))
                    .isInstanceOf(IllegalStateException.class);
        }

        @Test
        @DisplayName("실패: 유효하지 않은 상태값 → IllegalArgumentException")
        void 유효하지않은값_예외() {
            EngineerPayout ep = buildEngineerPayout(1L, AGENCY_ID, ENGINEER_ID, "PENDING");
            given(engineerPayoutRepository.findById(1L)).willReturn(Optional.of(ep));

            assertThatThrownBy(() -> agencyEngineerPayoutService.updateStatus(agencyUser, 1L, "PAID"))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("실패: 타 대행사 배치 → IllegalAccessException")
        void 타대행사배치_예외() {
            EngineerPayout ep = buildEngineerPayout(1L, 999L, ENGINEER_ID, "PENDING");
            given(engineerPayoutRepository.findById(1L)).willReturn(Optional.of(ep));

            assertThatThrownBy(() -> agencyEngineerPayoutService.updateStatus(agencyUser, 1L, "DISPUTED"))
                    .isInstanceOf(IllegalAccessException.class);
        }

        @Test
        @DisplayName("실패: AGENCY가 아닌 role → IllegalAccessException")
        void AGENCY아닌_role_예외() {
            assertThatThrownBy(() -> agencyEngineerPayoutService.updateStatus(engineerUser, 1L, "DISPUTED"))
                    .isInstanceOf(IllegalAccessException.class);
            verifyNoInteractions(engineerPayoutRepository);
        }

        @Test
        @DisplayName("실패: 존재하지 않는 배치 → NoSuchElementException")
        void 존재하지않는배치_예외() {
            given(engineerPayoutRepository.findById(999L)).willReturn(Optional.empty());

            assertThatThrownBy(() -> agencyEngineerPayoutService.updateStatus(agencyUser, 999L, "DISPUTED"))
                    .isInstanceOf(NoSuchElementException.class);
        }
    }
}
