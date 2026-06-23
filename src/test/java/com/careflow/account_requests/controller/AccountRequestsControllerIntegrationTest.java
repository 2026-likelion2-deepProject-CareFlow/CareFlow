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
@DisplayName("AccountRequestsController 통합 테스트 (H2)")
class AccountRequestsControllerIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private AccountRequestsRepository accountRequestsRepository;
    @Autowired private AgenciesRepository agenciesRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private RegionRepository regionRepository;
    @Autowired private JwtProvider jwtProvider;

    private Regions region;
    private User adminUser;

    @BeforeEach
    void setUp() {
        // cleanup.sql 실행 후 공통 픽스처 재삽입
        region = regionRepository.save(
                Regions.create("서울특별시 서초구", null, 1, 0)
        );
        adminUser = userRepository.save(User.builder()
                .email("admin@careflow.com")
                .passwordHash("hashed")
                .name("관리자")
                .phone("010-9999-0000")
                .role(Role.ADMIN)
                .build()
        );
    }

    // ─────────────────────────────────────────────
    //  POST /api/account-requests/agency/approval
    // ─────────────────────────────────────────────

    @Test
    @DisplayName("[approval] 성공: ADMIN이 슈퍼 계정 요청 승인 → users 생성 + agency APPROVED 전환")
    void approve_superAccountRequest_byAdmin_dbUpdated() throws Exception {
        Agencies pendingAgency = agenciesRepository.save(Agencies.builder()
                .agencyName("테스트대행사A").businessNumber("BIZ-A01")
                .agencyAddress("서울").agencyFeeRate(5.0).build());

        AccountRequests request = accountRequestsRepository.save(AccountRequests.create(
                pendingAgency, "super@agencyA.com", "hashed", "김슈퍼",
                "010-1111-2222", AccountRequestsRole.AGENCY, "상세주소", region));

        String adminToken = jwtProvider.generateAccessToken(
                adminUser.getId(), adminUser.getEmail(), "ADMIN");

        mockMvc.perform(post("/api/account-requests/agency/approval")
                        .header("Authorization", "Bearer " + adminToken)
                        .param("accountId", String.valueOf(request.getId())))
                .andExpect(status().isNoContent());

        // account_requests → APPROVED + 생성된 User 연결 확인
        AccountRequests updated = accountRequestsRepository.findById(request.getId()).orElseThrow();
        assertThat(updated.getStatus()).isEqualTo(AccountRequestsStatus.APPROVED);
        assertThat(updated.getCreatedUserId()).isNotNull();
        assertThat(updated.getReviewedBy().getId()).isEqualTo(adminUser.getId());

        // agency → APPROVED 전환 + 슈퍼 계정 연결 확인
        Agencies updatedAgency = agenciesRepository.findById(pendingAgency.getId()).orElseThrow();
        assertThat(updatedAgency.getApprovalStatus()).isEqualTo(AgencyStatus.APPROVED);
        assertThat(updatedAgency.getRepresentativeId()).isNotNull();

        // users 에 슈퍼 계정 생성 확인
        User createdUser = userRepository.findByEmail("super@agencyA.com").orElseThrow();
        assertThat(createdUser.getRole()).isEqualTo(Role.AGENCY);
        assertThat(createdUser.getName()).isEqualTo("김슈퍼");
    }

    @Test
    @DisplayName("[approval] 성공: AGENCY 슈퍼 계정이 같은 대행사 일반 관리자 요청 승인 → users 생성")
    void approve_managerAccountRequest_byAgencySuper_dbUpdated() throws Exception {
        User superUser = userRepository.save(User.builder()
                .email("super@agencyB.com").passwordHash("hashed")
                .name("슈퍼B").phone("010-2222-3333").role(Role.AGENCY).build());

        Agencies approvedAgency = agenciesRepository.save(Agencies.builder()
                .agencyName("대행사B").businessNumber("BIZ-B01")
                .agencyAddress("서울").agencyFeeRate(5.0)
                .approvalStatus(AgencyStatus.APPROVED)
                .representativeId(superUser).build());

        AccountRequests managerRequest = accountRequestsRepository.save(AccountRequests.create(
                approvedAgency, "manager@agencyB.com", "hashed", "김매니저",
                "010-3333-4444", AccountRequestsRole.AGENCY, "상세주소", region));

        String superToken = jwtProvider.generateAccessToken(
                superUser.getId(), superUser.getEmail(), "AGENCY");

        mockMvc.perform(post("/api/account-requests/agency/approval")
                        .header("Authorization", "Bearer " + superToken)
                        .param("accountId", String.valueOf(managerRequest.getId())))
                .andExpect(status().isNoContent());

        User createdManager = userRepository.findByEmail("manager@agencyB.com").orElseThrow();
        assertThat(createdManager.getRole()).isEqualTo(Role.AGENCY);
        assertThat(createdManager.getName()).isEqualTo("김매니저");

        AccountRequests updated = accountRequestsRepository.findById(managerRequest.getId()).orElseThrow();
        assertThat(updated.getStatus()).isEqualTo(AccountRequestsStatus.APPROVED);

        // APPROVED 대행사이므로 agency 상태 변화 없음
        Agencies unchanged = agenciesRepository.findById(approvedAgency.getId()).orElseThrow();
        assertThat(unchanged.getApprovalStatus()).isEqualTo(AgencyStatus.APPROVED);
    }

    @Test
    @DisplayName("[approval] 실패: AGENCY가 PENDING 대행사의 슈퍼 계정 요청 승인 시도 — 401 Unauthorized")
    void approve_agencyTriesSuperRequest_401() throws Exception {
        User superC = userRepository.save(User.builder()
                .email("super@agencyC.com").passwordHash("hashed")
                .name("슈퍼C").role(Role.AGENCY).build());
        agenciesRepository.save(Agencies.builder()
                .agencyName("대행사C").businessNumber("BIZ-C01")
                .agencyAddress("서울").agencyFeeRate(5.0)
                .approvalStatus(AgencyStatus.APPROVED).representativeId(superC).build());

        Agencies pendingD = agenciesRepository.save(Agencies.builder()
                .agencyName("신규대행사D").businessNumber("BIZ-D01")
                .agencyAddress("서울").agencyFeeRate(5.0).build());
        AccountRequests superRequest = accountRequestsRepository.save(AccountRequests.create(
                pendingD, "new@agencyD.com", "hashed", "새사람",
                "010-4444-5555", AccountRequestsRole.AGENCY, "상세주소", region));

        String superCToken = jwtProvider.generateAccessToken(superC.getId(), superC.getEmail(), "AGENCY");

        mockMvc.perform(post("/api/account-requests/agency/approval")
                        .header("Authorization", "Bearer " + superCToken)
                        .param("accountId", String.valueOf(superRequest.getId())))
                .andExpect(status().isUnauthorized());

        assertThat(accountRequestsRepository.findById(superRequest.getId())
                .orElseThrow().getStatus()).isEqualTo(AccountRequestsStatus.PENDING);
    }

    @Test
    @DisplayName("[approval] 실패: 다른 대행사 슈퍼 계정이 타 대행사 일반 관리자 요청 승인 시도 — 401 Unauthorized")
    void approve_byOtherAgencySuper_401() throws Exception {
        User superE = userRepository.save(User.builder()
                .email("super@agencyE.com").passwordHash("hashed")
                .name("슈퍼E").role(Role.AGENCY).build());
        agenciesRepository.save(Agencies.builder()
                .agencyName("대행사E").businessNumber("BIZ-E01")
                .agencyAddress("서울").agencyFeeRate(5.0)
                .approvalStatus(AgencyStatus.APPROVED).representativeId(superE).build());

        User superF = userRepository.save(User.builder()
                .email("super@agencyF.com").passwordHash("hashed")
                .name("슈퍼F").role(Role.AGENCY).build());
        Agencies agencyF = agenciesRepository.save(Agencies.builder()
                .agencyName("대행사F").businessNumber("BIZ-F01")
                .agencyAddress("서울").agencyFeeRate(5.0)
                .approvalStatus(AgencyStatus.APPROVED).representativeId(superF).build());
        AccountRequests fManagerReq = accountRequestsRepository.save(AccountRequests.create(
                agencyF, "manager@agencyF.com", "hashed", "매니저F",
                "010-6666-7777", AccountRequestsRole.AGENCY, "상세주소", region));

        String superEToken = jwtProvider.generateAccessToken(superE.getId(), superE.getEmail(), "AGENCY");

        mockMvc.perform(post("/api/account-requests/agency/approval")
                        .header("Authorization", "Bearer " + superEToken)
                        .param("accountId", String.valueOf(fManagerReq.getId())))
                .andExpect(status().isUnauthorized());

        assertThat(accountRequestsRepository.findById(fManagerReq.getId())
                .orElseThrow().getStatus()).isEqualTo(AccountRequestsStatus.PENDING);
    }

    @Test
    @DisplayName("[approval] 실패: 존재하지 않는 요청 ID 승인 시도 — 404 Not Found")
    void approve_notFound_404() throws Exception {
        String adminToken = jwtProvider.generateAccessToken(
                adminUser.getId(), adminUser.getEmail(), "ADMIN");

        mockMvc.perform(post("/api/account-requests/agency/approval")
                        .header("Authorization", "Bearer " + adminToken)
                        .param("accountId", "999999"))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("[approval] 실패: 이미 APPROVED 된 요청 재승인 시도 — 400 Bad Request")
    void approve_alreadyApproved_400() throws Exception {
        Agencies agency = agenciesRepository.save(Agencies.builder()
                .agencyName("이미승인대행사").businessNumber("BIZ-DONE1")
                .agencyAddress("서울").agencyFeeRate(5.0)
                .approvalStatus(AgencyStatus.APPROVED).build());

        AccountRequests approved = accountRequestsRepository.save(
                AccountRequests.builder()
                        .agency(agency).email("already@approved.com").password("hashed")
                        .name("승인완료").phone("010-0001-0001")
                        .requestsRole(AccountRequestsRole.AGENCY).addressDetail("상세주소")
                        .region(region).status(AccountRequestsStatus.APPROVED).build());

        String adminToken = jwtProvider.generateAccessToken(
                adminUser.getId(), adminUser.getEmail(), "ADMIN");

        mockMvc.perform(post("/api/account-requests/agency/approval")
                        .header("Authorization", "Bearer " + adminToken)
                        .param("accountId", String.valueOf(approved.getId())))
                .andExpect(status().isBadRequest());
    }

    // ─────────────────────────────────────────────
    //  POST /api/account-requests/agency/rejection
    // ─────────────────────────────────────────────

    @Test
    @DisplayName("[rejection] 성공: ADMIN이 슈퍼 계정 요청 거부 → REJECTED + agency REJECTED 전환")
    void reject_superAccountRequest_byAdmin_dbUpdated() throws Exception {
        Agencies pendingAgency = agenciesRepository.save(Agencies.builder()
                .agencyName("거부대행사G").businessNumber("BIZ-G01")
                .agencyAddress("서울").agencyFeeRate(5.0).build());

        AccountRequests request = accountRequestsRepository.save(AccountRequests.create(
                pendingAgency, "super@agencyG.com", "hashed", "김거부",
                "010-7777-8888", AccountRequestsRole.AGENCY, "상세주소", region));

        String adminToken = jwtProvider.generateAccessToken(
                adminUser.getId(), adminUser.getEmail(), "ADMIN");

        mockMvc.perform(post("/api/account-requests/agency/rejection")
                        .header("Authorization", "Bearer " + adminToken)
                        .param("accountId", String.valueOf(request.getId()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new AccountRequestReject("서류 미흡으로 거부합니다."))))
                .andExpect(status().isNoContent());

        AccountRequests updated = accountRequestsRepository.findById(request.getId()).orElseThrow();
        assertThat(updated.getStatus()).isEqualTo(AccountRequestsStatus.REJECTED);
        assertThat(updated.getRejectReason()).isEqualTo("서류 미흡으로 거부합니다.");
        assertThat(updated.getReviewedBy().getId()).isEqualTo(adminUser.getId());

        // 슈퍼 계정 요청이므로 대행사도 REJECTED
        Agencies updatedAgency = agenciesRepository.findById(pendingAgency.getId()).orElseThrow();
        assertThat(updatedAgency.getApprovalStatus()).isEqualTo(AgencyStatus.REJECTED);
    }

    @Test
    @DisplayName("[rejection] 성공: AGENCY 슈퍼 계정이 일반 관리자 요청 거부 → REJECTED, agency 상태 유지")
    void reject_managerRequest_byAgencySuper_agencyUnchanged() throws Exception {
        User superH = userRepository.save(User.builder()
                .email("super@agencyH.com").passwordHash("hashed")
                .name("슈퍼H").role(Role.AGENCY).build());
        Agencies approvedH = agenciesRepository.save(Agencies.builder()
                .agencyName("대행사H").businessNumber("BIZ-H01")
                .agencyAddress("서울").agencyFeeRate(5.0)
                .approvalStatus(AgencyStatus.APPROVED).representativeId(superH).build());
        AccountRequests managerReq = accountRequestsRepository.save(AccountRequests.create(
                approvedH, "manager@agencyH.com", "hashed", "매니저H",
                "010-8888-9999", AccountRequestsRole.AGENCY, "상세주소", region));

        String superToken = jwtProvider.generateAccessToken(superH.getId(), superH.getEmail(), "AGENCY");

        mockMvc.perform(post("/api/account-requests/agency/rejection")
                        .header("Authorization", "Bearer " + superToken)
                        .param("accountId", String.valueOf(managerReq.getId()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new AccountRequestReject("요건 미충족"))))
                .andExpect(status().isNoContent());

        assertThat(accountRequestsRepository.findById(managerReq.getId())
                .orElseThrow().getStatus()).isEqualTo(AccountRequestsStatus.REJECTED);

        // APPROVED 대행사이므로 상태 변경 없음
        assertThat(agenciesRepository.findById(approvedH.getId())
                .orElseThrow().getApprovalStatus()).isEqualTo(AgencyStatus.APPROVED);
    }

    @Test
    @DisplayName("[rejection] 실패: 이미 APPROVED 된 요청 거부 시도 — 401 Unauthorized")
    void reject_alreadyApproved_401() throws Exception {
        Agencies agency = agenciesRepository.save(Agencies.builder()
                .agencyName("이미승인대행사2").businessNumber("BIZ-DONE2")
                .agencyAddress("서울").agencyFeeRate(5.0)
                .approvalStatus(AgencyStatus.APPROVED).build());
        AccountRequests approved = accountRequestsRepository.save(
                AccountRequests.builder()
                        .agency(agency).email("done@approved.com").password("hashed")
                        .name("승인완료").phone("010-0001-0001")
                        .requestsRole(AccountRequestsRole.AGENCY).addressDetail("상세주소")
                        .region(region).status(AccountRequestsStatus.APPROVED).build());

        String adminToken = jwtProvider.generateAccessToken(
                adminUser.getId(), adminUser.getEmail(), "ADMIN");

        mockMvc.perform(post("/api/account-requests/agency/rejection")
                        .header("Authorization", "Bearer " + adminToken)
                        .param("accountId", String.valueOf(approved.getId()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new AccountRequestReject("뒤늦은 거부"))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("[rejection] 실패: 이미 REJECTED 된 요청 재거부 시도 — 401 Unauthorized")
    void reject_alreadyRejected_401() throws Exception {
        Agencies agency = agenciesRepository.save(Agencies.builder()
                .agencyName("이미거부대행사").businessNumber("BIZ-REJ01")
                .agencyAddress("서울").agencyFeeRate(5.0)
                .approvalStatus(AgencyStatus.REJECTED).build());
        AccountRequests rejected = accountRequestsRepository.save(
                AccountRequests.builder()
                        .agency(agency).email("done@rejected.com").password("hashed")
                        .name("거부완료").phone("010-0002-0002")
                        .requestsRole(AccountRequestsRole.AGENCY).addressDetail("상세주소")
                        .region(region).status(AccountRequestsStatus.REJECTED).build());

        String adminToken = jwtProvider.generateAccessToken(
                adminUser.getId(), adminUser.getEmail(), "ADMIN");

        mockMvc.perform(post("/api/account-requests/agency/rejection")
                        .header("Authorization", "Bearer " + adminToken)
                        .param("accountId", String.valueOf(rejected.getId()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new AccountRequestReject("중복 거부"))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("[rejection] 실패: 거부 사유가 255자 초과 — 400 Bad Request")
    void reject_reasonTooLong_400() throws Exception {
        Agencies agency = agenciesRepository.save(Agencies.builder()
                .agencyName("대행사I").businessNumber("BIZ-I01")
                .agencyAddress("서울").agencyFeeRate(5.0).build());
        AccountRequests request = accountRequestsRepository.save(AccountRequests.create(
                agency, "super@agencyI.com", "hashed", "박길동",
                "010-9999-1111", AccountRequestsRole.AGENCY, "상세주소", region));

        String adminToken = jwtProvider.generateAccessToken(
                adminUser.getId(), adminUser.getEmail(), "ADMIN");

        mockMvc.perform(post("/api/account-requests/agency/rejection")
                        .header("Authorization", "Bearer " + adminToken)
                        .param("accountId", String.valueOf(request.getId()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new AccountRequestReject("가".repeat(256)))))
                .andExpect(status().isBadRequest());
    }
}
