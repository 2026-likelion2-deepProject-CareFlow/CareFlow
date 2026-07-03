package com.careflow.admin.controller;

import com.careflow.admin.dto.response.AdminAsRequestListResponse;
import com.careflow.admin.service.AdminAsRequestService;
import com.careflow.auth.security.CustomOAuth2UserService;
import com.careflow.auth.security.CustomUserDetails;
import com.careflow.auth.security.JwtProvider;
import com.careflow.auth.security.OAuth2LoginSuccessHandler;
import com.careflow.common.config.PasswordEncoderConfig;
import com.careflow.common.config.SecurityConfig;
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
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.util.Collections;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AdminAsRequestController.class)
@Import({SecurityConfig.class, PasswordEncoderConfig.class})
@DisplayName("AdminAsRequestController 단위 테스트")
class AdminAsRequestControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;

    @MockitoBean private AdminAsRequestService adminAsRequestService;
    @MockitoBean private JwtProvider jwtProvider;
    @MockitoBean private StringRedisTemplate stringRedisTemplate; // Redis Mock 처리
    @MockitoBean private JpaMetamodelMappingContext jpaMetamodelMappingContext;
    @MockitoBean private CustomOAuth2UserService customOAuth2UserService;
    @MockitoBean private OAuth2LoginSuccessHandler oAuth2LoginSuccessHandler;
    @MockitoBean private ClientRegistrationRepository clientRegistrationRepository;

    private RequestPostProcessor adminAuth;
    private RequestPostProcessor agencyAuth;

    @BeforeEach
    void setUp() {
        // ADMIN 권한과 AGENCY 권한 분리 셋업
        adminAuth = buildAuth(1L, "admin@careflow.com", "ADMIN");
        agencyAuth = buildAuth(2L, "agency@careflow.com", "AGENCY");
    }

    private RequestPostProcessor buildAuth(Long userId, String email, String role) {
        CustomUserDetails userDetails = new CustomUserDetails(userId, email, "", role, null);
        return SecurityMockMvcRequestPostProcessors.authentication(
                new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities())
        );
    }

    @Nested
    @DisplayName("GET /api/admin/as-requests/stats - 실시간 통계 조회")
    class GetRealTimeStats {
        @Test
        @DisplayName("성공: ADMIN 권한 접근 - 200 OK")
        void success_200() throws Exception {
            // given
            given(adminAsRequestService.getRealTimeStats()).willReturn(Map.of("PENDING", 10L, "COMPLETED", 5L));

            // when & then
            mockMvc.perform(get("/api/admin/as-requests/stats").with(adminAuth))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.PENDING").value(10))
                    .andExpect(jsonPath("$.COMPLETED").value(5));
        }

        @Test
        @DisplayName("실패: AGENCY 권한 접근 - 403/401 에러")
        void fail_notAdmin_403() throws Exception {
            mockMvc.perform(get("/api/admin/as-requests/stats").with(agencyAuth))
                    .andExpect(status().isForbidden()); // SecurityConfig에 의해 필터링됨
        }
    }

    @Nested
    @DisplayName("GET /api/admin/as-requests - 전체 A/S 처리 내역 조회")
    class GetAsRequests {
        @Test
        @DisplayName("성공: 파라미터가 모두 있는 경우 정상 조회 - 200 OK")
        void success_withParams_200() throws Exception {
            // given
            AdminAsRequestListResponse mockResponse = new AdminAsRequestListResponse(
                    Collections.emptyMap(), Collections.emptyList(), 0, 0, 0, 10
            );
            given(adminAsRequestService.searchAsRequests(eq("PENDING"), eq("서울"), eq("2026-06-01"), eq("2026-06-30"), any()))
                    .willReturn(mockResponse);

            // when & then
            mockMvc.perform(get("/api/admin/as-requests")
                            .param("status", "PENDING")
                            .param("region", "서울")
                            .param("from", "2026-06-01")
                            .param("to", "2026-06-30")
                            .param("page", "0")
                            .param("size", "10")
                            .with(adminAuth))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("성공: 파라미터가 생략된 경우 (전체 조회) - 200 OK")
        void success_withoutParams_200() throws Exception {
            given(adminAsRequestService.searchAsRequests(eq(null), eq(null), eq(null), eq(null), any()))
                    .willReturn(new AdminAsRequestListResponse(Collections.emptyMap(), Collections.emptyList(), 0, 0, 0, 10));

            mockMvc.perform(get("/api/admin/as-requests").with(adminAuth))
                    .andExpect(status().isOk());
        }
    }
}