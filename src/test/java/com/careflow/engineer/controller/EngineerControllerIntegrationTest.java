package com.careflow.engineer.controller;

import com.careflow.account_requests.entity.AccountRequests;
import com.careflow.account_requests.repository.AccountRequestsRepository;
import com.careflow.agency.entity.Agencies;
import com.careflow.agency.repository.AgenciesRepository;
import com.careflow.common.enums.AccountRequestsRole;
import com.careflow.common.enums.AgencyStatus;
import com.careflow.engineer.dto.EngineerAccountRequest;
import com.careflow.region.entity.Regions;
import com.careflow.region.repository.RegionRepository;
import com.careflow.user.entity.User;
import com.careflow.user.repository.UserRepository;
import com.careflow.common.enums.Role;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("local")
@Sql(scripts = "/cleanup.sql", executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
@DisplayName("EngineerController 통합 테스트 (H2)")
class EngineerControllerIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private AgenciesRepository agenciesRepository;
    @Autowired private AccountRequestsRepository accountRequestsRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private RegionRepository regionRepository;

    private Regions region;
    private Agencies approvedAgency;

    @BeforeEach
    void setUp() {
        region = regionRepository.save(Regions.create("서울특별시 서초구", null, 1, 0));

        User superUser = userRepository.save(User.builder()
                .email("super@agency.com").passwordHash("hashed")
                .name("슈퍼계정").phone("010-0000-0001").role(Role.AGENCY).build());

        approvedAgency = agenciesRepository.save(Agencies.builder()
                .agencyName("승인된대행사").businessNumber("BIZ-APPROVED")
                .agencyAddress("서울").agencyFeeRate(5.0)
                .approvalStatus(AgencyStatus.APPROVED)
                .representativeId(superUser).build());
    }

    private EngineerAccountRequest request(String email, String businessNumber) {
        return new EngineerAccountRequest(
                "홍길동", email, "pass1234", "010-1234-5678",
                "서울특별시 서초구", "상세주소 101호", businessNumber
        );
    }

    // ─────────────────────────────────────────────
    //  POST /api/engineer/signup
    // ─────────────────────────────────────────────

    @Test
    @DisplayName("성공: APPROVED 대행사에 계정 요청 → account_requests 에 ENGINEER 역할 데이터 저장")
    void signup_approvedAgency_dbUpdated() throws Exception {
        mockMvc.perform(post("/api/engineer/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request("engineer@test.com", "BIZ-APPROVED"))))
                .andExpect(status().isCreated());

        AccountRequests saved = accountRequestsRepository.findAll().stream()
                .filter(r -> "engineer@test.com".equals(r.getEmail()))
                .findFirst().orElseThrow();

        assertThat(saved.getRequestsRole()).isEqualTo(AccountRequestsRole.ENGINEER);
        assertThat(saved.getAgency().getId()).isEqualTo(approvedAgency.getId());
    }

    @Test
    @DisplayName("실패: 존재하지 않는 사업자 등록번호 — 404 Not Found")
    void signup_unknownBizNumber_404() throws Exception {
        mockMvc.perform(post("/api/engineer/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request("engineer@test.com", "BIZ-UNKNOWN"))))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("실패: PENDING 상태 대행사에 요청 — 403 Forbidden")
    void signup_pendingAgency_403() throws Exception {
        agenciesRepository.save(Agencies.builder()
                .agencyName("대기중대행사").businessNumber("BIZ-PENDING")
                .agencyAddress("서울").agencyFeeRate(5.0)
                .approvalStatus(AgencyStatus.PENDING).build());

        mockMvc.perform(post("/api/engineer/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request("engineer2@test.com", "BIZ-PENDING"))))
                .andExpect(status().isForbidden());

        assertThat(accountRequestsRepository.findAll()).isEmpty();
    }

    @Test
    @DisplayName("실패: REJECTED 상태 대행사에 요청 — 403 Forbidden")
    void signup_rejectedAgency_403() throws Exception {
        agenciesRepository.save(Agencies.builder()
                .agencyName("거부된대행사").businessNumber("BIZ-REJECTED")
                .agencyAddress("서울").agencyFeeRate(5.0)
                .approvalStatus(AgencyStatus.REJECTED).build());

        mockMvc.perform(post("/api/engineer/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request("engineer3@test.com", "BIZ-REJECTED"))))
                .andExpect(status().isForbidden());

        assertThat(accountRequestsRepository.findAll()).isEmpty();
    }

    @Test
    @DisplayName("실패: users 테이블에 이미 존재하는 이메일 — 400 Bad Request")
    void signup_emailExistsInUsers_400() throws Exception {
        userRepository.save(User.builder()
                .email("duplicate@test.com").passwordHash("hashed")
                .name("기존회원").role(Role.ENGINEER).build());

        mockMvc.perform(post("/api/engineer/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request("duplicate@test.com", "BIZ-APPROVED"))))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("실패: account_requests 테이블에 이미 존재하는 이메일 — 400 Bad Request")
    void signup_emailExistsInAccountRequests_400() throws Exception {
        // 먼저 동일 이메일로 요청 적재
        accountRequestsRepository.save(AccountRequests.create(
                approvedAgency, "dup-req@test.com", "hashed", "홍길동",
                "010-1111-2222", AccountRequestsRole.ENGINEER, "상세주소", region));

        mockMvc.perform(post("/api/engineer/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request("dup-req@test.com", "BIZ-APPROVED"))))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("실패: 잘못된 지역명 — 404 Not Found")
    void signup_unknownRegion_404() throws Exception {
        EngineerAccountRequest badRegion = new EngineerAccountRequest(
                "홍길동", "engineer@test.com", "pass1234", "010-1234-5678",
                "존재하지않는지역", "상세주소 101호", "BIZ-APPROVED"
        );

        mockMvc.perform(post("/api/engineer/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(badRegion)))
                .andExpect(status().isNotFound());
    }
}
