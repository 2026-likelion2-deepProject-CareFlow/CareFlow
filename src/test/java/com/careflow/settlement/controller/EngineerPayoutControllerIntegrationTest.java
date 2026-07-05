package com.careflow.settlement.controller;

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
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("local")
@Sql(scripts = "/cleanup.sql", executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
@DisplayName("EngineerPayoutController 통합 테스트 (H2)")
class EngineerPayoutControllerIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private JwtProvider jwtProvider;
    @Autowired private AgenciesRepository agenciesRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private EngineerPayoutRepository engineerPayoutRepository;

    private Agencies agency;
    private User engineer;
    private User otherEngineer;

    private String engineerToken;
    private String customerToken;

    @BeforeEach
    void setUp() {
        agency = agenciesRepository.save(Agencies.builder()
                .agencyName("테스트대행사").businessNumber("111-11-11111")
                .agencyAddress("서울특별시 강남구").agencyFeeRate(0.1)
                .approvalStatus(AgencyStatus.APPROVED).build());

        engineer = userRepository.save(User.builder()
                .email("engineer@agency.com").passwordHash("hashed")
                .name("김기사").phone("010-0000-0001")
                .role(Role.ENGINEER).agency(agency).build());
        engineerToken = jwtProvider.generateAccessToken(engineer.getId(), engineer.getEmail(), "ENGINEER", agency.getId());

        otherEngineer = userRepository.save(User.builder()
                .email("other-engineer@agency.com").passwordHash("hashed")
                .name("타기사").phone("010-0000-0002")
                .role(Role.ENGINEER).agency(agency).build());

        User customer = userRepository.save(User.builder()
                .email("customer@test.com").passwordHash("hashed")
                .name("테스트고객").phone("010-0000-0003")
                .role(Role.CUSTOMER).build());
        customerToken = jwtProvider.generateAccessToken(customer.getId(), customer.getEmail(), "CUSTOMER", null);
    }

    @Test
    @DisplayName("성공: 본인 배치 목록이 최신월 순으로 반환된다")
    void 본인배치_목록조회() throws Exception {
        engineerPayoutRepository.save(EngineerPayout.create(agency, engineer, 2026, 5, 100000, 1));
        engineerPayoutRepository.save(EngineerPayout.create(agency, engineer, 2026, 6, 200000, 1));

        mockMvc.perform(get("/api/engineer/payouts")
                        .header("Authorization", "Bearer " + engineerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(2))
                .andExpect(jsonPath("$.content[0].payoutMonth").value(6))
                .andExpect(jsonPath("$.content[1].payoutMonth").value(5));
    }

    @Test
    @DisplayName("성공: 다른 기사의 배치는 결과에서 제외된다")
    void 타기사배치_제외() throws Exception {
        engineerPayoutRepository.save(EngineerPayout.create(agency, otherEngineer, 2026, 6, 200000, 1));

        mockMvc.perform(get("/api/engineer/payouts")
                        .header("Authorization", "Bearer " + engineerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(0));
    }

    @Test
    @DisplayName("실패: CUSTOMER 권한 → 403 Forbidden (@PreAuthorize에 의해 컨트롤러 진입 전 차단)")
    void CUSTOMER권한_403() throws Exception {
        mockMvc.perform(get("/api/engineer/payouts")
                        .header("Authorization", "Bearer " + customerToken))
                .andExpect(status().isForbidden());
    }
}
