package com.careflow.agency.controller;

import com.careflow.account_requests.entity.AccountRequests;
import com.careflow.account_requests.repository.AccountRequestsRepository;
import com.careflow.agency.dto.request.AgencyCreateRequest;
import com.careflow.agency.entity.Agencies;
import com.careflow.agency.repository.AgenciesRepository;
import com.careflow.common.enums.AccountRequestsRole;
import com.careflow.common.enums.AccountRequestsStatus;
import com.careflow.common.enums.AgencyStatus;
import com.careflow.region.entity.Regions;
import com.careflow.region.repository.RegionRepository;
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

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("local")
@Sql(scripts = "/cleanup.sql", executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
@DisplayName("AgenciesController 통합 테스트 (H2)")
class AgenciesControllerIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private AgenciesRepository agenciesRepository;
    @Autowired private AccountRequestsRepository accountRequestsRepository;
    @Autowired private RegionRepository regionRepository;

    private Regions savedRegion;

    @BeforeEach
    void setUp() {
        // cleanup.sql 실행 후 공통 픽스처 재삽입
        savedRegion = regionRepository.save(
                Regions.create("서울특별시 강남구", null, 1, 0)
        );
    }

    // ─────────────────────────────────────────────
    //  POST /api/agencies/signup
    // ─────────────────────────────────────────────

    @Test
    @DisplayName("[signup] 성공: 최초 대행사 등록 — 201 반환 + account_requests 에 PENDING 요청 생성")
    void signup_newAgency_201_andPendingRequestSaved() throws Exception {
        AgencyCreateRequest req = buildRequest(
                "홍길동", "super@newagency.com",
                "NEW-BIZ-001", "신규대행사"
        );

        String body = mockMvc.perform(post("/api/agencies/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        Long accountRequestId = Long.parseLong(body);

        assertThat(agenciesRepository.findByBusinessNumber("NEW-BIZ-001")).isPresent();

        AccountRequests saved = accountRequestsRepository.findById(accountRequestId).orElseThrow();
        assertThat(saved.getStatus()).isEqualTo(AccountRequestsStatus.PENDING);
        assertThat(saved.getEmail()).isEqualTo("super@newagency.com");
        assertThat(saved.getRequestsRole()).isEqualTo(AccountRequestsRole.AGENCY);
        // lazy proxy 초기화 방지를 위해 별도 조회
        Agencies savedAgency = agenciesRepository.findByBusinessNumber("NEW-BIZ-001").orElseThrow();
        assertThat(savedAgency.getApprovalStatus()).isEqualTo(AgencyStatus.PENDING);
    }

    @Test
    @DisplayName("[signup] 성공: APPROVED 된 대행사에 일반 관리자 계정 요청 — 201 반환")
    void signup_existingApprovedAgency_201_andManagerRequestSaved() throws Exception {
        Agencies approvedAgency = saveApprovedAgency("APPROVED-BIZ-001", "승인된대행사");

        AgencyCreateRequest req = buildRequest(
                "김관리", "manager@approvedagency.com",
                "APPROVED-BIZ-001", "승인된대행사"
        );

        String body = mockMvc.perform(post("/api/agencies/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        Long accountRequestId = Long.parseLong(body);

        AccountRequests saved = accountRequestsRepository.findById(accountRequestId).orElseThrow();
        assertThat(saved.getStatus()).isEqualTo(AccountRequestsStatus.PENDING);
        assertThat(saved.getAgency().getId()).isEqualTo(approvedAgency.getId());
    }

    @Test
    @DisplayName("[signup] 실패: PENDING 인 대행사에 추가 요청 — 403 Forbidden")
    void signup_pendingAgency_403() throws Exception {
        savePendingAgency("PENDING-BIZ-001", "대기중대행사");

        AgencyCreateRequest req = buildRequest(
                "이중복", "dup@pending.com",
                "PENDING-BIZ-001", "대기중대행사"
        );

        mockMvc.perform(post("/api/agencies/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isForbidden());

        List<AccountRequests> requests = accountRequestsRepository.findAll();
        assertThat(requests).isEmpty();
    }

    @Test
    @DisplayName("[signup] 실패: REJECTED 된 대행사에 추가 요청 — 403 Forbidden")
    void signup_rejectedAgency_403() throws Exception {
        saveRejectedAgency("REJECTED-BIZ-001", "거부된대행사");

        AgencyCreateRequest req = buildRequest(
                "박재도전", "retry@rejected.com",
                "REJECTED-BIZ-001", "거부된대행사"
        );

        mockMvc.perform(post("/api/agencies/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("[signup] 실패: 이미 가입된 이메일로 재요청 — 400 Bad Request")
    void signup_duplicateEmail_400() throws Exception {
        Agencies agency = savePendingAgency("DUP-BIZ-001", "중복테스트대행사");
        accountRequestsRepository.save(AccountRequests.create(
                agency, "dup@test.com", "hashed", "홍길동",
                "010-1111-2222", AccountRequestsRole.AGENCY, "상세주소", savedRegion
        ));

        AgencyCreateRequest req = buildRequest(
                "최재시도", "dup@test.com",
                "NEW-BIZ-002", "또다른대행사"
        );

        mockMvc.perform(post("/api/agencies/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest());
    }

    // ─────────────────────────────────────────────
    //  GET /api/agencies/agency
    // ─────────────────────────────────────────────

    @Test
    @DisplayName("[getAgency] 성공: 존재하는 대행사 이름으로 조회 — 200 OK + 대행사 정보 반환")
    void getAgency_found_200() throws Exception {
        saveApprovedAgency("FIND-BIZ-001", "조회가능대행사");

        mockMvc.perform(get("/api/agencies/agency")
                        .param("agencyName", "조회가능대행사"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.businessNumber").value("FIND-BIZ-001"));
    }

    @Test
    @DisplayName("[getAgency] 실패: 존재하지 않는 대행사 이름으로 조회 — 404 Not Found")
    void getAgency_notFound_404() throws Exception {
        mockMvc.perform(get("/api/agencies/agency")
                        .param("agencyName", "없는대행사"))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("[getAgency] 실패: agencyName 파라미터 누락 — 400 Bad Request")
    void getAgency_missingParam_400() throws Exception {
        mockMvc.perform(get("/api/agencies/agency"))
                .andExpect(status().isBadRequest());
    }

    // ── 공통 헬퍼 ──

    private AgencyCreateRequest buildRequest(String name, String email,
                                             String businessNumber, String agencyName) {
        return new AgencyCreateRequest(
                name, email, "password123", "010-1234-5678",
                savedRegion.getId(), "상세주소", businessNumber, agencyName,
                "서울특별시 강남구 테헤란로 1", 5.00
        );
    }

    private Agencies savePendingAgency(String businessNumber, String agencyName) {
        return agenciesRepository.save(Agencies.builder()
                .agencyName(agencyName)
                .businessNumber(businessNumber)
                .agencyAddress("서울특별시 강남구")
                .agencyFeeRate(5.0)
                .build());
    }

    private Agencies saveApprovedAgency(String businessNumber, String agencyName) {
        return agenciesRepository.save(Agencies.builder()
                .agencyName(agencyName)
                .businessNumber(businessNumber)
                .agencyAddress("서울특별시 강남구")
                .agencyFeeRate(5.0)
                .approvalStatus(AgencyStatus.APPROVED)
                .build());
    }

    private Agencies saveRejectedAgency(String businessNumber, String agencyName) {
        return agenciesRepository.save(Agencies.builder()
                .agencyName(agencyName)
                .businessNumber(businessNumber)
                .agencyAddress("서울특별시 강남구")
                .agencyFeeRate(5.0)
                .approvalStatus(AgencyStatus.REJECTED)
                .build());
    }
}
