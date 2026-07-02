package com.careflow.agency.controller;

import com.careflow.agency.service.AgencyEngineerService;
import com.careflow.as_request.dto.EngineerTaskScheduleResponse;
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
import java.util.NoSuchElementException;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.given;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.anonymous;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(AgencyEngineerController.class)
@Import({SecurityConfig.class, PasswordEncoderConfig.class})
@DisplayName("AgencyEngineerTaskSchedule 컨트롤러 단위 테스트")
class AgencyEngineerTaskScheduleControllerTest {

    @Autowired private MockMvc mockMvc;

    @MockitoBean private AgencyEngineerService agencyEngineerService;
    @MockitoBean private JwtProvider jwtProvider;
    @MockitoBean private JpaMetamodelMappingContext jpaMetamodelMappingContext;
    @MockitoBean private CustomOAuth2UserService customOAuth2UserService;
    @MockitoBean private OAuth2LoginSuccessHandler oAuth2LoginSuccessHandler;
    @MockitoBean private ClientRegistrationRepository clientRegistrationRepository;
    @MockitoBean private org.springframework.data.redis.core.StringRedisTemplate stringRedisTemplate;

    private static final Long AGENCY_USER_ID   = 1L;
    private static final Long ENGINEER_USER_ID = 10L;

    @BeforeEach
    void setUpAuth() {
        CustomUserDetails userDetails = new CustomUserDetails(
                AGENCY_USER_ID, "agency@test.com", "pw", "AGENCY", null);
        UsernamePasswordAuthenticationToken auth =
                new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities());
        SecurityContextHolder.getContext().setAuthentication(auth);
    }

    // ─────────────────────────────────────────────
    //  GET /api/agency/engineers/{id}/schedule?date=
    // ─────────────────────────────────────────────
    @Nested
    @DisplayName("GET /api/agency/engineers/{engineerUserId}/schedule — 소속 기사 작업 일정 조회")
    class GetAgencyEngineerTaskSchedule {

        @Test
        @DisplayName("성공: 해당 날짜 배정 2건 — 200 OK + 배열 반환")
        void success_twoTasks_200() throws Exception {
            given(agencyEngineerService.getAgencyEngineerTaskSchedule(
                    eq(AGENCY_USER_ID), eq(ENGINEER_USER_ID), eq(LocalDate.of(2026, 6, 1))))
                    .willReturn(List.of(
                            stubResponse(101L, "홍길동", "삼성", "냉방 불량"),
                            stubResponse(102L, "홍길동", "LG",  "전원 불량")));

            mockMvc.perform(get("/api/agency/engineers/{engineerUserId}/schedule", ENGINEER_USER_ID)
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
            given(agencyEngineerService.getAgencyEngineerTaskSchedule(any(), any(), any()))
                    .willReturn(List.of());

            mockMvc.perform(get("/api/agency/engineers/{engineerUserId}/schedule", ENGINEER_USER_ID)
                            .param("date", "2026-06-01"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.length()").value(0));
        }

        @Test
        @DisplayName("실패: date 파라미터 누락 — 400 Bad Request")
        void fail_missingDate_400() throws Exception {
            mockMvc.perform(get("/api/agency/engineers/{engineerUserId}/schedule", ENGINEER_USER_ID))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("실패: 타 대행사 기사 조회 시도 — 401 Unauthorized (IllegalAccessException)")
        void fail_otherAgencyEngineer_401() throws Exception {
            // GlobalExceptionHandler: IllegalAccessException → 401 UNAUTHORIZED
            given(agencyEngineerService.getAgencyEngineerTaskSchedule(any(), eq(20L), any()))
                    .willThrow(new IllegalAccessException("소속 대행사의 기사만 조회할 수 있습니다."));

            mockMvc.perform(get("/api/agency/engineers/20/schedule")
                            .param("date", "2026-06-01"))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("실패: 존재하지 않는 기사 — 404 Not Found")
        void fail_engineerNotFound_404() throws Exception {
            given(agencyEngineerService.getAgencyEngineerTaskSchedule(any(), eq(999L), any()))
                    .willThrow(new NoSuchElementException("해당 기사 정보가 존재하지 않습니다."));

            mockMvc.perform(get("/api/agency/engineers/999/schedule")
                            .param("date", "2026-06-01"))
                    .andExpect(status().isNotFound());
        }

        @Test
        @DisplayName("실패: 인증 없음 — 401 Unauthorized")
        void fail_noToken_401() throws Exception {
            mockMvc.perform(get("/api/agency/engineers/{engineerUserId}/schedule", ENGINEER_USER_ID)
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
