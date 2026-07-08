package com.careflow.engineer.controller;

import com.careflow.auth.security.CustomOAuth2UserService;
import com.careflow.auth.security.CustomUserDetails;
import com.careflow.auth.security.JwtProvider;
import com.careflow.auth.security.OAuth2LoginSuccessHandler;
import com.careflow.common.config.PasswordEncoderConfig;
import com.careflow.common.config.SecurityConfig;
import com.careflow.engineer.dto.EngineerDashboardResponse;
import com.careflow.settlement.dto.EngineerSettlementSummaryResponse;
import com.careflow.engineer.service.EngineerDashboardService;
import com.careflow.engineer.service.EngineerSettlementReportCsvGenerator;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.mapping.JpaMetamodelMappingContext;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.time.LocalDate;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(EngineerDashboardController.class)
@Import({SecurityConfig.class, PasswordEncoderConfig.class})
@DisplayName("EngineerDashboardController 단위 테스트")
class EngineerDashboardControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;

    @MockitoBean private EngineerDashboardService engineerDashboardService;
    @MockitoBean private EngineerSettlementReportCsvGenerator settlementReportCsvGenerator;
    @MockitoBean private JwtProvider jwtProvider;
    @MockitoBean private StringRedisTemplate stringRedisTemplate;
    @MockitoBean private JpaMetamodelMappingContext jpaMetamodelMappingContext;
    @MockitoBean private CustomOAuth2UserService customOAuth2UserService;
    @MockitoBean private OAuth2LoginSuccessHandler oAuth2LoginSuccessHandler;
    @MockitoBean private ClientRegistrationRepository clientRegistrationRepository;

    private RequestPostProcessor engineerAuth;
    private RequestPostProcessor customerAuth;

    @BeforeEach
    void setUp() {
        engineerAuth = buildAuth(1L, "engineer@test.com", "ENGINEER");
        customerAuth = buildAuth(2L, "customer@test.com", "CUSTOMER");
    }

    private RequestPostProcessor buildAuth(Long userId, String email, String role) {
        CustomUserDetails userDetails = new CustomUserDetails(userId, email, "", role, 100L);
        return SecurityMockMvcRequestPostProcessors.authentication(
                new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities())
        );
    }

    @Nested
    @DisplayName("GET /api/engineer/dashboard - 대시보드 조회")
    class GetDashboard {
        @Test
        @DisplayName("성공: ENGINEER 권한 접근 - 200 OK")
        void success_200() throws Exception {
            given(engineerDashboardService.getDashboardData(any())).willReturn(EngineerDashboardResponse.builder().build());

            mockMvc.perform(get("/api/engineer/dashboard").with(engineerAuth))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("실패: CUSTOMER 권한 접근 - 403 Forbidden")
        void fail_notEngineer_403() throws Exception {
            mockMvc.perform(get("/api/engineer/dashboard").with(customerAuth))
                    .andExpect(status().isForbidden());
        }
    }

    @Nested
    @DisplayName("GET /api/engineer/settlements/summary - 실적/정산 요약 조회")
    class GetSettlementSummary {
        @Test
        @DisplayName("성공: 날짜 및 필터 파라미터 유무와 관계없이 정상 조회 - 200 OK")
        void success_200() throws Exception {
            // 🌟 수정: 파라미터가 5개(engineerId, dateFrom, dateTo, brand, status)로 늘어났으므로 any() 5개 적용
            given(engineerDashboardService.getSettlementSummary(any(), any(), any(), any(), any()))
                    .willReturn(EngineerSettlementSummaryResponse.builder().build());

            // 1. 파라미터 없이 호출
            mockMvc.perform(get("/api/engineer/settlements/summary").with(engineerAuth))
                    .andExpect(status().isOk());

            // 2. 파라미터 모두 포함하여 호출 (새로 추가된 brand, status 파라미터 포함)
            mockMvc.perform(get("/api/engineer/settlements/summary")
                            .param("dateFrom", LocalDate.now().minusDays(30).toString())
                            .param("dateTo", LocalDate.now().toString())
                            .param("brand", "삼성")
                            .param("status", "NORMAL")
                            .with(engineerAuth))
                    .andExpect(status().isOk());
        }
    }
}