package com.careflow.agency.controller;

import com.careflow.agency.entity.Agencies;
import com.careflow.agency.repository.AgenciesRepository;
import com.careflow.auth.security.JwtProvider;
import com.careflow.common.enums.AgencyStatus;
import com.careflow.common.enums.Role;
import com.careflow.settlement.entity.EngineerPayout;
import com.careflow.settlement.repository.EngineerPayoutRepository;
import com.careflow.user.entity.User;
import com.careflow.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("local")
@Sql(scripts = "/cleanup.sql", executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
@DisplayName("AgencyEngineerPayoutController 통합 테스트 (H2)")
class AgencyEngineerPayoutControllerIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private JwtProvider jwtProvider;
    @Autowired private AgenciesRepository agenciesRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private EngineerPayoutRepository engineerPayoutRepository;

    private Agencies agency;
    private Agencies otherAgency;
    private User engineer;
    private User otherEngineer;

    private String agencyToken;
    private String customerToken;

    @BeforeEach
    void setUp() {
        agency = agenciesRepository.save(Agencies.builder()
                .agencyName("테스트대행사").businessNumber("111-11-11111")
                .agencyAddress("서울특별시 강남구").agencyFeeRate(0.1)
                .approvalStatus(AgencyStatus.APPROVED).build());
        otherAgency = agenciesRepository.save(Agencies.builder()
                .agencyName("다른대행사").businessNumber("222-22-22222")
                .agencyAddress("서울특별시 서초구").agencyFeeRate(0.1)
                .approvalStatus(AgencyStatus.APPROVED).build());

        User agencyManager = userRepository.save(User.builder()
                .email("manager@agency.com").passwordHash("hashed")
                .name("대행사관리자").phone("010-0000-0001")
                .role(Role.AGENCY).agency(agency).build());
        agencyToken = jwtProvider.generateAccessToken(
                agencyManager.getId(), agencyManager.getEmail(), "AGENCY", agency.getId());

        engineer = userRepository.save(User.builder()
                .email("engineer@agency.com").passwordHash("hashed")
                .name("김기사").phone("010-0000-0002")
                .role(Role.ENGINEER).agency(agency).build());

        otherEngineer = userRepository.save(User.builder()
                .email("other-engineer@agency.com").passwordHash("hashed")
                .name("타기사").phone("010-0000-0003")
                .role(Role.ENGINEER).agency(otherAgency).build());

        User customer = userRepository.save(User.builder()
                .email("customer@test.com").passwordHash("hashed")
                .name("테스트고객").phone("010-0000-0004")
                .role(Role.CUSTOMER).build());
        customerToken = jwtProvider.generateAccessToken(customer.getId(), customer.getEmail(), "CUSTOMER", null);
    }

    private EngineerPayout savePayout(Agencies targetAgency, User targetEngineer, int year, int month) {
        return engineerPayoutRepository.save(
                EngineerPayout.create(targetAgency, targetEngineer, year, month, 400000, 2));
    }

    @Nested
    @DisplayName("GET /api/agency/engineer-payouts — 기사별 지급 대상 목록 조회")
    class GetEngineerPayouts {

        @Test
        @DisplayName("성공: 소속 기사 배치가 목록에 포함된다")
        void 소속기사_배치목록_정상조회() throws Exception {
            savePayout(agency, engineer, 2026, 6);

            mockMvc.perform(get("/api/agency/engineer-payouts?year=2026&month=6")
                            .header("Authorization", "Bearer " + agencyToken))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content.length()").value(1))
                    .andExpect(jsonPath("$.content[0].engineerName").value("김기사"))
                    .andExpect(jsonPath("$.content[0].netAmountSum").value(400000));
        }

        @Test
        @DisplayName("성공: 타 대행사 소속 기사 배치는 결과에서 제외된다")
        void 타대행사_배치_제외() throws Exception {
            savePayout(otherAgency, otherEngineer, 2026, 6);

            mockMvc.perform(get("/api/agency/engineer-payouts?year=2026&month=6")
                            .header("Authorization", "Bearer " + agencyToken))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content.length()").value(0));
        }

        @Test
        @DisplayName("성공: 다른 달의 배치는 결과에서 제외된다")
        void 타월_배치_제외() throws Exception {
            savePayout(agency, engineer, 2026, 5);

            mockMvc.perform(get("/api/agency/engineer-payouts?year=2026&month=6")
                            .header("Authorization", "Bearer " + agencyToken))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content.length()").value(0));
        }

        @Test
        @DisplayName("실패: CUSTOMER 권한 → 401 Unauthorized")
        void CUSTOMER권한_401() throws Exception {
            mockMvc.perform(get("/api/agency/engineer-payouts?year=2026&month=6")
                            .header("Authorization", "Bearer " + customerToken))
                    .andExpect(status().isUnauthorized());
        }
    }

    @Nested
    @DisplayName("PATCH /api/agency/engineer-payouts/{id}/pay — 기사 지급 완료 처리")
    class PayEngineerPayout {

        @Test
        @DisplayName("성공: 정상 지급 완료 처리 → 204, DB 반영 확인")
        void 정상_지급완료_204() throws Exception {
            EngineerPayout payout = savePayout(agency, engineer, 2026, 6);

            mockMvc.perform(patch("/api/agency/engineer-payouts/" + payout.getId() + "/pay")
                            .header("Authorization", "Bearer " + agencyToken))
                    .andExpect(status().isNoContent());

            EngineerPayout reloaded = engineerPayoutRepository.findById(payout.getId()).orElseThrow();
            assertThat(reloaded.getStatus()).isEqualTo("PAID");
            assertThat(reloaded.getPaidAt()).isNotNull();
        }

        @Test
        @DisplayName("실패: 타 대행사 배치 처리 시도 → 401, DB 값 불변")
        void 타대행사배치_401() throws Exception {
            EngineerPayout payout = savePayout(otherAgency, otherEngineer, 2026, 6);

            mockMvc.perform(patch("/api/agency/engineer-payouts/" + payout.getId() + "/pay")
                            .header("Authorization", "Bearer " + agencyToken))
                    .andExpect(status().isUnauthorized());

            EngineerPayout unchanged = engineerPayoutRepository.findById(payout.getId()).orElseThrow();
            assertThat(unchanged.getStatus()).isEqualTo("PENDING");
        }

        @Test
        @DisplayName("실패: 존재하지 않는 배치 → 404 Not Found")
        void 존재하지않는배치_404() throws Exception {
            mockMvc.perform(patch("/api/agency/engineer-payouts/999999/pay")
                            .header("Authorization", "Bearer " + agencyToken))
                    .andExpect(status().isNotFound());
        }

        @Test
        @DisplayName("실패: DISPUTED(보류 중) 배치 지급 시도 → 409(또는 매핑된 상태), DB 값 불변")
        void 보류중인배치_지급시도시_상태불변() throws Exception {
            EngineerPayout payout = savePayout(agency, engineer, 2026, 6);
            payout.dispute();
            engineerPayoutRepository.save(payout);

            mockMvc.perform(patch("/api/agency/engineer-payouts/" + payout.getId() + "/pay")
                            .header("Authorization", "Bearer " + agencyToken))
                    .andExpect(status().is4xxClientError());

            EngineerPayout unchanged = engineerPayoutRepository.findById(payout.getId()).orElseThrow();
            assertThat(unchanged.getStatus()).isEqualTo("DISPUTED");
            assertThat(unchanged.getPaidAt()).isNull();
        }
    }

    @Nested
    @DisplayName("PATCH /api/agency/engineer-payouts/{id}/status — 지급 건별 상태 변경(보류/재검토)")
    class UpdateStatus {

        @Test
        @DisplayName("성공: DISPUTED 전이 → 204, DB 반영 확인")
        void DISPUTED_전이_204() throws Exception {
            EngineerPayout payout = savePayout(agency, engineer, 2026, 6);

            mockMvc.perform(patch("/api/agency/engineer-payouts/" + payout.getId() + "/status")
                            .header("Authorization", "Bearer " + agencyToken)
                            .contentType("application/json")
                            .content("{\"status\":\"DISPUTED\"}"))
                    .andExpect(status().isNoContent());

            EngineerPayout reloaded = engineerPayoutRepository.findById(payout.getId()).orElseThrow();
            assertThat(reloaded.getStatus()).isEqualTo("DISPUTED");
        }

        @Test
        @DisplayName("실패: 타 대행사 배치 → 401, DB 값 불변")
        void 타대행사배치_401() throws Exception {
            EngineerPayout payout = savePayout(otherAgency, otherEngineer, 2026, 6);

            mockMvc.perform(patch("/api/agency/engineer-payouts/" + payout.getId() + "/status")
                            .header("Authorization", "Bearer " + agencyToken)
                            .contentType("application/json")
                            .content("{\"status\":\"DISPUTED\"}"))
                    .andExpect(status().isUnauthorized());

            EngineerPayout unchanged = engineerPayoutRepository.findById(payout.getId()).orElseThrow();
            assertThat(unchanged.getStatus()).isEqualTo("PENDING");
        }

        @Test
        @DisplayName("실패: status=PAID 요청 → 400 (검증 단계에서 거부)")
        void PAID요청_400() throws Exception {
            EngineerPayout payout = savePayout(agency, engineer, 2026, 6);

            mockMvc.perform(patch("/api/agency/engineer-payouts/" + payout.getId() + "/status")
                            .header("Authorization", "Bearer " + agencyToken)
                            .contentType("application/json")
                            .content("{\"status\":\"PAID\"}"))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("실패: 존재하지 않는 배치 → 404 Not Found")
        void 존재하지않는배치_404() throws Exception {
            mockMvc.perform(patch("/api/agency/engineer-payouts/999999/status")
                            .header("Authorization", "Bearer " + agencyToken)
                            .contentType("application/json")
                            .content("{\"status\":\"DISPUTED\"}"))
                    .andExpect(status().isNotFound());
        }
    }
}
