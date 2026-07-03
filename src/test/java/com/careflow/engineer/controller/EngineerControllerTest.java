package com.careflow.engineer.controller;

import com.careflow.auth.security.CustomOAuth2UserService;
import com.careflow.auth.security.JwtProvider;
import com.careflow.auth.security.OAuth2LoginSuccessHandler;
import com.careflow.common.config.PasswordEncoderConfig;
import com.careflow.common.config.SecurityConfig;
import com.careflow.engineer.dto.EngineerAccountRequest;
import com.careflow.engineer.service.EngineerService;
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
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.NoSuchElementException;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(EngineerController.class)
@Import({SecurityConfig.class, PasswordEncoderConfig.class})
@DisplayName("EngineerController 테스트")
class EngineerControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private EngineerService engineerService;
    @MockitoBean
    private JwtProvider jwtProvider;
    @MockitoBean
    private JpaMetamodelMappingContext jpaMetamodelMappingContext;
    @MockitoBean
    private CustomOAuth2UserService customOAuth2UserService;
    @MockitoBean
    private OAuth2LoginSuccessHandler oAuth2LoginSuccessHandler;
    @MockitoBean
    private ClientRegistrationRepository clientRegistrationRepository;
    @MockitoBean
    private org.springframework.data.redis.core.StringRedisTemplate stringRedisTemplate;

    private EngineerAccountRequest validRequest() {
        return new EngineerAccountRequest(
                "홍길동",
                "engineer@test.com",
                "pass1234",
                "010-1234-5678",
                1,
                "상세주소 101호",
                "BIZ-001"
        );
    }

    // ─────────────────────────────────────────────
    //  POST /api/engineers/signup
    // ─────────────────────────────────────────────
    @Nested
    @DisplayName("POST /api/engineer/signup — 수리기사 계정 생성 요청")
    class EngineerSignup {

        @Test
        @DisplayName("성공: 유효한 요청 — 201 Created 반환")
        void signup_success_201() throws Exception {
            given(engineerService.requestEngineerAccount(any())).willReturn(1L);

            mockMvc.perform(post("/api/engineer/signup")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(validRequest())))
                    .andExpect(status().isCreated());
        }

        @Test
        @DisplayName("실패: 존재하지 않는 사업자 등록번호 — 404 Not Found 반환")
        void signup_notFoundAgency_404() throws Exception {
            given(engineerService.requestEngineerAccount(any()))
                    .willThrow(new NoSuchElementException("입력받은 대행사 정보가 존재하지 않습니다."));

            mockMvc.perform(post("/api/engineer/signup")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(validRequest())))
                    .andExpect(status().isNotFound());
        }

        @Test
        @DisplayName("실패: 승인 대기중(PENDING) 대행사 — 403 Forbidden 반환")
        void signup_pendingAgency_403() throws Exception {
            given(engineerService.requestEngineerAccount(any()))
                    .willThrow(new IllegalStateException("아직 등록 대기중인 대행사입니다. 등록이 승인된 이후 다시 요청해주세요"));

            mockMvc.perform(post("/api/engineer/signup")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(validRequest())))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("실패: 승인 거부(REJECTED) 대행사 — 403 Forbidden 반환")
        void signup_rejectedAgency_403() throws Exception {
            given(engineerService.requestEngineerAccount(any()))
                    .willThrow(new IllegalStateException("등록이 거부된 대행사입니다. 관리자에게 문의해주세요."));

            mockMvc.perform(post("/api/engineer/signup")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(validRequest())))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("실패: 이미 가입된 이메일 — 400 Bad Request 반환")
        void signup_duplicateEmail_400() throws Exception {
            given(engineerService.requestEngineerAccount(any()))
                    .willThrow(new IllegalArgumentException("이미 가입된 이메일입니다."));

            mockMvc.perform(post("/api/engineer/signup")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(validRequest())))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("유효성 실패: 이름 누락 — 400 Bad Request 반환")
        void signup_missingName_400() throws Exception {
            EngineerAccountRequest invalid = new EngineerAccountRequest(
                    "",
                    "engineer@test.com",
                    "pass1234",
                    "010-1234-5678",
                    1,
                    "상세주소 101호",
                    "BIZ-001"
            );

            mockMvc.perform(post("/api/engineer/signup")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(invalid)))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("유효성 실패: 잘못된 이메일 형식 — 400 Bad Request 반환")
        void signup_invalidEmail_400() throws Exception {
            EngineerAccountRequest invalid = new EngineerAccountRequest(
                    "홍길동",
                    "not-an-email",
                    "pass1234",
                    "010-1234-5678",
                    1,
                    "상세주소 101호",
                    "BIZ-001"
            );

            mockMvc.perform(post("/api/engineer/signup")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(invalid)))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("유효성 실패: 잘못된 전화번호 형식 — 400 Bad Request 반환")
        void signup_invalidPhone_400() throws Exception {
            EngineerAccountRequest invalid = new EngineerAccountRequest(
                    "홍길동",
                    "engineer@test.com",
                    "pass1234",
                    "01012345678999",
                    1,
                    "상세주소 101호",
                    "BIZ-001"
            );

            mockMvc.perform(post("/api/engineer/signup")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(invalid)))
                    .andExpect(status().isBadRequest());
        }
    }
}
