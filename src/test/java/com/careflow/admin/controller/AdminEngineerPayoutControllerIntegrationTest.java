package com.careflow.admin.controller;

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
@DisplayName("AdminEngineerPayoutController 통합 테스트 (H2)")
class AdminEngineerPayoutControllerIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private JwtProvider jwtProvider;
    @Autowired private AgenciesRepository agenciesRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private EngineerPayoutRepository engineerPayoutRepository;

    private Agencies agency;
    private Agencies otherAgency;
    private User engineer;
    private User otherEngineer;

    private String adminToken;
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

        engineer = userRepository.save(User.builder()
                .email("engineer@agency.com").passwordHash("hashed")
                .name("김기사").phone("010-0000-0001")
                .role(Role.ENGINEER).agency(agency).build());
        otherEngineer = userRepository.save(User.builder()
                .email("other-engineer@agency.com").passwordHash("hashed")
                .name("타기사").phone("010-0000-0002")
                .role(Role.ENGINEER).agency(otherAgency).build());

        User admin = userRepository.save(User.builder()
                .email("admin@careflow.com").passwordHash("hashed")
                .name("관리자").phone("010-0000-0003")
                .role(Role.ADMIN).build());
        adminToken = jwtProvider.generateAccessToken(admin.getId(), admin.getEmail(), "ADMIN", null);

        User customer = userRepository.save(User.builder()
                .email("customer@test.com").passwordHash("hashed")
                .name("테스트고객").phone("010-0000-0004")
                .role(Role.CUSTOMER).build());
        customerToken = jwtProvider.generateAccessToken(customer.getId(), customer.getEmail(), "CUSTOMER", null);
    }

    @Test
    @DisplayName("성공: 전체 대행사의 배치가 조회된다")
    void 전체대행사_배치조회() throws Exception {
        engineerPayoutRepository.save(EngineerPayout.create(agency, engineer, 2026, 6, 100000, 1));
        engineerPayoutRepository.save(EngineerPayout.create(otherAgency, otherEngineer, 2026, 6, 200000, 1));

        mockMvc.perform(get("/api/admin/engineer-payouts?year=2026&month=6")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(2));
    }

    @Test
    @DisplayName("성공: status=DISPUTED 필터링 시 해당 상태만 조회된다")
    void DISPUTED필터_조회() throws Exception {
        EngineerPayout normal = engineerPayoutRepository.save(
                EngineerPayout.create(agency, engineer, 2026, 6, 100000, 1));
        EngineerPayout disputed = engineerPayoutRepository.save(
                EngineerPayout.create(otherAgency, otherEngineer, 2026, 6, 200000, 1));
        disputed.dispute();
        engineerPayoutRepository.save(disputed);

        mockMvc.perform(get("/api/admin/engineer-payouts?year=2026&month=6&status=DISPUTED")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].status").value("DISPUTED"));
    }

    @Test
    @DisplayName("실패: CUSTOMER 권한 → 403 Forbidden (SecurityConfig의 /api/admin/** hasRole(ADMIN)에 의해 차단)")
    void CUSTOMER권한_403() throws Exception {
        mockMvc.perform(get("/api/admin/engineer-payouts?year=2026&month=6")
                        .header("Authorization", "Bearer " + customerToken))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("실패: 잘못된 status 값 → 400 Bad Request")
    void 잘못된status_400() throws Exception {
        mockMvc.perform(get("/api/admin/engineer-payouts?year=2026&month=6&status=INVALID")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isBadRequest());
    }

    @Nested
    @DisplayName("PATCH /api/admin/engineer-payouts/{id}/status — 건별 지급 상태 변경(기사 이의제기 조정용)")
    class UpdateStatus {

        @Test
        @DisplayName("성공: DISPUTED 전이 → 204, DB 반영 확인")
        void DISPUTED_전이_204() throws Exception {
            EngineerPayout payout = engineerPayoutRepository.save(
                    EngineerPayout.create(agency, engineer, 2026, 6, 100000, 1));

            mockMvc.perform(patch("/api/admin/engineer-payouts/" + payout.getId() + "/status")
                            .header("Authorization", "Bearer " + adminToken)
                            .contentType("application/json")
                            .content("{\"status\":\"DISPUTED\"}"))
                    .andExpect(status().isNoContent());

            EngineerPayout reloaded = engineerPayoutRepository.findById(payout.getId()).orElseThrow();
            assertThat(reloaded.getStatus()).isEqualTo("DISPUTED");
        }

        @Test
        @DisplayName("성공: DISPUTED → PENDING 재검토 → 204, DB 반영 확인")
        void PENDING_재검토_204() throws Exception {
            EngineerPayout payout = engineerPayoutRepository.save(
                    EngineerPayout.create(agency, engineer, 2026, 6, 100000, 1));
            payout.dispute();
            engineerPayoutRepository.save(payout);

            mockMvc.perform(patch("/api/admin/engineer-payouts/" + payout.getId() + "/status")
                            .header("Authorization", "Bearer " + adminToken)
                            .contentType("application/json")
                            .content("{\"status\":\"PENDING\"}"))
                    .andExpect(status().isNoContent());

            EngineerPayout reloaded = engineerPayoutRepository.findById(payout.getId()).orElseThrow();
            assertThat(reloaded.getStatus()).isEqualTo("PENDING");
        }

        @Test
        @DisplayName("실패: CUSTOMER 권한 → 403 Forbidden")
        void CUSTOMER권한_403() throws Exception {
            EngineerPayout payout = engineerPayoutRepository.save(
                    EngineerPayout.create(agency, engineer, 2026, 6, 100000, 1));

            mockMvc.perform(patch("/api/admin/engineer-payouts/" + payout.getId() + "/status")
                            .header("Authorization", "Bearer " + customerToken)
                            .contentType("application/json")
                            .content("{\"status\":\"DISPUTED\"}"))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("실패: 존재하지 않는 배치 → 404 Not Found")
        void 존재하지않는배치_404() throws Exception {
            mockMvc.perform(patch("/api/admin/engineer-payouts/999999/status")
                            .header("Authorization", "Bearer " + adminToken)
                            .contentType("application/json")
                            .content("{\"status\":\"DISPUTED\"}"))
                    .andExpect(status().isNotFound());
        }
    }
}
