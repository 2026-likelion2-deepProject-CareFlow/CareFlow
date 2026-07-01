
package com.careflow.report.controller;

import com.careflow.auth.security.CustomOAuth2UserService;
import com.careflow.auth.security.CustomUserDetails;
import com.careflow.auth.security.JwtProvider;
import com.careflow.auth.security.OAuth2LoginSuccessHandler;
import com.careflow.common.config.PasswordEncoderConfig;
import com.careflow.common.config.SecurityConfig;
import com.careflow.report.dto.EngineerReportListResponse;
import com.careflow.report.dto.WorkReportDetailResponse;
import com.careflow.report.service.WorkReportService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.mapping.JpaMetamodelMappingContext;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.util.List;
import java.util.NoSuchElementException;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willDoNothing;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.anonymous;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(WorkReportController.class)
@Import({SecurityConfig.class, PasswordEncoderConfig.class})
@DisplayName("WorkReportController 단위 테스트")
class WorkReportControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;

    @MockitoBean private WorkReportService workReportService;
    @MockitoBean private JwtProvider jwtProvider;
    @MockitoBean private JpaMetamodelMappingContext jpaMetamodelMappingContext;
    @MockitoBean private CustomOAuth2UserService customOAuth2UserService;
    @MockitoBean private OAuth2LoginSuccessHandler oAuth2LoginSuccessHandler;
    @MockitoBean private ClientRegistrationRepository clientRegistrationRepository;

    @MockitoBean private StringRedisTemplate stringRedisTemplate;

    private static final Long ENGINEER_USER_ID = 10L;
    private static final Long CUSTOMER_USER_ID = 1L;

    // 기사 권한 픽스처
    private RequestPostProcessor engineerAuth;
    // 고객 권한 픽스처
    private RequestPostProcessor customerAuth;

    @BeforeEach
    void setUp() {
        engineerAuth = SecurityMockMvcRequestPostProcessors.authentication(
                new UsernamePasswordAuthenticationToken(
                        new CustomUserDetails(ENGINEER_USER_ID, "engineer@test.com", "pw", "ENGINEER", 100L),
                        null, List.of(new SimpleGrantedAuthority("ENGINEER"))
                )
        );
        customerAuth = SecurityMockMvcRequestPostProcessors.authentication(
                new UsernamePasswordAuthenticationToken(
                        new CustomUserDetails(CUSTOMER_USER_ID, "customer@test.com", "pw", "CUSTOMER", null),
                        null, List.of(new SimpleGrantedAuthority("CUSTOMER"))
                )
        );
    }

    @Nested
    @DisplayName("GET /api/engineer/work-reports — 기사 작업 보고서 목록 조회")
    class GetEngineerWorkReports {

        @Test
        @DisplayName("성공: 프론트엔드의 page=1 요청이 백엔드의 page=0으로 매핑되어 호출된다")
        void success_paginationTranslation() throws Exception {
            EngineerReportListResponse stubResponse = EngineerReportListResponse.builder()
                    .reportId(1L).requestId("AS-20260701-0001").status("SUBMITTED").build();

            PageRequest expectedPageRequest = PageRequest.of(0, 20);
            Page<EngineerReportListResponse> mockPage = new PageImpl<>(List.of(stubResponse), expectedPageRequest, 1);

            given(workReportService.getEngineerWorkReports(eq(ENGINEER_USER_ID), eq(expectedPageRequest)))
                    .willReturn(mockPage);

            mockMvc.perform(get("/api/engineer/work-reports")
                            .with(engineerAuth)
                            .param("page", "1")
                            .param("size", "20"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content.length()").value(1))
                    .andExpect(jsonPath("$.content[0].status").value("SUBMITTED"));
        }

        @Test
        @DisplayName("성공: 파라미터가 없으면 기본값(1페이지, 20개)이 적용된다")
        void success_defaultPagination() throws Exception {
            PageRequest expectedPageRequest = PageRequest.of(0, 20);
            Page<EngineerReportListResponse> mockPage = new PageImpl<>(List.of(), expectedPageRequest, 0);

            given(workReportService.getEngineerWorkReports(eq(ENGINEER_USER_ID), eq(expectedPageRequest)))
                    .willReturn(mockPage);

            mockMvc.perform(get("/api/engineer/work-reports").with(engineerAuth))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content.length()").value(0));
        }

        @Test
        @DisplayName("예외 테스트: 인증 토큰 없음 — 401 Unauthorized")
        void fail_noAuth_401() throws Exception {
            mockMvc.perform(get("/api/engineer/work-reports").with(anonymous()))
                    .andExpect(status().isUnauthorized());
        }
    }

    @Nested
    @DisplayName("POST /api/engineer/work-reports — 작업 완료 보고서 제출")
    class SubmitReport {

        @Test
        @DisplayName("성공: 보고서 제출 정상 처리 — 201 Created")
        void success_201() throws Exception {
            String jsonRequest = """
                    {
                        "requestId": 100,
                        "diagnosisResult": "REPAIRED",
                        "workDurationMin": 60,
                        "finalAmount": 50000
                    }
                    """;

            given(workReportService.submitWorkReport(eq(ENGINEER_USER_ID), any())).willReturn(1L);

            mockMvc.perform(post("/api/engineer/work-reports")
                            .with(engineerAuth)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(jsonRequest))
                    .andExpect(status().isCreated())
                    .andExpect(content().string("작업 완료 보고서가 제출되고, 제품 건강 진단서가 갱신되었습니다. (Report ID: 1)"));
        }

        @Test
        @DisplayName("예외 테스트: 필수 필드 누락 — 400 Bad Request")
        void fail_validation_400() throws Exception {
            String badJsonRequest = """
                    {
                        "diagnosisResult": "REPAIRED",
                        "workDurationMin": 60,
                        "finalAmount": 50000
                    }
                    """;

            mockMvc.perform(post("/api/engineer/work-reports")
                            .with(engineerAuth)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(badJsonRequest))
                    .andExpect(status().isBadRequest());
        }
    }

    @Nested
    @DisplayName("GET /api/engineer/work-reports/{reportId} — 보고서 상세 조회")
    class GetReportDetail {

        @Test
        @DisplayName("성공: 상세 조회 정상 처리 — 200 OK")
        void success_200() throws Exception {
            WorkReportDetailResponse stubResponse = WorkReportDetailResponse.builder()
                    .reportId(1L).engineerName("테스트기사").build();

            given(workReportService.getWorkReportDetail(eq(ENGINEER_USER_ID), eq("ENGINEER"), eq(1L)))
                    .willReturn(stubResponse);

            mockMvc.perform(get("/api/engineer/work-reports/1").with(engineerAuth))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.reportId").value(1))
                    .andExpect(jsonPath("$.engineerName").value("테스트기사"));
        }

        @Test
        @DisplayName("예외 테스트: 존재하지 않는 보고서 — 404 Not Found")
        void fail_notFound_404() throws Exception {
            given(workReportService.getWorkReportDetail(any(), any(), eq(999L)))
                    .willThrow(new NoSuchElementException("존재하지 않는 보고서입니다."));

            mockMvc.perform(get("/api/engineer/work-reports/999").with(engineerAuth))
                    .andExpect(status().isNotFound());
        }
    }

    @Nested
    @DisplayName("PATCH /api/engineer/work-reports/{reportId}/approve — 보고서 고객 승인")
    class ApproveReport {

        @Test
        @DisplayName("성공: CUSTOMER 권한으로 승인 — 200 OK")
        void success_200() throws Exception {
            mockMvc.perform(patch("/api/engineer/work-reports/1/approve").with(customerAuth))
                    .andExpect(status().isOk())
                    .andExpect(content().string("작업 보고서가 성공적으로 승인되었습니다. 결제 단계로 이동합니다."));
        }

        @Test
        @DisplayName("예외 테스트: ENGINEER 권한으로 승인 시도 시 예외 발생 — 403 Forbidden")
        void fail_engineerRole_403() throws Exception {
            // 🌟 수정 포인트: 401(isUnauthorized) -> 403(isForbidden)으로 변경 완료!
            mockMvc.perform(patch("/api/engineer/work-reports/1/approve").with(engineerAuth))
                    .andExpect(status().isForbidden());
        }
    }

    @Nested
    @DisplayName("DELETE /api/engineer/work-reports/{reportId}/approval-request — 승인 요청 취소")
    class CancelApprovalRequest {

        @Test
        @DisplayName("성공: ENGINEER 역할이 승인 요청을 취소하면 200 OK 반환")
        void success_200() throws Exception {
            willDoNothing().given(workReportService).cancelApprovalRequest(eq(ENGINEER_USER_ID), eq(1L));

            mockMvc.perform(delete("/api/engineer/work-reports/1/approval-request").with(engineerAuth))
                    .andExpect(status().isOk())
                    .andExpect(content().string("보고서 승인 요청이 취소되었습니다."));
        }

        @Test
        @DisplayName("예외 테스트: CUSTOMER 권한으로 접근 시 403 Forbidden 반환")
        void fail_notEngineer_403() throws Exception {
            mockMvc.perform(delete("/api/engineer/work-reports/1/approval-request").with(customerAuth))
                    .andExpect(status().isForbidden());
        }
    }
}