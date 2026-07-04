package com.careflow.agency.controller;

import com.careflow.agency.dto.request.AgencyFeeRateUpdateRequest;
import com.careflow.agency.dto.request.AgencyProfileUpdateRequest;
import com.careflow.agency.entity.Agencies;
import com.careflow.agency.repository.AgenciesRepository;
import com.careflow.agency_bank_account.entity.AgencyBankAccount;
import com.careflow.agency_bank_account.repository.AgencyBankAccountRepository;
import com.careflow.auth.security.JwtProvider;
import com.careflow.common.enums.AgencyStatus;
import com.careflow.common.enums.Role;
import com.careflow.user.entity.User;
import com.careflow.user.repository.UserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("local")
@Sql(scripts = "/cleanup.sql", executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
@DisplayName("대행사 설정 API 통합 테스트 (H2)")
class AgencySettingsControllerIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private AgenciesRepository agenciesRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private JwtProvider jwtProvider;
    @Autowired private AgencyBankAccountRepository agencyBankAccountRepository;

    // 공통 픽스처
    private Agencies agency;
    private User representative;
    private String accessToken;

    @BeforeEach
    void setUp() {
        // 1. 대행사 저장 (APPROVED 상태)
        agency = agenciesRepository.save(Agencies.builder()
                .agencyName("테스트대행사")
                .businessNumber("TEST-BIZ-001")
                .agencyAddress("서울특별시 강남구 테헤란로 1")
                .agencyFeeRate(0.05) // 비율(5%) — v14 스키마 기준 agencies.agency_fee_rate는 0~1 비율로 저장
                .approvalStatus(AgencyStatus.APPROVED)
                .build());

        // 2. 대행사 대표 사용자(ROLE_AGENCY) 저장
        representative = userRepository.save(User.builder()
                .email("agency-rep@test.com")
                .passwordHash("hashed")
                .name("대행사대표")
                .phone("010-1234-5678")
                .role(Role.AGENCY)
                .agency(agency)
                .build());

        // 3. agencies.representative_user_id 를 해당 사용자로 설정
        agency.approve(representative, representative);
        agenciesRepository.save(agency);

        // 4. 테스트용 JWT 발급 — 실제 로그인(AuthService)과 동일하게 Role.name() 값인 "AGENCY" 사용
        accessToken = jwtProvider.generateAccessToken(
                representative.getId(), representative.getEmail(), "AGENCY", null);
    }

    // ─────────────────────────────────────────────
    //  GET /api/agency/me
    // ─────────────────────────────────────────────

    @Nested
    @DisplayName("GET /api/agency/me — 대행사 정보 조회")
    class GetProfile {

        @Test
        @DisplayName("성공: 대표 계정 — 200 OK")
        void getProfile_representative_200() throws Exception {
            String repToken = jwtProvider.generateAccessToken(
                    representative.getId(), representative.getEmail(), "AGENCY", agency.getId());

            mockMvc.perform(get("/api/agency/me")
                            .header("Authorization", "Bearer " + repToken))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.agencyName").value("테스트대행사"))
                    .andExpect(jsonPath("$.agencyAddress").value("서울특별시 강남구 테헤란로 1"));
        }

        @Test
        @DisplayName("성공: staff(비대표) 계정도 자기 소속 대행사 정보를 조회할 수 있어야 함 — 200 OK")
        void getProfile_staff_200() throws Exception {
            // representative_user_id 로는 매칭되지 않는 소속 staff 계정
            User staff = userRepository.save(User.builder()
                    .email("agency-staff@test.com")
                    .passwordHash("hashed")
                    .name("대행사직원")
                    .phone("010-2222-3333")
                    .role(Role.AGENCY)
                    .agency(agency)
                    .build());

            String staffToken = jwtProvider.generateAccessToken(
                    staff.getId(), staff.getEmail(), "AGENCY", agency.getId());

            mockMvc.perform(get("/api/agency/me")
                            .header("Authorization", "Bearer " + staffToken))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.agencyName").value("테스트대행사"));
        }

        @Test
        @DisplayName("실패: 인증 토큰 없음 — 401 Unauthorized")
        void getProfile_noAuth_401() throws Exception {
            mockMvc.perform(get("/api/agency/me"))
                    .andExpect(status().isUnauthorized());
        }
    }

    // ─────────────────────────────────────────────
    //  PATCH /api/agencies/profile
    // ─────────────────────────────────────────────

    @Nested
    @DisplayName("PATCH /api/agencies/profile — 대행사 프로필 수정")
    class UpdateProfile {

        @Test
        @DisplayName("성공: 상호명과 주소 모두 변경 — 200 OK + DB 반영 확인")
        void updateProfile_success_200() throws Exception {
            AgencyProfileUpdateRequest req = new AgencyProfileUpdateRequest(
                    "수정된대행사", "서울특별시 서초구 서초대로 99", null, null);

            mockMvc.perform(put("/api/agency/me")
                            .header("Authorization", "Bearer " + accessToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(req)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.agencyName").value("수정된대행사"))
                    .andExpect(jsonPath("$.agencyAddress").value("서울특별시 서초구 서초대로 99"));

            // DB 값 직접 검증
            Agencies updated = agenciesRepository.findById(agency.getId()).orElseThrow();
            assertThat(updated.getAgencyName()).isEqualTo("수정된대행사");
            assertThat(updated.getAgencyAddress()).isEqualTo("서울특별시 서초구 서초대로 99");
        }

        @Test
        @DisplayName("성공: 상호명만 변경해도 주소는 입력값 그대로 저장됨")
        void updateProfile_onlyNameChanged_addressFromRequest() throws Exception {
            // 요청에 기존 주소를 그대로 전달하면 기존 값이 유지됨
            AgencyProfileUpdateRequest req = new AgencyProfileUpdateRequest(
                    "이름만바꾼대행사", "서울특별시 강남구 테헤란로 1", null, null);

            mockMvc.perform(put("/api/agency/me")
                            .header("Authorization", "Bearer " + accessToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(req)))
                    .andExpect(status().isOk());

            Agencies updated = agenciesRepository.findById(agency.getId()).orElseThrow();
            assertThat(updated.getAgencyName()).isEqualTo("이름만바꾼대행사");
            assertThat(updated.getAgencyAddress()).isEqualTo("서울특별시 강남구 테헤란로 1");
        }

        @Test
        @DisplayName("성공: 계좌 정보 최초 등록 — 200 OK + agency_bank_accounts 신규 생성 확인")
        void updateProfile_registerBankAccount_success_200() throws Exception {
            AgencyProfileUpdateRequest req = new AgencyProfileUpdateRequest(
                    "테스트대행사", "서울특별시 강남구 테헤란로 1", "신한은행", "110-123-456789");

            mockMvc.perform(put("/api/agency/me")
                            .header("Authorization", "Bearer " + accessToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(req)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.bankName").value("신한은행"))
                    .andExpect(jsonPath("$.accountNumber").value("110-123-456789"));

            AgencyBankAccount saved = agencyBankAccountRepository.findByAgencyId(agency.getId()).orElseThrow();
            assertThat(saved.getBankName()).isEqualTo("신한은행");
            assertThat(saved.getAccountNumber()).isEqualTo("110-123-456789");
            // 프론트에서 아직 예금주명을 입력받지 않아 대행사 상호명으로 기본 설정됨
            assertThat(saved.getAccountHolder()).isEqualTo("테스트대행사");
        }

        @Test
        @DisplayName("성공: 이미 등록된 계좌 정보 수정 — 기존 레코드가 갱신됨(신규 생성 아님)")
        void updateProfile_updateExistingBankAccount_success_200() throws Exception {
            AgencyBankAccount existing = agencyBankAccountRepository.save(
                    AgencyBankAccount.create(agency.getId(), "국민은행", "123-456-789", "테스트대행사"));

            AgencyProfileUpdateRequest req = new AgencyProfileUpdateRequest(
                    "테스트대행사", "서울특별시 강남구 테헤란로 1", "신한은행", "110-123-456789");

            mockMvc.perform(put("/api/agency/me")
                            .header("Authorization", "Bearer " + accessToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(req)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.bankName").value("신한은행"));

            assertThat(agencyBankAccountRepository.count()).isEqualTo(1);
            AgencyBankAccount updated = agencyBankAccountRepository.findByAgencyId(agency.getId()).orElseThrow();
            assertThat(updated.getId()).isEqualTo(existing.getId());
            assertThat(updated.getBankName()).isEqualTo("신한은행");
            assertThat(updated.getAccountNumber()).isEqualTo("110-123-456789");
        }

        @Test
        @DisplayName("성공: 계좌 정보 없이 요청하면 기존 계좌 정보는 그대로 유지됨")
        void updateProfile_omitBankInfo_existingAccountUntouched_200() throws Exception {
            agencyBankAccountRepository.save(
                    AgencyBankAccount.create(agency.getId(), "국민은행", "123-456-789", "테스트대행사"));

            AgencyProfileUpdateRequest req = new AgencyProfileUpdateRequest(
                    "수정된대행사", "서울특별시 강남구 테헤란로 1", null, null);

            mockMvc.perform(put("/api/agency/me")
                            .header("Authorization", "Bearer " + accessToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(req)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.bankName").value("국민은행"));

            AgencyBankAccount unchanged = agencyBankAccountRepository.findByAgencyId(agency.getId()).orElseThrow();
            assertThat(unchanged.getBankName()).isEqualTo("국민은행");
            assertThat(unchanged.getAccountNumber()).isEqualTo("123-456-789");
        }

        @Test
        @DisplayName("실패: 존재하지 않는 사용자 토큰 — 404 Not Found")
        void updateProfile_unknownUser_404() throws Exception {
            // representative_user_id 가 존재하지 않는 userId 로 토큰 생성
            String unknownToken = jwtProvider.generateAccessToken(9999L, "ghost@test.com", "AGENCY", null);
            AgencyProfileUpdateRequest req = new AgencyProfileUpdateRequest("수정된대행사", "서울 서초구", null, null);

            mockMvc.perform(put("/api/agency/me")
                            .header("Authorization", "Bearer " + unknownToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(req)))
                    .andExpect(status().isNotFound());
        }
    }

    // ─────────────────────────────────────────────
    //  GET /api/agencies/fee-rate
    // ─────────────────────────────────────────────

    @Nested
    @DisplayName("GET /api/agencies/fee-rate — 대행사 수수료율 조회")
    class GetFeeRate {

        @Test
        @DisplayName("성공: 수수료율 조회 — 200 OK + DB 저장값(0.05 비율)이 변환 없이 그대로 응답")
        void getFeeRate_success_200() throws Exception {
            mockMvc.perform(get("/api/agencies/fee-rate")
                            .header("Authorization", "Bearer " + accessToken))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.agencyFeeRate").value(0.05))
                    .andExpect(jsonPath("$.agencyName").value("테스트대행사"));
        }

        @Test
        @DisplayName("실패: 존재하지 않는 사용자 토큰 — 404 Not Found")
        void getFeeRate_unknownUser_404() throws Exception {
            String unknownToken = jwtProvider.generateAccessToken(9999L, "ghost@test.com", "AGENCY", null);

            mockMvc.perform(get("/api/agencies/fee-rate")
                            .header("Authorization", "Bearer " + unknownToken))
                    .andExpect(status().isNotFound());
        }
    }

    // ─────────────────────────────────────────────
    //  PATCH /api/agencies/fee-rate
    // ─────────────────────────────────────────────

    @Nested
    @DisplayName("PATCH /api/agencies/fee-rate — 대행사 수수료율 수정")
    class UpdateFeeRate {

        @Test
        @DisplayName("성공: 비율 0.1(10%) 요청 — 200 OK + DB 값 0.1(비율) 그대로 저장 확인")
        void updateFeeRate_success_200() throws Exception {
            AgencyFeeRateUpdateRequest req = new AgencyFeeRateUpdateRequest(0.1);

            mockMvc.perform(patch("/api/agencies/fee-rate")
                            .header("Authorization", "Bearer " + accessToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(req)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.agencyFeeRate").value(0.1));

            Agencies updated = agenciesRepository.findById(agency.getId()).orElseThrow();
            assertThat(updated.getAgencyFeeRate()).isCloseTo(0.1, within(0.0001));
        }

        @Test
        @DisplayName("성공: 경계값 0(비율) — 200 OK + DB 값 0.0 확인")
        void updateFeeRate_minBoundary_200() throws Exception {
            AgencyFeeRateUpdateRequest req = new AgencyFeeRateUpdateRequest(0.0);

            mockMvc.perform(patch("/api/agencies/fee-rate")
                            .header("Authorization", "Bearer " + accessToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(req)))
                    .andExpect(status().isOk());

            Agencies updated = agenciesRepository.findById(agency.getId()).orElseThrow();
            assertThat(updated.getAgencyFeeRate()).isCloseTo(0.0, within(0.0001));
        }

        @Test
        @DisplayName("성공: 경계값 1(100%) — 200 OK + DB 값 1.0(비율) 확인")
        void updateFeeRate_maxBoundary_200() throws Exception {
            AgencyFeeRateUpdateRequest req = new AgencyFeeRateUpdateRequest(1.0);

            mockMvc.perform(patch("/api/agencies/fee-rate")
                            .header("Authorization", "Bearer " + accessToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(req)))
                    .andExpect(status().isOk());

            Agencies updated = agenciesRepository.findById(agency.getId()).orElseThrow();
            assertThat(updated.getAgencyFeeRate()).isCloseTo(1.0, within(0.0001));
        }

        @Test
        @DisplayName("실패: 수수료율 -0.1(범위 초과) — 400 Bad Request")
        void updateFeeRate_negative_400() throws Exception {
            AgencyFeeRateUpdateRequest req = new AgencyFeeRateUpdateRequest(-0.1);

            mockMvc.perform(patch("/api/agencies/fee-rate")
                            .header("Authorization", "Bearer " + accessToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(req)))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("실패: 수수료율 1.01(범위 초과, 101%) — 400 Bad Request")
        void updateFeeRate_over1_400() throws Exception {
            AgencyFeeRateUpdateRequest req = new AgencyFeeRateUpdateRequest(1.01);

            mockMvc.perform(patch("/api/agencies/fee-rate")
                            .header("Authorization", "Bearer " + accessToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(req)))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("실패: 존재하지 않는 사용자 토큰 — 404 Not Found")
        void updateFeeRate_unknownUser_404() throws Exception {
            String unknownToken = jwtProvider.generateAccessToken(9999L, "ghost@test.com", "AGENCY", null);
            AgencyFeeRateUpdateRequest req = new AgencyFeeRateUpdateRequest(0.1);

            mockMvc.perform(patch("/api/agencies/fee-rate")
                            .header("Authorization", "Bearer " + unknownToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(req)))
                    .andExpect(status().isNotFound());
        }
    }
}
