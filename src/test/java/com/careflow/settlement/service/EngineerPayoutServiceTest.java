package com.careflow.settlement.service;

import com.careflow.agency.entity.Agencies;
import com.careflow.auth.security.CustomUserDetails;
import com.careflow.settlement.dto.EngineerPayoutPageResponse;
import com.careflow.settlement.entity.EngineerPayout;
import com.careflow.settlement.repository.EngineerPayoutRepository;
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
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("EngineerPayoutService 단위 테스트")
class EngineerPayoutServiceTest {

    @InjectMocks
    private EngineerPayoutService engineerPayoutService;

    @Mock private EngineerPayoutRepository engineerPayoutRepository;

    private static final Long ENGINEER_ID = 20L;

    private CustomUserDetails engineerUser;
    private CustomUserDetails agencyUser;
    private Pageable pageable;

    @BeforeEach
    void setUp() {
        engineerUser = mock(CustomUserDetails.class);
        agencyUser = mock(CustomUserDetails.class);
        pageable = PageRequest.of(0, 10);

        given(engineerUser.getRole()).willReturn("ENGINEER");
        given(engineerUser.getUserId()).willReturn(ENGINEER_ID);
        given(agencyUser.getRole()).willReturn("AGENCY");
    }

    private EngineerPayout buildEngineerPayout() {
        EngineerPayout ep = mock(EngineerPayout.class);
        Agencies agency = mock(Agencies.class);
        given(agency.getAgencyName()).willReturn("케어플로우 서울대행사");
        given(ep.getId()).willReturn(1L);
        given(ep.getAgency()).willReturn(agency);
        given(ep.getPayoutYear()).willReturn(2026);
        given(ep.getPayoutMonth()).willReturn(6);
        given(ep.getNetAmountSum()).willReturn(400000);
        given(ep.getCaseCount()).willReturn(2);
        given(ep.getStatus()).willReturn("PENDING");
        return ep;
    }

    @Nested
    @DisplayName("성공 케이스")
    class Success {

        @Test
        @DisplayName("정상 조회: 본인 배치가 반환된다")
        void 정상조회_본인배치_반환() throws Exception {
            EngineerPayout ep = buildEngineerPayout();
            given(engineerPayoutRepository.findByEngineer_IdOrderByPayoutYearDescPayoutMonthDesc(ENGINEER_ID, pageable))
                    .willReturn(new PageImpl<>(List.of(ep), pageable, 1));

            Page<EngineerPayoutPageResponse> result = engineerPayoutService.getMyPayouts(engineerUser, pageable);

            assertThat(result.getContent()).hasSize(1);
            assertThat(result.getContent().get(0).agencyName()).isEqualTo("케어플로우 서울대행사");
        }

        @Test
        @DisplayName("배치 없음: 빈 배열 반환")
        void 배치없음_빈배열반환() throws Exception {
            given(engineerPayoutRepository.findByEngineer_IdOrderByPayoutYearDescPayoutMonthDesc(any(), any()))
                    .willReturn(new PageImpl<>(List.of(), pageable, 0));

            Page<EngineerPayoutPageResponse> result = engineerPayoutService.getMyPayouts(engineerUser, pageable);

            assertThat(result.getContent()).isEmpty();
        }
    }

    @Nested
    @DisplayName("실패 케이스")
    class Failure {

        @Test
        @DisplayName("ENGINEER가 아닌 role → IllegalAccessException")
        void ENGINEER아닌_role_예외발생() {
            assertThatThrownBy(() -> engineerPayoutService.getMyPayouts(agencyUser, pageable))
                    .isInstanceOf(IllegalAccessException.class);
            verifyNoInteractions(engineerPayoutRepository);
        }
    }
}
