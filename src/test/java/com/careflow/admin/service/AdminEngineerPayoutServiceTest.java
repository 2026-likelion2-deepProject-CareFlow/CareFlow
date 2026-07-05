package com.careflow.admin.service;

import com.careflow.admin.dto.response.AdminEngineerPayoutListResponse;
import com.careflow.agency.entity.Agencies;
import com.careflow.auth.security.CustomUserDetails;
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

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("AdminEngineerPayoutService 단위 테스트")
class AdminEngineerPayoutServiceTest {

    @InjectMocks
    private AdminEngineerPayoutService adminEngineerPayoutService;

    @Mock private EngineerPayoutRepository engineerPayoutRepository;

    private CustomUserDetails adminUser;
    private CustomUserDetails agencyUser;

    @BeforeEach
    void setUp() {
        adminUser = mock(CustomUserDetails.class);
        agencyUser = mock(CustomUserDetails.class);
        given(adminUser.getRole()).willReturn("ADMIN");
        given(agencyUser.getRole()).willReturn("AGENCY");
    }

    private EngineerPayout buildEngineerPayout(String status) {
        EngineerPayout ep = mock(EngineerPayout.class);
        Agencies agency = mock(Agencies.class);
        User engineer = mock(User.class);
        given(agency.getId()).willReturn(1L);
        given(agency.getAgencyName()).willReturn("케어플로우 서울대행사");
        given(engineer.getId()).willReturn(10L);
        given(engineer.getName()).willReturn("홍길동");
        given(ep.getId()).willReturn(1L);
        given(ep.getAgency()).willReturn(agency);
        given(ep.getEngineer()).willReturn(engineer);
        given(ep.getNetAmountSum()).willReturn(100000);
        given(ep.getCaseCount()).willReturn(1);
        given(ep.getStatus()).willReturn(status);
        return ep;
    }

    @Nested
    @DisplayName("성공 케이스")
    class Success {

        @Test
        @DisplayName("정상 조회: 전체 배치가 반환된다")
        void 정상조회_전체배치_반환() throws Exception {
            EngineerPayout ep1 = buildEngineerPayout("PENDING");
            EngineerPayout ep2 = buildEngineerPayout("PAID");
            given(engineerPayoutRepository.findAllByPeriodAndOptionalStatus(2026, 6, null))
                    .willReturn(List.of(ep1, ep2));

            AdminEngineerPayoutListResponse response =
                    adminEngineerPayoutService.getEngineerPayouts(adminUser, 2026, 6, null);

            assertThat(response.items()).hasSize(2);
        }

        @Test
        @DisplayName("status 필터: DISPUTED만 조회")
        void status필터_DISPUTED만조회() throws Exception {
            EngineerPayout disputed = buildEngineerPayout("DISPUTED");
            given(engineerPayoutRepository.findAllByPeriodAndOptionalStatus(2026, 6, "DISPUTED"))
                    .willReturn(List.of(disputed));

            AdminEngineerPayoutListResponse response =
                    adminEngineerPayoutService.getEngineerPayouts(adminUser, 2026, 6, "DISPUTED");

            assertThat(response.items()).hasSize(1);
            assertThat(response.items().get(0).status()).isEqualTo("DISPUTED");
        }
    }

    @Nested
    @DisplayName("실패 케이스")
    class Failure {

        @Test
        @DisplayName("ADMIN이 아닌 role → IllegalAccessException")
        void ADMIN아닌_role_예외발생() {
            assertThatThrownBy(() -> adminEngineerPayoutService.getEngineerPayouts(agencyUser, 2026, 6, null))
                    .isInstanceOf(IllegalAccessException.class);
            verifyNoInteractions(engineerPayoutRepository);
        }

        @Test
        @DisplayName("잘못된 status 값 → IllegalArgumentException")
        void 잘못된status값_예외발생() {
            assertThatThrownBy(() -> adminEngineerPayoutService.getEngineerPayouts(adminUser, 2026, 6, "INVALID"))
                    .isInstanceOf(IllegalArgumentException.class);
            verifyNoInteractions(engineerPayoutRepository);
        }

        @Test
        @DisplayName("month 범위 초과 → IllegalArgumentException")
        void month범위초과_예외발생() {
            assertThatThrownBy(() -> adminEngineerPayoutService.getEngineerPayouts(adminUser, 2026, 13, null))
                    .isInstanceOf(IllegalArgumentException.class);
            verifyNoInteractions(engineerPayoutRepository);
        }
    }

    @Nested
    @DisplayName("updateStatus — 건별 지급 상태 변경 (기사 이의제기 조정용)")
    class UpdateStatus {

        @Test
        @DisplayName("성공: PENDING → DISPUTED 전이")
        void 정상_DISPUTED로전이() throws Exception {
            EngineerPayout ep = buildEngineerPayout("PENDING");
            given(engineerPayoutRepository.findById(1L)).willReturn(Optional.of(ep));

            adminEngineerPayoutService.updateStatus(adminUser, 1L, "DISPUTED");

            verify(ep).dispute();
        }

        @Test
        @DisplayName("성공: DISPUTED → PENDING 복귀")
        void 정상_PENDING으로복귀() throws Exception {
            EngineerPayout ep = buildEngineerPayout("DISPUTED");
            given(engineerPayoutRepository.findById(1L)).willReturn(Optional.of(ep));

            adminEngineerPayoutService.updateStatus(adminUser, 1L, "PENDING");

            verify(ep).revertToPending();
        }

        @Test
        @DisplayName("실패: 이미 PAID인 배치는 IllegalStateException")
        void 이미PAID인배치_예외() {
            EngineerPayout ep = buildEngineerPayout("PAID");
            given(engineerPayoutRepository.findById(1L)).willReturn(Optional.of(ep));

            assertThatThrownBy(() -> adminEngineerPayoutService.updateStatus(adminUser, 1L, "DISPUTED"))
                    .isInstanceOf(IllegalStateException.class);
        }

        @Test
        @DisplayName("실패: 유효하지 않은 상태값 → IllegalArgumentException")
        void 유효하지않은값_예외() {
            EngineerPayout ep = buildEngineerPayout("PENDING");
            given(engineerPayoutRepository.findById(1L)).willReturn(Optional.of(ep));

            assertThatThrownBy(() -> adminEngineerPayoutService.updateStatus(adminUser, 1L, "PAID"))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("실패: ADMIN이 아닌 role → IllegalAccessException")
        void ADMIN아닌_role_예외() {
            assertThatThrownBy(() -> adminEngineerPayoutService.updateStatus(agencyUser, 1L, "DISPUTED"))
                    .isInstanceOf(IllegalAccessException.class);
            verifyNoInteractions(engineerPayoutRepository);
        }

        @Test
        @DisplayName("실패: 존재하지 않는 배치 → NoSuchElementException")
        void 존재하지않는배치_예외() {
            given(engineerPayoutRepository.findById(999L)).willReturn(Optional.empty());

            assertThatThrownBy(() -> adminEngineerPayoutService.updateStatus(adminUser, 999L, "DISPUTED"))
                    .isInstanceOf(java.util.NoSuchElementException.class);
        }
    }
}
