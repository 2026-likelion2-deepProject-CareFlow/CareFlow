package com.careflow.agency.controller;

import com.careflow.agency.dto.response.*;
import com.careflow.agency.service.AgencyStatisticsService;
import com.careflow.auth.security.CustomOAuth2UserService;
import com.careflow.auth.security.CustomUserDetails;
import com.careflow.auth.security.JwtProvider;
import com.careflow.auth.security.OAuth2LoginSuccessHandler;
import com.careflow.common.config.PasswordEncoderConfig;
import com.careflow.common.config.SecurityConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.mapping.JpaMetamodelMappingContext;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(AgencyStatisticsController.class)
@Import({SecurityConfig.class, PasswordEncoderConfig.class})
@DisplayName("AgencyStatisticsController 단위 테스트")
class AgencyStatisticsControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AgencyStatisticsService statisticsService;
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

    // AGENCY 역할 CustomUserDetails 픽스처
    private CustomUserDetails agencyUser() {
        return new CustomUserDetails(1L, "agency@test.com", "pw", "AGENCY", 10L);
    }

    // CUSTOMER 역할 픽스처 (권한 없음 케이스용)
    private CustomUserDetails customerUser() {
        return new CustomUserDetails(2L, "customer@test.com", "pw", "CUSTOMER", null);
    }

    // ─────────────────────────────────────────────
    // /summary
    // ─────────────────────────────────────────────
    @Nested
    @DisplayName("GET /api/agency/statistics/summary")
    class Summary {

        @Test
        @DisplayName("성공: AGENCY 인증 + 유효 날짜 → 200 + 응답 JSON 필드 존재")
        void success() throws Exception {
            AgencyStatisticsSummaryResponse stub = new AgencyStatisticsSummaryResponse(
                    100, 80, 80.0, 2.4, 4.8, 5000000L, 10.0, 8.0, 0.2, 12.0);
            given(statisticsService.getSummary(eq(10L), any())).willReturn(stub);

            mockMvc.perform(get("/api/agency/statistics/summary")
                            .param("dateFrom", "2024-06-01")
                            .param("dateTo", "2024-06-18")
                            .with(user(agencyUser())))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.totalReceiptCount").value(100))
                    .andExpect(jsonPath("$.completedCount").value(80))
                    .andExpect(jsonPath("$.avgRating").value(4.8));
        }

        @Test
        @DisplayName("실패: 비인증 요청 → 401")
        void unauthenticated() throws Exception {
            mockMvc.perform(get("/api/agency/statistics/summary")
                            .param("dateFrom", "2024-06-01")
                            .param("dateTo", "2024-06-18"))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("실패: dateFrom 누락 → 400")
        void missingDateFrom() throws Exception {
            mockMvc.perform(get("/api/agency/statistics/summary")
                            .param("dateTo", "2024-06-18")
                            .with(user(agencyUser())))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("실패: AGENCY 아닌 역할(CUSTOMER) → 403 (@PreAuthorize(\"hasRole('AGENCY')\") 매핑)")
        void nonAgencyRole() throws Exception {
            mockMvc.perform(get("/api/agency/statistics/summary")
                            .param("dateFrom", "2024-06-01")
                            .param("dateTo", "2024-06-18")
                            .with(user(customerUser())))
                    .andExpect(status().isForbidden());
        }
    }

    // ─────────────────────────────────────────────
    // /daily-trend
    // ─────────────────────────────────────────────
    @Nested
    @DisplayName("GET /api/agency/statistics/daily-trend")
    class DailyTrend {

        @Test
        @DisplayName("성공: AGENCY 인증 + 유효 날짜 → 200 + JSON 배열")
        void success() throws Exception {
            given(statisticsService.getDailyTrend(eq(10L), any()))
                    .willReturn(List.of(new AgencyStatisticsDailyTrendResponse("2024-06-01", 38, 32)));

            mockMvc.perform(get("/api/agency/statistics/daily-trend")
                            .param("dateFrom", "2024-06-01")
                            .param("dateTo", "2024-06-18")
                            .with(user(agencyUser())))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$[0].date").value("2024-06-01"))
                    .andExpect(jsonPath("$[0].receiptCount").value(38));
        }

        @Test
        @DisplayName("실패: 비인증 → 401")
        void unauthenticated() throws Exception {
            mockMvc.perform(get("/api/agency/statistics/daily-trend")
                            .param("dateFrom", "2024-06-01")
                            .param("dateTo", "2024-06-18"))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("실패: dateTo 누락 → 400")
        void missingDateTo() throws Exception {
            mockMvc.perform(get("/api/agency/statistics/daily-trend")
                            .param("dateFrom", "2024-06-01")
                            .with(user(agencyUser())))
                    .andExpect(status().isBadRequest());
        }
    }

    // ─────────────────────────────────────────────
    // /hourly
    // ─────────────────────────────────────────────
    @Nested
    @DisplayName("GET /api/agency/statistics/hourly")
    class Hourly {

        @Test
        @DisplayName("성공: AGENCY 인증 + 유효 날짜 → 200 + 8개 슬롯")
        void success() throws Exception {
            List<AgencyStatisticsHourlyResponse> stub = List.of(
                    new AgencyStatisticsHourlyResponse("00-03", 5),
                    new AgencyStatisticsHourlyResponse("03-06", 12),
                    new AgencyStatisticsHourlyResponse("06-09", 65),
                    new AgencyStatisticsHourlyResponse("09-12", 98),
                    new AgencyStatisticsHourlyResponse("12-15", 110),
                    new AgencyStatisticsHourlyResponse("15-18", 85),
                    new AgencyStatisticsHourlyResponse("18-21", 45),
                    new AgencyStatisticsHourlyResponse("21-24", 8)
            );
            given(statisticsService.getHourly(eq(10L), any())).willReturn(stub);

            mockMvc.perform(get("/api/agency/statistics/hourly")
                            .param("dateFrom", "2024-06-01")
                            .param("dateTo", "2024-06-18")
                            .with(user(agencyUser())))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.length()").value(8));
        }

        @Test
        @DisplayName("실패: 비인증 → 401")
        void unauthenticated() throws Exception {
            mockMvc.perform(get("/api/agency/statistics/hourly")
                            .param("dateFrom", "2024-06-01")
                            .param("dateTo", "2024-06-18"))
                    .andExpect(status().isUnauthorized());
        }
    }

    // ─────────────────────────────────────────────
    // /category-dist
    // ─────────────────────────────────────────────
    @Nested
    @DisplayName("GET /api/agency/statistics/category-dist")
    class CategoryDist {

        @Test
        @DisplayName("성공: AGENCY 인증 + 유효 날짜 → 200 + JSON 배열")
        void success() throws Exception {
            given(statisticsService.getCategoryDist(eq(10L), any()))
                    .willReturn(List.of(new AgencyStatisticsCategoryDistResponse("에어컨", 50, 50.0)));

            mockMvc.perform(get("/api/agency/statistics/category-dist")
                            .param("dateFrom", "2024-06-01")
                            .param("dateTo", "2024-06-18")
                            .with(user(agencyUser())))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$[0].categoryName").value("에어컨"));
        }

        @Test
        @DisplayName("실패: 비인증 → 401")
        void unauthenticated() throws Exception {
            mockMvc.perform(get("/api/agency/statistics/category-dist")
                            .param("dateFrom", "2024-06-01")
                            .param("dateTo", "2024-06-18"))
                    .andExpect(status().isUnauthorized());
        }
    }

    // ─────────────────────────────────────────────
    // /status-count
    // ─────────────────────────────────────────────
    @Nested
    @DisplayName("GET /api/agency/statistics/status-count")
    class StatusCount {

        @Test
        @DisplayName("성공: AGENCY 인증 + 유효 날짜 → 200 + JSON 배열")
        void success() throws Exception {
            given(statisticsService.getStatusCount(eq(10L), any()))
                    .willReturn(List.of(new AgencyStatisticsStatusCountResponse("PENDING", 10, 100.0)));

            mockMvc.perform(get("/api/agency/statistics/status-count")
                            .param("dateFrom", "2024-06-01")
                            .param("dateTo", "2024-06-18")
                            .with(user(agencyUser())))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$[0].status").value("PENDING"));
        }

        @Test
        @DisplayName("실패: 비인증 → 401")
        void unauthenticated() throws Exception {
            mockMvc.perform(get("/api/agency/statistics/status-count")
                            .param("dateFrom", "2024-06-01")
                            .param("dateTo", "2024-06-18"))
                    .andExpect(status().isUnauthorized());
        }
    }

    // ─────────────────────────────────────────────
    // /engineer-top5
    // ─────────────────────────────────────────────
    @Nested
    @DisplayName("GET /api/agency/statistics/engineer-top5")
    class EngineerTop5 {

        @Test
        @DisplayName("성공: AGENCY 인증 + 유효 날짜 → 200 + JSON 배열")
        void success() throws Exception {
            given(statisticsService.getEngineerTop5(eq(10L), any()))
                    .willReturn(List.of(new AgencyStatisticsEngineerTop5Response(1, "김현수", 128)));

            mockMvc.perform(get("/api/agency/statistics/engineer-top5")
                            .param("dateFrom", "2024-06-01")
                            .param("dateTo", "2024-06-18")
                            .with(user(agencyUser())))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$[0].rank").value(1))
                    .andExpect(jsonPath("$[0].engineerName").value("김현수"));
        }

        @Test
        @DisplayName("실패: 비인증 → 401")
        void unauthenticated() throws Exception {
            mockMvc.perform(get("/api/agency/statistics/engineer-top5")
                            .param("dateFrom", "2024-06-01")
                            .param("dateTo", "2024-06-18"))
                    .andExpect(status().isUnauthorized());
        }
    }

    // ─────────────────────────────────────────────
    // /monthly-summary
    // ─────────────────────────────────────────────
    @Nested
    @DisplayName("GET /api/agency/statistics/monthly-summary")
    class MonthlySummary {

        @Test
        @DisplayName("성공: AGENCY 인증 → 200 + 6개 필드 존재")
        void success() throws Exception {
            AgencyStatisticsMonthlySummaryResponse stub = new AgencyStatisticsMonthlySummaryResponse(
                    "금요일", 212, "10-11시", 186, "김현수", 4.9);
            given(statisticsService.getMonthlySummary(10L)).willReturn(stub);

            mockMvc.perform(get("/api/agency/statistics/monthly-summary")
                            .with(user(agencyUser())))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.topReceiptDayOfWeek").value("금요일"))
                    .andExpect(jsonPath("$.topReceiptDayCount").value(212))
                    .andExpect(jsonPath("$.topReceiptHour").value("10-11시"))
                    .andExpect(jsonPath("$.topReceiptHourCount").value(186))
                    .andExpect(jsonPath("$.topRatingEngineerName").value("김현수"))
                    .andExpect(jsonPath("$.topRatingEngineerScore").value(4.9));
        }

        @Test
        @DisplayName("실패: 비인증 → 401")
        void unauthenticated() throws Exception {
            mockMvc.perform(get("/api/agency/statistics/monthly-summary"))
                    .andExpect(status().isUnauthorized());
        }
    }
}
