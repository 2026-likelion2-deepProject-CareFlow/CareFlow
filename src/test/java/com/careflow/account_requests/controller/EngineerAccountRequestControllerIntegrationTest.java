package com.careflow.account_requests.controller;

import com.careflow.account_requests.dto.AccountRequestReject;
import com.careflow.account_requests.entity.AccountRequests;
import com.careflow.account_requests.repository.AccountRequestsRepository;
import com.careflow.agency.entity.Agencies;
import com.careflow.agency.repository.AgenciesRepository;
import com.careflow.auth.security.JwtProvider;
import com.careflow.common.enums.AccountRequestsRole;
import com.careflow.common.enums.AccountRequestsStatus;
import com.careflow.common.enums.AgencyStatus;
import com.careflow.common.enums.Role;
import com.careflow.region.entity.Regions;
import com.careflow.region.repository.RegionRepository;
import com.careflow.user.entity.User;
import com.careflow.user.repository.UserRepository;
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
@DisplayName("EngineerAccountRequestController 통합 테스트 (H2)")
class EngineerAccountRequestControllerIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private AccountRequestsRepository accountRequestsRepository;
    @Autowired private AgenciesRepository agenciesRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private RegionRepository regionRepository;
    @Autowired private JwtProvider jwtProvider;

    private Regions region;
    private User superUser;
    private Agencies approvedAgency;

    @BeforeEach
    void setUp() {
        region = regionRepository.save(Regions.create("서울특별시 서초구", null, 1, 0));

        superUser = userRepository.save(User.builder()
                .email("super@agency.com").passwordHash("hashed")
                .name("슈퍼계정").phone("010-0000-0001").role(Role.AGENCY).build());

        approvedAgency = agenciesRepository.save(Agencies.builder()
                .agencyName("승인된대행사").businessNumber("BIZ-APPROVED")
                .agencyAddress("서울").agencyFeeRate(5.0)
                .approvalStatus(AgencyStatus.APPROVED)
                .representativeId(superUser).build());
    }

    private AccountRequests pendingEngineerRequest(String email) {
        return accountRequestsRepository.save(AccountRequests.create(
                approvedAgency, email, "hashed", "홍길동",
                "010-1234-5678", AccountRequestsRole.ENGINEER, "상세주소 101호", region
        ));
    }

    // ─────────────────────────────────────────────
    //  POST /api/account-requests/engineer/approval
    // ─────────────────────────────────────────────

    @Test
    @DisplayName("[approval] 성공: AGENCY 슈퍼 계정이 요청 승인 → ENGINEER 역할 회원 생성 + account_requests APPROVED 전환")
    void approve_byAgencySuper_engineerCreated() throws Exception {
        AccountRequests request = pendingEngineerRequest("engineer@test.com");
        String superToken = jwtProvider.generateAccessToken(superUser.getId(), superUser.getEmail(), "AGENCY", null);

        mockMvc.perform(post("/api/account-requests/engineer/approval")
                        .header("Authorization", "Bearer " + superToken)
                        .param("accountId", String.valueOf(request.getId())))
                .andExpect(status().isNoContent());

        // account_requests → APPROVED 전환 및 생성된 User 연결 확인
        AccountRequests updated = accountRequestsRepository.findById(request.getId()).orElseThrow();
        assertThat(updated.getStatus()).isEqualTo(AccountRequestsStatus.APPROVED);
        assertThat(updated.getCreatedUserId()).isNotNull();
        assertThat(updated.getReviewedBy().getId()).isEqualTo(superUser.getId());

        // users 테이블에 ENGINEER 역할 회원 생성 확인
        User createdEngineer = userRepository.findByEmail("engineer@test.com").orElseThrow();
        assertThat(createdEngineer.getRole()).isEqualTo(Role.ENGINEER);
        assertThat(createdEngineer.getName()).isEqualTo("홍길동");
    }

    @Test
    @DisplayName("[approval] 실패: ADMIN 계정으로 승인 시도 — 401 Unauthorized")
    void approve_byAdmin_401() throws Exception {
        User adminUser = userRepository.save(User.builder()
                .email("admin@careflow.com").passwordHash("hashed")
                .name("관리자").role(Role.ADMIN).build());
        AccountRequests request = pendingEngineerRequest("engineer2@test.com");
        String adminToken = jwtProvider.generateAccessToken(adminUser.getId(), adminUser.getEmail(), "ADMIN", null);

        mockMvc.perform(post("/api/account-requests/engineer/approval")
                        .header("Authorization", "Bearer " + adminToken)
                        .param("accountId", String.valueOf(request.getId())))
                .andExpect(status().isUnauthorized());

        // account_requests 상태 변경 없음 확인
        assertThat(accountRequestsRepository.findById(request.getId())
                .orElseThrow().getStatus()).isEqualTo(AccountRequestsStatus.PENDING);
    }

    @Test
    @DisplayName("[approval] 실패: 존재하지 않는 요청 ID — 404 Not Found")
    void approve_notFound_404() throws Exception {
        String superToken = jwtProvider.generateAccessToken(superUser.getId(), superUser.getEmail(), "AGENCY", null);

        mockMvc.perform(post("/api/account-requests/engineer/approval")
                        .header("Authorization", "Bearer " + superToken)
                        .param("accountId", "999999"))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("[approval] 실패: 이미 APPROVED 된 요청 재승인 시도 — 400 Bad Request")
    void approve_alreadyApproved_400() throws Exception {
        AccountRequests alreadyApproved = accountRequestsRepository.save(
                AccountRequests.builder()
                        .agency(approvedAgency).email("done@test.com").password("hashed")
                        .name("기승인").phone("010-0000-0002")
                        .requestsRole(AccountRequestsRole.ENGINEER).addressDetail("상세주소")
                        .region(region).status(AccountRequestsStatus.APPROVED).build());

        String superToken = jwtProvider.generateAccessToken(superUser.getId(), superUser.getEmail(), "AGENCY", null);

        mockMvc.perform(post("/api/account-requests/engineer/approval")
                        .header("Authorization", "Bearer " + superToken)
                        .param("accountId", String.valueOf(alreadyApproved.getId())))
                .andExpect(status().isBadRequest());
    }

    // ─────────────────────────────────────────────
    //  POST /api/account-requests/engineer/rejection
    // ─────────────────────────────────────────────

    @Test
    @DisplayName("[rejection] 성공: AGENCY 슈퍼 계정이 요청 거부 → REJECTED 전환 + 거부 사유·검토자 기록")
    void reject_byAgencySuper_dbUpdated() throws Exception {
        AccountRequests request = pendingEngineerRequest("engineer3@test.com");
        String superToken = jwtProvider.generateAccessToken(superUser.getId(), superUser.getEmail(), "AGENCY", null);

        mockMvc.perform(post("/api/account-requests/engineer/rejection")
                        .header("Authorization", "Bearer " + superToken)
                        .param("accountId", String.valueOf(request.getId()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new AccountRequestReject("요건 미충족으로 거부합니다."))))
                .andExpect(status().isNoContent());

        AccountRequests updated = accountRequestsRepository.findById(request.getId()).orElseThrow();
        assertThat(updated.getStatus()).isEqualTo(AccountRequestsStatus.REJECTED);
        assertThat(updated.getRejectReason()).isEqualTo("요건 미충족으로 거부합니다.");
        assertThat(updated.getReviewedBy().getId()).isEqualTo(superUser.getId());

        // 수리기사 거부 시 대행사 상태는 변경되지 않음
        assertThat(agenciesRepository.findById(approvedAgency.getId())
                .orElseThrow().getApprovalStatus()).isEqualTo(AgencyStatus.APPROVED);
    }

    @Test
    @DisplayName("[rejection] 실패: ADMIN 계정으로 거부 시도 — 401 Unauthorized")
    void reject_byAdmin_401() throws Exception {
        User adminUser = userRepository.save(User.builder()
                .email("admin2@careflow.com").passwordHash("hashed")
                .name("관리자2").role(Role.ADMIN).build());
        AccountRequests request = pendingEngineerRequest("engineer4@test.com");
        String adminToken = jwtProvider.generateAccessToken(adminUser.getId(), adminUser.getEmail(), "ADMIN", null);

        mockMvc.perform(post("/api/account-requests/engineer/rejection")
                        .header("Authorization", "Bearer " + adminToken)
                        .param("accountId", String.valueOf(request.getId()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new AccountRequestReject("거부 사유"))))
                .andExpect(status().isUnauthorized());

        assertThat(accountRequestsRepository.findById(request.getId())
                .orElseThrow().getStatus()).isEqualTo(AccountRequestsStatus.PENDING);
    }

    @Test
    @DisplayName("[rejection] 실패: 이미 APPROVED 된 요청 거부 시도 — 401 Unauthorized")
    void reject_alreadyApproved_401() throws Exception {
        AccountRequests alreadyApproved = accountRequestsRepository.save(
                AccountRequests.builder()
                        .agency(approvedAgency).email("done2@test.com").password("hashed")
                        .name("기승인2").phone("010-0000-0003")
                        .requestsRole(AccountRequestsRole.ENGINEER).addressDetail("상세주소")
                        .region(region).status(AccountRequestsStatus.APPROVED).build());

        String superToken = jwtProvider.generateAccessToken(superUser.getId(), superUser.getEmail(), "AGENCY", null);

        mockMvc.perform(post("/api/account-requests/engineer/rejection")
                        .header("Authorization", "Bearer " + superToken)
                        .param("accountId", String.valueOf(alreadyApproved.getId()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new AccountRequestReject("뒤늦은 거부"))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("[rejection] 실패: 이미 REJECTED 된 요청 재거부 시도 — 401 Unauthorized")
    void reject_alreadyRejected_401() throws Exception {
        AccountRequests alreadyRejected = accountRequestsRepository.save(
                AccountRequests.builder()
                        .agency(approvedAgency).email("rej@test.com").password("hashed")
                        .name("기거부").phone("010-0000-0004")
                        .requestsRole(AccountRequestsRole.ENGINEER).addressDetail("상세주소")
                        .region(region).status(AccountRequestsStatus.REJECTED).build());

        String superToken = jwtProvider.generateAccessToken(superUser.getId(), superUser.getEmail(), "AGENCY", null);

        mockMvc.perform(post("/api/account-requests/engineer/rejection")
                        .header("Authorization", "Bearer " + superToken)
                        .param("accountId", String.valueOf(alreadyRejected.getId()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new AccountRequestReject("중복 거부"))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("[rejection] 실패: 거부 사유가 255자 초과 — 400 Bad Request")
    void reject_reasonTooLong_400() throws Exception {
        AccountRequests request = pendingEngineerRequest("engineer5@test.com");
        String superToken = jwtProvider.generateAccessToken(superUser.getId(), superUser.getEmail(), "AGENCY", null);

        mockMvc.perform(post("/api/account-requests/engineer/rejection")
                        .header("Authorization", "Bearer " + superToken)
                        .param("accountId", String.valueOf(request.getId()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new AccountRequestReject("가".repeat(256)))))
                .andExpect(status().isBadRequest());
    }
}
