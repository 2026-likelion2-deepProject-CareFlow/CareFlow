package com.careflow.admin.controller;

import com.careflow.admin.dto.response.AdminSettlementDetailResponse;
import com.careflow.admin.dto.response.AdminSettlementSummaryResponse;
import com.careflow.admin.service.AdminSettlementService;
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
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.BDDMockito.given;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.anonymous;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(AdminSettlementController.class)
@Import({SecurityConfig.class, PasswordEncoderConfig.class})
@DisplayName("AdminSettlementController 테스트")
class AdminSettlementControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean private AdminSettlementService adminSettlementService;
    @MockitoBean private JwtProvider jwtProvider;
    @MockitoBean private JpaMetamodelMappingContext jpaMetamodelMappingContext;
    @MockitoBean private CustomOAuth2UserService customOAuth2UserService;
    @MockitoBean private OAuth2LoginSuccessHandler oAuth2LoginSuccessHandler;
    @MockitoBean private ClientRegistrationRepository clientRegistrationRepository;
    @MockitoBean private StringRedisTemplate stringRedisTemplate;

    private static final Long AGENCY_ID = 100L;

    private void authenticateAs(String role) {
        CustomUserDetails userDetails = new CustomUserDetails(1L, "user@test.com", "pw", role, null);
        UsernamePasswordAuthenticationToken auth =
                new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities());
        SecurityContextHolder.getContext().setAuthentication(auth);
    }

    private AdminSettlementSummaryResponse sampleSummary() {
        AdminSettlementSummaryResponse.Summary summary =
                new AdminSettlementSummaryResponse.Summary(11130000L, 1113000L, 10017000L, 3L);
        AdminSettlementSummaryResponse.AgencySettlementItem item =
                new AdminSettlementSummaryResponse.AgencySettlementItem(
                        1L, "한국서비스대행사", 5L, 5200000L, 520000L, 4680000L, "PENDING", "PENDING", null);
        return new AdminSettlementSummaryResponse(summary, List.of(item));
    }

    // ── ⑦ GET /api/admin/settlements ───────────────────────────────

    @Nested
    @DisplayName("GET /api/admin/settlements — 월별 전체 대행사 정산 현황")
    class GetMonthlySummary {

        @Test
        @DisplayName("TC-C-1: 인증된 ADMIN — 200 OK, 응답 구조 검증")
        void success_200() throws Exception {
            authenticateAs("ADMIN");
            given(adminSettlementService.getMonthlySummary(any(), anyInt(), anyInt()))
                    .willReturn(sampleSummary());

            mockMvc.perform(get("/api/admin/settlements?year=2026&month=6"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.summary.totalRevenue").value(11130000))
                    .andExpect(jsonPath("$.summary.pendingCount").value(3))
                    .andExpect(jsonPath("$.agencies[0].agencyName").value("한국서비스대행사"))
                    .andExpect(jsonPath("$.agencies[0].status").value("PENDING"))
                    .andExpect(jsonPath("$.agencies[0].platformSettlementStatus").value("PENDING"));
        }

        @Test
        @DisplayName("TC-C-2: 인증 없음 — 401")
        void fail_anonymous_401() throws Exception {
            mockMvc.perform(get("/api/admin/settlements?year=2026&month=6").with(anonymous()))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("TC-C-3: ADMIN이 아닌 role(AGENCY) — 403")
        void fail_wrongRole_403() throws Exception {
            authenticateAs("AGENCY");

            mockMvc.perform(get("/api/admin/settlements?year=2026&month=6"))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("TC-C-4: year/month 파라미터 누락 — 400")
        void fail_missingParam_400() throws Exception {
            authenticateAs("ADMIN");

            mockMvc.perform(get("/api/admin/settlements"))
                    .andExpect(status().isBadRequest());
        }
    }

    // ── ⑧ GET /api/admin/settlements/{agencyId}/details ────────────

    @Nested
    @DisplayName("GET /api/admin/settlements/{agencyId}/details — 건별 정산 내역")
    class GetAgencyDetails {

        @Test
        @DisplayName("TC-C-1: 인증된 ADMIN — 200 OK, 응답 배열 구조 검증")
        void success_200() throws Exception {
            authenticateAs("ADMIN");
            AdminSettlementDetailResponse detail = new AdminSettlementDetailResponse(
                    1L, "SET-001", "2026-06-05", "냉장고", "김철수", 95000, 9500, 85500, "PAID");
            given(adminSettlementService.getAgencyDetails(any(), any(), anyInt(), anyInt()))
                    .willReturn(List.of(detail));

            mockMvc.perform(get("/api/admin/settlements/" + AGENCY_ID + "/details?year=2026&month=6"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$[0].settlementCode").value("SET-001"))
                    .andExpect(jsonPath("$[0].applianceName").value("냉장고"))
                    .andExpect(jsonPath("$[0].customerName").value("김철수"));
        }

        @Test
        @DisplayName("TC-C-2: 인증 없음 — 401")
        void fail_anonymous_401() throws Exception {
            mockMvc.perform(get("/api/admin/settlements/" + AGENCY_ID + "/details?year=2026&month=6")
                            .with(anonymous()))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("TC-C-3: 존재하지 않는 agencyId — 404")
        void fail_agencyNotFound_404() throws Exception {
            authenticateAs("ADMIN");
            given(adminSettlementService.getAgencyDetails(any(), any(), anyInt(), anyInt()))
                    .willThrow(new java.util.NoSuchElementException("존재하지 않는 대행사입니다."));

            mockMvc.perform(get("/api/admin/settlements/999/details?year=2026&month=6"))
                    .andExpect(status().isNotFound());
        }
    }

    // ── ⑨ PATCH /api/admin/settlements/{agencyId}/approve ──────────

    @Nested
    @DisplayName("PATCH /api/admin/settlements/{agencyId}/approve — 단일 대행사 승인")
    class ApproveAgency {

        @Test
        @DisplayName("TC-C-1: 인증된 ADMIN — 200 OK")
        void success_200() throws Exception {
            authenticateAs("ADMIN");

            mockMvc.perform(patch("/api/admin/settlements/" + AGENCY_ID + "/approve?year=2026&month=6"))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("TC-C-2: 인증 없음 — 401")
        void fail_anonymous_401() throws Exception {
            mockMvc.perform(patch("/api/admin/settlements/" + AGENCY_ID + "/approve?year=2026&month=6")
                            .with(anonymous()))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("TC-C-3: 존재하지 않는 agencyId — 404")
        void fail_agencyNotFound_404() throws Exception {
            authenticateAs("ADMIN");
            org.mockito.Mockito.doThrow(new java.util.NoSuchElementException("존재하지 않는 대행사입니다."))
                    .when(adminSettlementService).approveAgency(any(), any(), anyInt(), anyInt());

            mockMvc.perform(patch("/api/admin/settlements/999/approve?year=2026&month=6"))
                    .andExpect(status().isNotFound());
        }
    }

    // ── ⑩ PATCH /api/admin/settlements/approve-all ─────────────────

    @Nested
    @DisplayName("PATCH /api/admin/settlements/approve-all — 미지급 전체 일괄 승인")
    class ApproveAll {

        @Test
        @DisplayName("TC-C-1: 인증된 ADMIN — 200 OK")
        void success_200() throws Exception {
            authenticateAs("ADMIN");

            mockMvc.perform(patch("/api/admin/settlements/approve-all?year=2026&month=6"))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("TC-C-2: 인증 없음 — 401")
        void fail_anonymous_401() throws Exception {
            mockMvc.perform(patch("/api/admin/settlements/approve-all?year=2026&month=6")
                            .with(anonymous()))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("TC-C-3: year/month 파라미터 누락 — 400")
        void fail_missingParam_400() throws Exception {
            authenticateAs("ADMIN");

            mockMvc.perform(patch("/api/admin/settlements/approve-all"))
                    .andExpect(status().isBadRequest());
        }
    }

    // ── ⑪ PATCH /api/admin/settlements/{settlementId}/status ───────

    @Nested
    @DisplayName("PATCH /api/admin/settlements/{settlementId}/status — 건별 보류/재검토")
    class UpdateItemStatus {

        @Test
        @DisplayName("TC-C-1: 인증된 ADMIN, DISPUTED 요청 — 204")
        void success_204() throws Exception {
            authenticateAs("ADMIN");

            mockMvc.perform(patch("/api/admin/settlements/1/status")
                            .contentType("application/json")
                            .content("{\"status\":\"DISPUTED\"}"))
                    .andExpect(status().isNoContent());
        }

        @Test
        @DisplayName("TC-C-2: 인증 없음 — 401")
        void fail_anonymous_401() throws Exception {
            mockMvc.perform(patch("/api/admin/settlements/1/status")
                            .with(anonymous())
                            .contentType("application/json")
                            .content("{\"status\":\"DISPUTED\"}"))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("TC-C-3: ADMIN이 아닌 role(AGENCY) — 403")
        void fail_wrongRole_403() throws Exception {
            authenticateAs("AGENCY");

            mockMvc.perform(patch("/api/admin/settlements/1/status")
                            .contentType("application/json")
                            .content("{\"status\":\"DISPUTED\"}"))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("TC-C-4: status=PAID 요청 — 400 (검증 단계에서 거부)")
        void fail_paidRequest_400() throws Exception {
            authenticateAs("ADMIN");

            mockMvc.perform(patch("/api/admin/settlements/1/status")
                            .contentType("application/json")
                            .content("{\"status\":\"PAID\"}"))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("TC-C-5: 존재하지 않는 정산 — 404")
        void fail_notFound_404() throws Exception {
            authenticateAs("ADMIN");
            org.mockito.Mockito.doThrow(new java.util.NoSuchElementException("존재하지 않는 정산 내역입니다."))
                    .when(adminSettlementService).updateItemStatus(any(), any(), any());

            mockMvc.perform(patch("/api/admin/settlements/999/status")
                            .contentType("application/json")
                            .content("{\"status\":\"DISPUTED\"}"))
                    .andExpect(status().isNotFound());
        }
    }

    // ── ⑫ PATCH /api/admin/settlements/{agencyId}/batch-status ─────

    @Nested
    @DisplayName("PATCH /api/admin/settlements/{agencyId}/batch-status — 배치 단위 보류/재검토")
    class UpdateBatchStatus {

        @Test
        @DisplayName("TC-C-1: 인증된 ADMIN, DISPUTED 요청 — 204")
        void success_204() throws Exception {
            authenticateAs("ADMIN");

            mockMvc.perform(patch("/api/admin/settlements/" + AGENCY_ID + "/batch-status?year=2026&month=6")
                            .contentType("application/json")
                            .content("{\"status\":\"DISPUTED\"}"))
                    .andExpect(status().isNoContent());
        }

        @Test
        @DisplayName("TC-C-2: 인증 없음 — 401")
        void fail_anonymous_401() throws Exception {
            mockMvc.perform(patch("/api/admin/settlements/" + AGENCY_ID + "/batch-status?year=2026&month=6")
                            .with(anonymous())
                            .contentType("application/json")
                            .content("{\"status\":\"DISPUTED\"}"))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("TC-C-3: ADMIN이 아닌 role(AGENCY) — 403")
        void fail_wrongRole_403() throws Exception {
            authenticateAs("AGENCY");

            mockMvc.perform(patch("/api/admin/settlements/" + AGENCY_ID + "/batch-status?year=2026&month=6")
                            .contentType("application/json")
                            .content("{\"status\":\"DISPUTED\"}"))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("TC-C-4: status=PAID 요청 — 400 (검증 단계에서 거부)")
        void fail_paidRequest_400() throws Exception {
            authenticateAs("ADMIN");

            mockMvc.perform(patch("/api/admin/settlements/" + AGENCY_ID + "/batch-status?year=2026&month=6")
                            .contentType("application/json")
                            .content("{\"status\":\"PAID\"}"))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("TC-C-5: 해당 기간 배치 없음 — 404")
        void fail_notFound_404() throws Exception {
            authenticateAs("ADMIN");
            org.mockito.Mockito.doThrow(new java.util.NoSuchElementException("해당 기간의 정산 배치가 존재하지 않습니다."))
                    .when(adminSettlementService).updateBatchStatus(any(), any(), anyInt(), anyInt(), any());

            mockMvc.perform(patch("/api/admin/settlements/" + AGENCY_ID + "/batch-status?year=2026&month=6")
                            .contentType("application/json")
                            .content("{\"status\":\"DISPUTED\"}"))
                    .andExpect(status().isNotFound());
        }

        @Test
        @DisplayName("TC-C-6: year/month 파라미터 누락 — 400")
        void fail_missingParam_400() throws Exception {
            authenticateAs("ADMIN");

            mockMvc.perform(patch("/api/admin/settlements/" + AGENCY_ID + "/batch-status")
                            .contentType("application/json")
                            .content("{\"status\":\"DISPUTED\"}"))
                    .andExpect(status().isBadRequest());
        }
    }
}
