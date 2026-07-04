package com.careflow.agency.controller;

import com.careflow.agency.dto.request.AgencyFeeRateUpdateRequest;
import com.careflow.agency.dto.request.AgencyProfileUpdateRequest;
import com.careflow.agency.dto.response.AgencyFeeRateResponse;
import com.careflow.agency.dto.response.AgencyProfileResponse;
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
            AgencyProfileResponse resp = new AgencyProfileResponse(5L, "테스트대행사", "서울특별시 강남구 테헤란로 1");
            given(agenciesService.getProfile(eq(5L))).willReturn(resp);

            mockMvc.perform(get("/api/agency/me")
                            .with(user(agencyUser(1L, 5L))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.agencyName").value("테스트대행사"));
        }

        @Test
        @DisplayName("성공: staff(비대표) 계정도 agencyId 기준으로 동일하게 조회 — 200 OK")
        void getProfile_staff_200() throws Exception {
            AgencyProfileResponse resp = new AgencyProfileResponse(5L, "테스트대행사", "서울특별시 강남구 테헤란로 1");
            given(agenciesService.getProfile(eq(5L))).willReturn(resp);

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
            given(agenciesService.getProfile(eq(5L)))
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
            AgencyProfileUpdateRequest req = new AgencyProfileUpdateRequest("수정된대행사", "서울특별시 서초구 서초대로 1");
            AgencyProfileResponse resp = new AgencyProfileResponse(1L, "수정된대행사", "서울특별시 서초구 서초대로 1");

            given(agenciesService.updateProfile(eq(1L), any())).willReturn(resp);

            mockMvc.perform(put("/api/agency/me")
                            .with(user(agencyUser()))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(req)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.agencyName").value("수정된대행사"))
                    .andExpect(jsonPath("$.agencyAddress").value("서울특별시 서초구 서초대로 1"));
        }

        @Test
        @DisplayName("실패: 인증 토큰 없음 — 401 Unauthorized")
        void updateProfile_noAuth_401() throws Exception {
            AgencyProfileUpdateRequest req = new AgencyProfileUpdateRequest("수정된대행사", "서울 서초구");

            mockMvc.perform(put("/api/agency/me")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(req)))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("실패: AGENCY 역할 아님 — 403 Forbidden")
        @WithMockUser(authorities = "CUSTOMER")
        void updateProfile_wrongRole_403() throws Exception {
            AgencyProfileUpdateRequest req = new AgencyProfileUpdateRequest("수정된대행사", "서울 서초구");

            mockMvc.perform(put("/api/agency/me")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(req)))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("유효성 실패: agencyName 공백 — 400 Bad Request")
        void updateProfile_blankName_400() throws Exception {
            AgencyProfileUpdateRequest req = new AgencyProfileUpdateRequest("", "서울 서초구");

            mockMvc.perform(put("/api/agency/me")
                            .with(user(agencyUser()))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(req)))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("유효성 실패: agencyAddress 공백 — 400 Bad Request")
        void updateProfile_blankAddress_400() throws Exception {
            AgencyProfileUpdateRequest req = new AgencyProfileUpdateRequest("정상대행사", "");

            mockMvc.perform(put("/api/agency/me")
                            .with(user(agencyUser()))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(req)))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("유효성 실패: agencyAddress 255자 초과 — 400 Bad Request")
        void updateProfile_addressTooLong_400() throws Exception {
            AgencyProfileUpdateRequest req = new AgencyProfileUpdateRequest("정상대행사", "주".repeat(256));

            mockMvc.perform(put("/api/agency/me")
                            .with(user(agencyUser()))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(req)))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("실패: 대행사 정보 없음(서비스 NoSuchElementException) — 404 Not Found")
        void updateProfile_notFound_404() throws Exception {
            AgencyProfileUpdateRequest req = new AgencyProfileUpdateRequest("수정된대행사", "서울 서초구");

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
}
