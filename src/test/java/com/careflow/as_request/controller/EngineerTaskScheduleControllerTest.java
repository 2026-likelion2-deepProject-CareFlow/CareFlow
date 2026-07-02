package com.careflow.as_request.controller;

import com.careflow.as_request.dto.EngineerTaskScheduleResponse;
import com.careflow.as_request.service.AsRequestService;
import com.careflow.as_request.service.EngineerTaskScheduleService;
import com.careflow.auth.security.CustomOAuth2UserService;
import com.careflow.auth.security.CustomUserDetails;
import com.careflow.auth.security.JwtProvider;
import com.careflow.auth.security.OAuth2LoginSuccessHandler;
import com.careflow.common.config.PasswordEncoderConfig;
import com.careflow.common.config.SecurityConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.mapping.JpaMetamodelMappingContext;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.anonymous;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(EngineerTaskController.class)
@Import({SecurityConfig.class, PasswordEncoderConfig.class})
@DisplayName("EngineerTaskSchedule 컨트롤러 단위 테스트")
class EngineerTaskScheduleControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean private EngineerTaskScheduleService engineerTaskScheduleService;
    @MockitoBean private AsRequestService asRequestService;
    @MockitoBean private JwtProvider jwtProvider;
    @MockitoBean private JpaMetamodelMappingContext jpaMetamodelMappingContext;
    @MockitoBean private CustomOAuth2UserService customOAuth2UserService;
    @MockitoBean private OAuth2LoginSuccessHandler oAuth2LoginSuccessHandler;
    @MockitoBean private ClientRegistrationRepository clientRegistrationRepository;
    @MockitoBean private org.springframework.data.redis.core.StringRedisTemplate stringRedisTemplate;

    private static final Long ENGINEER_USER_ID = 10L;

    @BeforeEach
    void setUpAuth() {
        CustomUserDetails userDetails = new CustomUserDetails(
                ENGINEER_USER_ID, "engineer@test.com", "pw", "ENGINEER", null);
        UsernamePasswordAuthenticationToken auth =
                new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities());
        SecurityContextHolder.getContext().setAuthentication(auth);
    }

    // ─────────────────────────────────────────────
    //  GET /api/engineer/schedule?date=2026-06-01
    // ─────────────────────────────────────────────
    @Nested
    @DisplayName("GET /api/engineer/schedule — 기사 본인 작업 일정 조회")
    class GetTaskSchedule {

        @Test
        @DisplayName("성공: 해당 날짜 배정 2건 — 200 OK + 배열 반환")
        void success_twoTasks_200() throws Exception {
            given(engineerTaskScheduleService.getTaskSchedule(eq(ENGINEER_USER_ID), eq(LocalDate.of(2026, 6, 1))))
                    .willReturn(List.of(
                            stubResponse(101L, "홍길동", "삼성", "냉방 불량"),
                            stubResponse(102L, "이순신", "LG", "전원 불량")));

            mockMvc.perform(get("/api/engineer/schedule")
                            .param("date", "2026-06-01"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.length()").value(2))
                    .andExpect(jsonPath("$[0].requestId").value(101))
                    .andExpect(jsonPath("$[0].customerName").value("홍길동"))
                    .andExpect(jsonPath("$[0].applianceBrand").value("삼성"))
                    .andExpect(jsonPath("$[0].symptomName").value("냉방 불량"));
        }

        @Test
        @DisplayName("성공: 해당 날짜 배정 없음 — 200 OK + 빈 배열")
        void success_empty_200() throws Exception {
            given(engineerTaskScheduleService.getTaskSchedule(any(), any()))
                    .willReturn(List.of());

            mockMvc.perform(get("/api/engineer/schedule")
                            .param("date", "2026-06-01"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.length()").value(0));
        }

        @Test
        @DisplayName("실패: date 파라미터 누락 — 400 Bad Request")
        void fail_missingDate_400() throws Exception {
            mockMvc.perform(get("/api/engineer/schedule"))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("실패: 인증 없음 — 401 Unauthorized")
        void fail_noToken_401() throws Exception {
            // .with(anonymous()) 으로 @BeforeEach 에서 설정한 SecurityContext 를 익명으로 덮어씀
            mockMvc.perform(get("/api/engineer/schedule")
                            .with(anonymous())
                            .param("date", "2026-06-01"))
                    .andExpect(status().isUnauthorized());
        }
    }

    // ── 픽스처 헬퍼 ──────────────────────────────────────────────────

    private EngineerTaskScheduleResponse stubResponse(
            Long requestId, String customerName, String brand, String symptomName) {
        return EngineerTaskScheduleResponse.builder()
                .requestId(requestId)
                .scheduledDate(LocalDate.of(2026, 6, 1))
                .scheduledTime("10:00")
                .customerName(customerName)
                .customerPhone("010-1111-2222")
                .applianceBrand(brand)
                .applianceModelName("테스트모델")
                .symptomName(symptomName)
                .visitRegionName("강남구")
                .visitAddressDetail("테헤란로 123")
                .requestStatus("ACCEPTED")
                .assignmentStatus("ACCEPTED")
                .build();
    }
}
