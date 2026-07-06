package com.careflow.agency.controller;

import com.careflow.agency.dto.request.AgencyFeeRateUpdateRequest;
import com.careflow.agency.dto.request.AgencyProfileUpdateRequest;
import com.careflow.agency.dto.request.AgencyWithdrawRequest;
import com.careflow.agency.dto.request.SecurityToggleRequest;
import com.careflow.agency.dto.response.AgencyFeeRateResponse;
import com.careflow.agency.dto.response.AgencyProfileResponse;
import com.careflow.agency.dto.response.AgencySecuritySettingsResponse;
import com.careflow.agency.dto.response.TrustedDeviceResponse;
import com.careflow.agency.service.AgenciesService;
import com.careflow.as_request.service.AgencyAsRequestService;
import com.careflow.as_status_log.service.AgencyAsStatusLogService;
import com.careflow.auth.security.CustomOAuth2UserService;
import com.careflow.auth.security.CustomUserDetails;
import com.careflow.auth.security.JwtProvider;
import com.careflow.auth.security.OAuth2LoginSuccessHandler;
import com.careflow.common.config.PasswordEncoderConfig;
import com.careflow.common.config.SecurityConfig;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.mapping.JpaMetamodelMappingContext;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.NoSuchElementException;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willDoNothing;
import static org.mockito.BDDMockito.willThrow;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest({AgenciesController.class, AgencyController.class})
@Import({SecurityConfig.class, PasswordEncoderConfig.class})
@DisplayName("AgenciesController 대행사 설정 단위 테스트")
class AgencySettingsControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;

    @MockitoBean private AgenciesService agenciesService;
    @MockitoBean private AgencyAsRequestService agencyAsRequestService;
    @MockitoBean private AgencyAsStatusLogService agencyAsStatusLogService;
    @MockitoBean private com.careflow.agency.service.AgencyDataTransferService agencyDataTransferService;
    @MockitoBean private JwtProvider jwtProvider;
    @MockitoBean private JpaMetamodelMappingContext jpaMetamodelMappingContext;
    @MockitoBean private CustomOAuth2UserService customOAuth2UserService;
    @MockitoBean private OAuth2LoginSuccessHandler oAuth2LoginSuccessHandler;
    @MockitoBean private ClientRegistrationRepository clientRegistrationRepository;
    @MockitoBean private org.springframework.data.redis.core.StringRedisTemplate stringRedisTemplate;

    // AGENCY 역할 인증 사용자 픽스처 — JWT payload의 role 클레임과 동일하게 "AGENCY" 사용
    private CustomUserDetails agencyUser() {
        return new CustomUserDetails(1L, "agency@test.com", "", "AGENCY", null);
    }

    // GET /api/agency/me 는 agencyId 기준 조회이므로 agencyId가 채워진 사용자 픽스처 필요
    // (대표든 staff든 role=AGENCY 면 동일하게 agencyId가 채워짐)
    private CustomUserDetails agencyUser(Long userId, Long agencyId) {
        return new CustomUserDetails(userId, "agency@test.com", "", "AGENCY", agencyId);
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
            AgencyProfileResponse resp = new AgencyProfileResponse(
                    5L, "테스트대행사", "서울특별시 강남구 테헤란로 1", "신한은행", "110-123-456789",
                    "김대표", "agency@test.com", java.time.LocalDateTime.of(2026, 7, 1, 9, 0));
            given(agenciesService.getProfile(eq(5L), eq(1L))).willReturn(resp);

            mockMvc.perform(get("/api/agency/me")
                            .with(user(agencyUser(1L, 5L))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.agencyName").value("테스트대행사"))
                    .andExpect(jsonPath("$.bankName").value("신한은행"))
                    .andExpect(jsonPath("$.accountNumber").value("110-123-456789"))
                    .andExpect(jsonPath("$.name").value("김대표"))
                    .andExpect(jsonPath("$.email").value("agency@test.com"));
        }

        @Test
        @DisplayName("성공: 계좌 미등록 시 bankName/accountNumber는 null — 200 OK")
        void getProfile_noBankAccount_nullFields_200() throws Exception {
            AgencyProfileResponse resp = new AgencyProfileResponse(
                    5L, "테스트대행사", "서울특별시 강남구 테헤란로 1", null, null,
                    "김대표", "agency@test.com", null);
            given(agenciesService.getProfile(eq(5L), eq(1L))).willReturn(resp);

            mockMvc.perform(get("/api/agency/me")
                            .with(user(agencyUser(1L, 5L))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.bankName").value(org.hamcrest.Matchers.nullValue()))
                    .andExpect(jsonPath("$.accountNumber").value(org.hamcrest.Matchers.nullValue()));
        }

        @Test
        @DisplayName("성공: staff(비대표) 계정도 agencyId 기준으로 동일하게 조회 — 200 OK")
        void getProfile_staff_200() throws Exception {
            AgencyProfileResponse resp = new AgencyProfileResponse(
                    5L, "테스트대행사", "서울특별시 강남구 테헤란로 1", "신한은행", "110-123-456789",
                    "박직원", "staff@test.com", java.time.LocalDateTime.of(2026, 7, 1, 9, 0));
            given(agenciesService.getProfile(eq(5L), eq(2L))).willReturn(resp);

            // userId=2L(대표 아님)이라도 토큰의 agencyId=5L만 일치하면 조회 성공해야 함
            mockMvc.perform(get("/api/agency/me")
                            .with(user(agencyUser(2L, 5L))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.agencyName").value("테스트대행사"));
        }

        @Test
        @DisplayName("실패: 인증 토큰 없음 — 401 Unauthorized")
        void getProfile_noAuth_401() throws Exception {
            mockMvc.perform(get("/api/agency/me"))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("실패: 대행사 정보 없음(서비스 NoSuchElementException) — 404 Not Found")
        void getProfile_notFound_404() throws Exception {
            given(agenciesService.getProfile(eq(5L), eq(1L)))
                    .willThrow(new NoSuchElementException("해당 사용자의 대행사 정보를 찾을 수 없습니다."));

            mockMvc.perform(get("/api/agency/me")
                            .with(user(agencyUser(1L, 5L))))
                    .andExpect(status().isNotFound());
        }
    }

    // ─────────────────────────────────────────────
    //  PATCH /api/agencies/profile
    // ─────────────────────────────────────────────

    @Nested
    @DisplayName("PATCH /api/agencies/profile — 대행사 프로필 수정")
    class UpdateProfile {

        @Test
        @DisplayName("성공: 유효한 요청 + AGENCY 인증 — 200 OK")
        void updateProfile_success_200() throws Exception {
            AgencyProfileUpdateRequest req = new AgencyProfileUpdateRequest(
                    "수정된대행사", "서울특별시 서초구 서초대로 1", "신한은행", "110-123-456789");
            AgencyProfileResponse resp = new AgencyProfileResponse(
                    1L, "수정된대행사", "서울특별시 서초구 서초대로 1", "신한은행", "110-123-456789",
                    "김대표", "agency@test.com", java.time.LocalDateTime.of(2026, 7, 1, 9, 0));

            given(agenciesService.updateProfile(eq(1L), any())).willReturn(resp);

            mockMvc.perform(put("/api/agency/me")
                            .with(user(agencyUser()))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(req)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.agencyName").value("수정된대행사"))
                    .andExpect(jsonPath("$.agencyAddress").value("서울특별시 서초구 서초대로 1"))
                    .andExpect(jsonPath("$.bankName").value("신한은행"))
                    .andExpect(jsonPath("$.accountNumber").value("110-123-456789"));
        }

        @Test
        @DisplayName("성공: 은행 정보 없이 상호명/주소만 수정 — 200 OK")
        void updateProfile_withoutBankInfo_success_200() throws Exception {
            AgencyProfileUpdateRequest req = new AgencyProfileUpdateRequest(
                    "수정된대행사", "서울특별시 서초구 서초대로 1", null, null);
            AgencyProfileResponse resp = new AgencyProfileResponse(
                    1L, "수정된대행사", "서울특별시 서초구 서초대로 1", null, null,
                    "김대표", "agency@test.com", java.time.LocalDateTime.of(2026, 7, 1, 9, 0));

            given(agenciesService.updateProfile(eq(1L), any())).willReturn(resp);

            mockMvc.perform(put("/api/agency/me")
                            .with(user(agencyUser()))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(req)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.agencyName").value("수정된대행사"));
        }

        @Test
        @DisplayName("실패: 인증 토큰 없음 — 401 Unauthorized")
        void updateProfile_noAuth_401() throws Exception {
            AgencyProfileUpdateRequest req = new AgencyProfileUpdateRequest("수정된대행사", "서울 서초구", null, null);

            mockMvc.perform(put("/api/agency/me")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(req)))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("실패: AGENCY 역할 아님 — 403 Forbidden")
        @WithMockUser(authorities = "CUSTOMER")
        void updateProfile_wrongRole_403() throws Exception {
            AgencyProfileUpdateRequest req = new AgencyProfileUpdateRequest("수정된대행사", "서울 서초구", null, null);

            mockMvc.perform(put("/api/agency/me")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(req)))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("유효성 실패: agencyName 공백 — 400 Bad Request")
        void updateProfile_blankName_400() throws Exception {
            AgencyProfileUpdateRequest req = new AgencyProfileUpdateRequest("", "서울 서초구", null, null);

            mockMvc.perform(put("/api/agency/me")
                            .with(user(agencyUser()))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(req)))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("유효성 실패: agencyAddress 공백 — 400 Bad Request")
        void updateProfile_blankAddress_400() throws Exception {
            AgencyProfileUpdateRequest req = new AgencyProfileUpdateRequest("정상대행사", "", null, null);

            mockMvc.perform(put("/api/agency/me")
                            .with(user(agencyUser()))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(req)))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("유효성 실패: agencyAddress 255자 초과 — 400 Bad Request")
        void updateProfile_addressTooLong_400() throws Exception {
            AgencyProfileUpdateRequest req = new AgencyProfileUpdateRequest("정상대행사", "주".repeat(256), null, null);

            mockMvc.perform(put("/api/agency/me")
                            .with(user(agencyUser()))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(req)))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("유효성 실패: bankName 50자 초과 — 400 Bad Request")
        void updateProfile_bankNameTooLong_400() throws Exception {
            AgencyProfileUpdateRequest req = new AgencyProfileUpdateRequest(
                    "정상대행사", "서울 서초구", "은".repeat(51), "110-123-456789");

            mockMvc.perform(put("/api/agency/me")
                            .with(user(agencyUser()))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(req)))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("실패: 대행사 정보 없음(서비스 NoSuchElementException) — 404 Not Found")
        void updateProfile_notFound_404() throws Exception {
            AgencyProfileUpdateRequest req = new AgencyProfileUpdateRequest("수정된대행사", "서울 서초구", null, null);

            given(agenciesService.updateProfile(eq(1L), any()))
                    .willThrow(new NoSuchElementException("해당 사용자의 대행사 정보를 찾을 수 없습니다."));

            mockMvc.perform(put("/api/agency/me")
                            .with(user(agencyUser()))
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
        @DisplayName("성공: AGENCY 인증 — 200 OK + agencyFeeRate 반환(비율)")
        void getFeeRate_success_200() throws Exception {
            AgencyFeeRateResponse resp = new AgencyFeeRateResponse(1L, "테스트대행사", 0.05);

            given(agenciesService.getFeeRate(1L)).willReturn(resp);

            mockMvc.perform(get("/api/agencies/fee-rate")
                            .with(user(agencyUser())))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.agencyFeeRate").value(0.05));
        }

        @Test
        @DisplayName("실패: 인증 토큰 없음 — 401 Unauthorized")
        void getFeeRate_noAuth_401() throws Exception {
            mockMvc.perform(get("/api/agencies/fee-rate"))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("실패: 대행사 정보 없음 — 404 Not Found")
        void getFeeRate_notFound_404() throws Exception {
            given(agenciesService.getFeeRate(1L))
                    .willThrow(new NoSuchElementException("해당 사용자의 대행사 정보를 찾을 수 없습니다."));

            mockMvc.perform(get("/api/agencies/fee-rate")
                            .with(user(agencyUser())))
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
        @DisplayName("성공: 유효한 수수료율(비율 0.075) + AGENCY 인증 — 200 OK")
        void updateFeeRate_success_200() throws Exception {
            AgencyFeeRateUpdateRequest req = new AgencyFeeRateUpdateRequest(0.075);
            AgencyFeeRateResponse resp = new AgencyFeeRateResponse(1L, "테스트대행사", 0.075);

            given(agenciesService.updateFeeRate(eq(1L), any())).willReturn(resp);

            mockMvc.perform(patch("/api/agencies/fee-rate")
                            .with(user(agencyUser()))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(req)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.agencyFeeRate").value(0.075));
        }

        @Test
        @DisplayName("실패: 인증 토큰 없음 — 401 Unauthorized")
        void updateFeeRate_noAuth_401() throws Exception {
            AgencyFeeRateUpdateRequest req = new AgencyFeeRateUpdateRequest(0.075);

            mockMvc.perform(patch("/api/agencies/fee-rate")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(req)))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("유효성 실패: agencyFeeRate null — 400 Bad Request")
        void updateFeeRate_nullFeeRate_400() throws Exception {
            AgencyFeeRateUpdateRequest req = new AgencyFeeRateUpdateRequest(null);

            mockMvc.perform(patch("/api/agencies/fee-rate")
                            .with(user(agencyUser()))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(req)))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("유효성 실패: 소수점 5자리(0.12345) — 400 Bad Request")
        void updateFeeRate_fractionExceeded_400() throws Exception {
            AgencyFeeRateUpdateRequest req = new AgencyFeeRateUpdateRequest(0.12345);

            mockMvc.perform(patch("/api/agencies/fee-rate")
                            .with(user(agencyUser()))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(req)))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("실패: 대행사 정보 없음 — 404 Not Found")
        void updateFeeRate_notFound_404() throws Exception {
            AgencyFeeRateUpdateRequest req = new AgencyFeeRateUpdateRequest(0.075);

            given(agenciesService.updateFeeRate(eq(1L), any()))
                    .willThrow(new NoSuchElementException("해당 사용자의 대행사 정보를 찾을 수 없습니다."));

            mockMvc.perform(patch("/api/agencies/fee-rate")
                            .with(user(agencyUser()))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(req)))
                    .andExpect(status().isNotFound());
        }

        @Test
        @DisplayName("실패: 수수료율 범위 초과(서비스 IllegalArgumentException) — 400 Bad Request")
        void updateFeeRate_outOfRange_400() throws Exception {
            AgencyFeeRateUpdateRequest req = new AgencyFeeRateUpdateRequest(1.5);

            given(agenciesService.updateFeeRate(eq(1L), any()))
                    .willThrow(new IllegalArgumentException("수수료율은 0 이상 1 이하의 비율이어야 합니다."));

            mockMvc.perform(patch("/api/agencies/fee-rate")
                            .with(user(agencyUser()))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(req)))
                    .andExpect(status().isBadRequest());
        }
    }

    // ─────────────────────────────────────────────
    //  DELETE /api/agency/me/withdraw
    // ─────────────────────────────────────────────

    @Nested
    @DisplayName("DELETE /api/agency/me/withdraw — 대행사 관리자 계정 탈퇴")
    class WithdrawAccount {

        @Test
        @DisplayName("성공: 일반 관리자, 올바른 비밀번호 — 204 No Content")
        void withdraw_success_204() throws Exception {
            AgencyWithdrawRequest req = new AgencyWithdrawRequest("현재비밀번호");
            willDoNothing().given(agenciesService).withdrawAccount(eq(1L), any());

            mockMvc.perform(delete("/api/agency/me/withdraw")
                            .with(user(agencyUser()))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(req)))
                    .andExpect(status().isNoContent());
        }

        @Test
        @DisplayName("실패: 비밀번호 불일치 — 400 Bad Request")
        void withdraw_wrongPassword_400() throws Exception {
            AgencyWithdrawRequest req = new AgencyWithdrawRequest("틀린비밀번호");
            willThrow(new IllegalArgumentException("현재 비밀번호가 일치하지 않습니다."))
                    .given(agenciesService).withdrawAccount(eq(1L), any());

            mockMvc.perform(delete("/api/agency/me/withdraw")
                            .with(user(agencyUser()))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(req)))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("실패: 대표 담당자 탈퇴 시도 — 403 Forbidden")
        void withdraw_representative_403() throws Exception {
            AgencyWithdrawRequest req = new AgencyWithdrawRequest("현재비밀번호");
            willThrow(new IllegalStateException("대표 담당자는 탈퇴할 수 없습니다. 먼저 대표를 위임하거나 대행사 해지를 문의해주세요."))
                    .given(agenciesService).withdrawAccount(eq(1L), any());

            mockMvc.perform(delete("/api/agency/me/withdraw")
                            .with(user(agencyUser()))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(req)))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("유효성 실패: password 공백 — 400 Bad Request")
        void withdraw_blankPassword_400() throws Exception {
            AgencyWithdrawRequest req = new AgencyWithdrawRequest("");

            mockMvc.perform(delete("/api/agency/me/withdraw")
                            .with(user(agencyUser()))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(req)))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("실패: 인증 토큰 없음 — 401 Unauthorized")
        void withdraw_noAuth_401() throws Exception {
            AgencyWithdrawRequest req = new AgencyWithdrawRequest("현재비밀번호");

            mockMvc.perform(delete("/api/agency/me/withdraw")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(req)))
                    .andExpect(status().isUnauthorized());
        }
    }

    // ─────────────────────────────────────────────
    //  GET /api/agency/me/security, PATCH .../two-factor, .../login-alert
    // ─────────────────────────────────────────────

    @Nested
    @DisplayName("로그인 보안 설정 조회/토글")
    class SecuritySettings {

        @Test
        @DisplayName("GET /api/agency/me/security — 성공 200 OK")
        void getSecuritySettings_success_200() throws Exception {
            given(agenciesService.getSecuritySettings(1L))
                    .willReturn(new AgencySecuritySettingsResponse(true, false, 2L));

            mockMvc.perform(get("/api/agency/me/security").with(user(agencyUser())))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.twoFactorEnabled").value(true))
                    .andExpect(jsonPath("$.loginAlertEnabled").value(false))
                    .andExpect(jsonPath("$.trustedDeviceCount").value(2));
        }

        @Test
        @DisplayName("GET /api/agency/me/security — 인증 없음 401")
        void getSecuritySettings_noAuth_401() throws Exception {
            mockMvc.perform(get("/api/agency/me/security"))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("PATCH .../two-factor — 성공 204")
        void toggleTwoFactor_success_204() throws Exception {
            willDoNothing().given(agenciesService).toggleTwoFactor(eq(1L), eq(true));

            mockMvc.perform(patch("/api/agency/me/security/two-factor")
                            .with(user(agencyUser()))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(new SecurityToggleRequest(true))))
                    .andExpect(status().isNoContent());
        }

        @Test
        @DisplayName("PATCH .../two-factor — enabled 누락 400")
        void toggleTwoFactor_missingEnabled_400() throws Exception {
            mockMvc.perform(patch("/api/agency/me/security/two-factor")
                            .with(user(agencyUser()))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{}"))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("PATCH .../login-alert — 성공 204")
        void toggleLoginAlert_success_204() throws Exception {
            willDoNothing().given(agenciesService).toggleLoginAlert(eq(1L), eq(true));

            mockMvc.perform(patch("/api/agency/me/security/login-alert")
                            .with(user(agencyUser()))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(new SecurityToggleRequest(true))))
                    .andExpect(status().isNoContent());
        }
    }

    // ─────────────────────────────────────────────
    //  GET/DELETE /api/agency/me/trusted-devices
    // ─────────────────────────────────────────────

    @Nested
    @DisplayName("신뢰 기기 목록 조회/삭제")
    class TrustedDevices {

        @Test
        @DisplayName("GET /api/agency/me/trusted-devices — 성공 200 OK")
        void getTrustedDevices_success_200() throws Exception {
            given(agenciesService.getTrustedDevices(1L)).willReturn(java.util.List.of(
                    new TrustedDeviceResponse(1L, "Mozilla/5.0 Chrome/126",
                            java.time.LocalDateTime.of(2026, 7, 6, 10, 0),
                            java.time.LocalDateTime.of(2026, 6, 1, 9, 0))));

            mockMvc.perform(get("/api/agency/me/trusted-devices").with(user(agencyUser())))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$[0].deviceId").value(1))
                    .andExpect(jsonPath("$[0].deviceName").value("Mozilla/5.0 Chrome/126"));
        }

        @Test
        @DisplayName("DELETE .../trusted-devices/{id} — 성공 204")
        void deleteTrustedDevice_success_204() throws Exception {
            willDoNothing().given(agenciesService).deleteTrustedDevice(eq(1L), eq(10L));

            mockMvc.perform(delete("/api/agency/me/trusted-devices/{deviceId}", 10L)
                            .with(user(agencyUser())))
                    .andExpect(status().isNoContent());
        }

        @Test
        @DisplayName("DELETE .../trusted-devices/{id} — 타인 기기 401")
        void deleteTrustedDevice_notOwner_401() throws Exception {
            willThrow(new IllegalAccessException("본인 소유의 기기만 삭제할 수 있습니다."))
                    .given(agenciesService).deleteTrustedDevice(eq(1L), eq(10L));

            mockMvc.perform(delete("/api/agency/me/trusted-devices/{deviceId}", 10L)
                            .with(user(agencyUser())))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("DELETE .../trusted-devices/{id} — 존재하지 않는 기기 404")
        void deleteTrustedDevice_notFound_404() throws Exception {
            willThrow(new NoSuchElementException("해당 기기 정보를 찾을 수 없습니다."))
                    .given(agenciesService).deleteTrustedDevice(eq(1L), eq(999L));

            mockMvc.perform(delete("/api/agency/me/trusted-devices/{deviceId}", 999L)
                            .with(user(agencyUser())))
                    .andExpect(status().isNotFound());
        }
    }

    // ─────────────────────────────────────────────
    //  GET /api/agency/me/data-export, POST /api/agency/me/data-import
    // ─────────────────────────────────────────────

    @Nested
    @DisplayName("데이터 내보내기/가져오기")
    class DataTransfer {

        @Test
        @DisplayName("GET /api/agency/me/data-export — 성공 200 OK + text/csv")
        void exportData_success_200() throws Exception {
            given(agencyDataTransferService.exportEngineerRoster(any()))
                    .willReturn("engineerUserId,name\n1,김철수\n".getBytes());

            mockMvc.perform(get("/api/agency/me/data-export").with(user(agencyUser(1L, 5L))))
                    .andExpect(status().isOk())
                    .andExpect(header().string("Content-Type", org.hamcrest.Matchers.containsString("text/csv")))
                    .andExpect(header().string("Content-Disposition", org.hamcrest.Matchers.containsString("attachment")));
        }

        @Test
        @DisplayName("GET /api/agency/me/data-export — 인증 없음 401")
        void exportData_noAuth_401() throws Exception {
            mockMvc.perform(get("/api/agency/me/data-export"))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("POST /api/agency/me/data-import — 성공 200 OK")
        void importData_success_200() throws Exception {
            given(agencyDataTransferService.importEngineerRoster(any(), any()))
                    .willReturn(new com.careflow.agency.dto.response.AgencyDataImportResponse(2, 0, java.util.List.of()));

            org.springframework.mock.web.MockMultipartFile file = new org.springframework.mock.web.MockMultipartFile(
                    "file", "roster.csv", "text/csv", "name,email,phone\n홍길동,hong@test.com,01011112222\n".getBytes());

            mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart("/api/agency/me/data-import")
                            .file(file)
                            .with(user(agencyUser())))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.successCount").value(2))
                    .andExpect(jsonPath("$.failCount").value(0));
        }

        @Test
        @DisplayName("POST /api/agency/me/data-import — 대표 담당자 아님 401")
        void importData_notRepresentative_401() throws Exception {
            willThrow(new IllegalAccessException("대표 담당자만 기사 로스터를 일괄 등록할 수 있습니다."))
                    .given(agencyDataTransferService).importEngineerRoster(any(), any());

            org.springframework.mock.web.MockMultipartFile file = new org.springframework.mock.web.MockMultipartFile(
                    "file", "roster.csv", "text/csv", "name,email,phone\n".getBytes());

            mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart("/api/agency/me/data-import")
                            .file(file)
                            .with(user(agencyUser())))
                    .andExpect(status().isUnauthorized());
        }
    }
}
