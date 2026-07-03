package com.careflow.agency.controller;

import com.careflow.agency.dto.request.AgencySettlementSearchRequest;
import com.careflow.agency.dto.response.AgencySettlementListResponse;
import com.careflow.agency.service.AgencySettlementService;
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
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.mapping.JpaMetamodelMappingContext;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.anonymous;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(AgencySettlementController.class)
@Import({SecurityConfig.class, PasswordEncoderConfig.class})
@DisplayName("AgencySettlementController 테스트")
class AgencySettlementControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean private AgencySettlementService agencySettlementService;
    @MockitoBean private JwtProvider jwtProvider;
    @MockitoBean private JpaMetamodelMappingContext jpaMetamodelMappingContext;
    @MockitoBean private CustomOAuth2UserService customOAuth2UserService;
    @MockitoBean private OAuth2LoginSuccessHandler oAuth2LoginSuccessHandler;
    @MockitoBean private ClientRegistrationRepository clientRegistrationRepository;
    @MockitoBean private StringRedisTemplate stringRedisTemplate;

    private static final Long AGENCY_USER_ID = 1L;
    private static final Long AGENCY_ID = 100L;

    @BeforeEach
    void setUpAuth() {
        CustomUserDetails userDetails = new CustomUserDetails(
                AGENCY_USER_ID, "agency@test.com", "pw", "AGENCY", AGENCY_ID);
        UsernamePasswordAuthenticationToken auth =
                new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities());
        SecurityContextHolder.getContext().setAuthentication(auth);
    }

    private AgencySettlementListResponse sampleResponse() {
        AgencySettlementListResponse.Stats stats =
                new AgencySettlementListResponse.Stats(
                        125680000L, 98420000L, 22380000L, 4880000L, 1248L, 105L, 13680000L);
        AgencySettlementListResponse.SettlementSummary summary =
                new AgencySettlementListResponse.SettlementSummary(
                        1L, "ENGINEER", 123L, "김현수", "010-1234-5678",
                        "퀵케어 서비스", "2024-06-01", "2024-06-30",
                        1, 88000, BigDecimal.valueOf(10), 8800,
                        BigDecimal.valueOf(10), 8800, 70400,
                        "PAID", LocalDateTime.of(2024, 6, 18, 15, 30),
                        null, null);
        return new AgencySettlementListResponse(stats, List.of(summary), 1248L, 125, 0, 10);
    }

    @Nested
    @DisplayName("GET /api/agency/settlements — 정산 목록 조회")
    class GetSettlements {

        @Test
        @DisplayName("TC-C-1: 인증된 AGENCY — 200 OK, 응답 구조 검증")
        void success_200_returnsSettlementList() throws Exception {
            given(agencySettlementService.getSettlements(any(), any(), any()))
                    .willReturn(sampleResponse());

            mockMvc.perform(get("/api/agency/settlements"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.stats.thisMonthGrossAmount").value(125680000))
                    .andExpect(jsonPath("$.stats.paidAmount").value(98420000))
                    .andExpect(jsonPath("$.stats.thisMonthCount").value(1248))
                    .andExpect(jsonPath("$.content[0].settlementId").value(1))
                    .andExpect(jsonPath("$.content[0].engineerName").value("김현수"))
                    .andExpect(jsonPath("$.content[0].status").value("PAID"))
                    .andExpect(jsonPath("$.content[0].type").value("ENGINEER"))
                    .andExpect(jsonPath("$.content[0].periodStart").value("2024-06-01"))
                    .andExpect(jsonPath("$.content[0].periodEnd").value("2024-06-30"))
                    .andExpect(jsonPath("$.totalElements").value(1248))
                    .andExpect(jsonPath("$.totalPages").value(125))
                    .andExpect(jsonPath("$.currentPage").value(0));
        }

        @Test
        @DisplayName("TC-C-2: 인증 없음 — 401")
        void fail_anonymous_returns401() throws Exception {
            mockMvc.perform(get("/api/agency/settlements").with(anonymous()))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("TC-C-3: page/size 미전달 시 기본값(page=0, size=10)으로 Service 호출")
        void success_defaultPageable() throws Exception {
            given(agencySettlementService.getSettlements(any(), any(), any()))
                    .willReturn(sampleResponse());

            mockMvc.perform(get("/api/agency/settlements"))
                    .andExpect(status().isOk());

            verify(agencySettlementService).getSettlements(any(), any(), eq(PageRequest.of(0, 10)));
        }

        @Test
        @DisplayName("TC-C-4: 필터 쿼리 파라미터 없이 호출해도 정상 동작")
        void success_noFilterParams() throws Exception {
            given(agencySettlementService.getSettlements(any(), any(), any()))
                    .willReturn(sampleResponse());

            mockMvc.perform(get("/api/agency/settlements?page=0&size=10"))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("TC-C-5: status/keyword/dateFrom/dateTo 쿼리 파라미터가 Service 필터로 정확히 전달된다")
        void success_filterQueryParamsPropagated() throws Exception {
            given(agencySettlementService.getSettlements(any(), any(), any()))
                    .willReturn(sampleResponse());

            mockMvc.perform(get("/api/agency/settlements")
                            .param("status", "PAID")
                            .param("keyword", "김현수")
                            .param("dateFrom", "2024-06-01")
                            .param("dateTo", "2024-06-30"))
                    .andExpect(status().isOk());

            ArgumentCaptor<AgencySettlementSearchRequest> captor =
                    ArgumentCaptor.forClass(AgencySettlementSearchRequest.class);
            verify(agencySettlementService).getSettlements(any(), captor.capture(), any());

            AgencySettlementSearchRequest filter = captor.getValue();
            assertThat(filter.getStatus()).isEqualTo("PAID");
            assertThat(filter.getKeyword()).isEqualTo("김현수");
            assertThat(filter.getDateFrom()).isEqualTo("2024-06-01");
            assertThat(filter.getDateTo()).isEqualTo("2024-06-30");
        }
    }
}
