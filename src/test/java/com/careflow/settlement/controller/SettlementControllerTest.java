package com.careflow.settlement.controller;

import com.careflow.auth.security.CustomOAuth2UserService;
import com.careflow.auth.security.CustomUserDetails;
import com.careflow.auth.security.JwtProvider;
import com.careflow.auth.security.OAuth2LoginSuccessHandler;
import com.careflow.common.config.PasswordEncoderConfig;
import com.careflow.common.config.SecurityConfig;
import com.careflow.settlement.dto.EngineerPerformanceItem;
import com.careflow.settlement.dto.EngineerPerformanceResponse;
import com.careflow.settlement.dto.MonthlySummaryResponse;
import com.careflow.settlement.service.SettlementService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.mapping.JpaMetamodelMappingContext;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.NoSuchElementException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.given;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.anonymous;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(SettlementController.class)
@Import({SecurityConfig.class, PasswordEncoderConfig.class})
@DisplayName("SettlementController 슬라이스 테스트")
class SettlementControllerTest {

    @Autowired private MockMvc mockMvc;

    @MockitoBean private SettlementService settlementService;
    @MockitoBean private JwtProvider jwtProvider;
    @MockitoBean private JpaMetamodelMappingContext jpaMetamodelMappingContext;
    @MockitoBean private CustomOAuth2UserService customOAuth2UserService;
    @MockitoBean private OAuth2LoginSuccessHandler oAuth2LoginSuccessHandler;
    @MockitoBean private ClientRegistrationRepository clientRegistrationRepository;

    private static final Long AGENCY_USER_ID = 1L;

    // 각 테스트 전 AGENCY 역할로 인증된 상태 설정
    @BeforeEach
    void setUpAuth() {
        CustomUserDetails userDetails = new CustomUserDetails(
                AGENCY_USER_ID, "agency@test.com", "pw", "ROLE_AGENCY");
        UsernamePasswordAuthenticationToken auth =
                new UsernamePasswordAuthenticationToken(
                        userDetails, null, userDetails.getAuthorities());
        SecurityContextHolder.getContext().setAuthentication(auth);
    }

    // ══════════════════════════════════════════════════════════════
    //  1. GET /api/settlements/engineers/performance
    // ══════════════════════════════════════════════════════════════
    @Nested
    @DisplayName("GET /api/settlements/engineers/performance — 기사별 실적 리포트")
    class GetEngineerPerformance {

        @Test
        @DisplayName("성공: 기사 2명 실적 반환 — 200 OK, JSON 구조 검증")
        void success_200_returnsList() throws Exception {
            EngineerPerformanceResponse response = EngineerPerformanceResponse.builder()
                    .year(2026).month(6)
                    .engineers(List.of(
                            EngineerPerformanceItem.builder()
                                    .engineerId(10L).engineerName("홍길동")
                                    .completedCount(12).avgRating(4.75).totalEarning(960000)
                                    .build(),
                            EngineerPerformanceItem.builder()
                                    .engineerId(11L).engineerName("이순신")
                                    .completedCount(8).avgRating(4.25).totalEarning(640000)
                                    .build()))
                    .build();

            given(settlementService.getEngineerPerformance(eq(AGENCY_USER_ID), eq(2026), eq(6)))
                    .willReturn(response);

            mockMvc.perform(get("/api/agency/settlements/engineers/performance")
                            .param("year", "2026")
                            .param("month", "6"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.year").value(2026))
                    .andExpect(jsonPath("$.month").value(6))
                    .andExpect(jsonPath("$.engineers.length()").value(2))
                    .andExpect(jsonPath("$.engineers[0].engineerId").value(10))
                    .andExpect(jsonPath("$.engineers[0].engineerName").value("홍길동"))
                    .andExpect(jsonPath("$.engineers[0].completedCount").value(12))
                    .andExpect(jsonPath("$.engineers[0].avgRating").value(4.75))
                    .andExpect(jsonPath("$.engineers[0].totalEarning").value(960000));
        }

        @Test
        @DisplayName("성공: 해당 월 데이터 없음 — 200 OK, 빈 배열 반환")
        void success_200_emptyList() throws Exception {
            given(settlementService.getEngineerPerformance(eq(AGENCY_USER_ID), anyInt(), anyInt()))
                    .willReturn(EngineerPerformanceResponse.builder()
                            .year(2026).month(6).engineers(List.of()).build());

            mockMvc.perform(get("/api/agency/settlements/engineers/performance")
                            .param("year", "2026")
                            .param("month", "6"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.engineers.length()").value(0));
        }

        @Test
        @DisplayName("실패: JWT 없음 — 401 Unauthorized")
        void fail_noAuth_401() throws Exception {
            mockMvc.perform(get("/api/agency/settlements/engineers/performance")
                            .with(anonymous())
                            .param("year", "2026")
                            .param("month", "6"))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("실패: year 파라미터 누락 — 400 Bad Request")
        void fail_missingYear_400() throws Exception {
            mockMvc.perform(get("/api/agency/settlements/engineers/performance")
                            .param("month", "6"))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("실패: month 파라미터 누락 — 400 Bad Request")
        void fail_missingMonth_400() throws Exception {
            mockMvc.perform(get("/api/agency/settlements/engineers/performance")
                            .param("year", "2026"))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("실패: month=0 — 서비스에서 IllegalArgumentException → 400")
        void fail_invalidMonth_400() throws Exception {
            given(settlementService.getEngineerPerformance(eq(AGENCY_USER_ID), anyInt(), eq(0)))
                    .willThrow(new IllegalArgumentException("월은 1~12 사이여야 합니다."));

            mockMvc.perform(get("/api/agency/settlements/engineers/performance")
                            .param("year", "2026")
                            .param("month", "0"))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("실패: 대행사 정보 없는 유저 — 404 Not Found")
        void fail_noAgency_404() throws Exception {
            given(settlementService.getEngineerPerformance(eq(AGENCY_USER_ID), anyInt(), anyInt()))
                    .willThrow(new NoSuchElementException("소속 대행사 정보가 없습니다."));

            mockMvc.perform(get("/api/agency/settlements/engineers/performance")
                            .param("year", "2026")
                            .param("month", "6"))
                    .andExpect(status().isNotFound());
        }
    }

    // ══════════════════════════════════════════════════════════════
    //  2. GET /api/settlements/monthly-summary
    // ══════════════════════════════════════════════════════════════
    @Nested
    @DisplayName("GET /api/settlements/monthly-summary — 월별 합산 내역")
    class GetMonthlySummary {

        @Test
        @DisplayName("성공: 합산 내역 반환 — 200 OK, 모든 필드 검증")
        void success_200_returnsSummary() throws Exception {
            given(settlementService.getMonthlySummary(eq(AGENCY_USER_ID), eq(2026), eq(6)))
                    .willReturn(MonthlySummaryResponse.builder()
                            .year(2026).month(6)
                            .totalCount(20)
                            .totalGrossAmount(4000000)
                            .totalPlatformFee(400000)
                            .totalAgencyFee(360000)
                            .totalEngineerPayout(3240000)
                            .build());

            mockMvc.perform(get("/api/agency/settlements/summary")
                            .param("year", "2026")
                            .param("month", "6"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.totalCount").value(20))
                    .andExpect(jsonPath("$.totalGrossAmount").value(4000000))
                    .andExpect(jsonPath("$.totalPlatformFee").value(400000))
                    .andExpect(jsonPath("$.totalAgencyFee").value(360000))
                    .andExpect(jsonPath("$.totalEngineerPayout").value(3240000));
        }

        @Test
        @DisplayName("성공: 데이터 없는 월 — 200 OK, 모든 금액 0 반환")
        void success_200_allZero() throws Exception {
            given(settlementService.getMonthlySummary(eq(AGENCY_USER_ID), anyInt(), anyInt()))
                    .willReturn(MonthlySummaryResponse.builder()
                            .year(2026).month(6)
                            .totalCount(0).totalGrossAmount(0)
                            .totalPlatformFee(0).totalAgencyFee(0).totalEngineerPayout(0)
                            .build());

            mockMvc.perform(get("/api/agency/settlements/summary")
                            .param("year", "2026")
                            .param("month", "6"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.totalCount").value(0))
                    .andExpect(jsonPath("$.totalGrossAmount").value(0));
        }

        @Test
        @DisplayName("실패: JWT 없음 — 401 Unauthorized")
        void fail_noAuth_401() throws Exception {
            mockMvc.perform(get("/api/agency/settlements/summary")
                            .with(anonymous())
                            .param("year", "2026")
                            .param("month", "6"))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("실패: month=13 — 서비스에서 IllegalArgumentException → 400")
        void fail_invalidMonth_400() throws Exception {
            given(settlementService.getMonthlySummary(eq(AGENCY_USER_ID), anyInt(), eq(13)))
                    .willThrow(new IllegalArgumentException("월은 1~12 사이여야 합니다."));

            mockMvc.perform(get("/api/agency/settlements/summary")
                            .param("year", "2026")
                            .param("month", "13"))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("실패: month 누락 — 400 Bad Request")
        void fail_missingMonth_400() throws Exception {
            mockMvc.perform(get("/api/agency/settlements/summary")
                            .param("year", "2026"))
                    .andExpect(status().isBadRequest());
        }
    }

    // ══════════════════════════════════════════════════════════════
    //  3. GET /api/settlements/monthly-report/download
    // ══════════════════════════════════════════════════════════════
    @Nested
    @DisplayName("GET /api/settlements/monthly-report/download — CSV 다운로드")
    class DownloadMonthlyReport {

        private static final byte[] BOM = {(byte) 0xEF, (byte) 0xBB, (byte) 0xBF};
        private static final String SAMPLE_CSV_CONTENT = "[기사별 실적]\n기사ID,기사명";

        @Test
        @DisplayName("성공: CSV 다운로드 — 200 OK, Content-Type·Content-Disposition 검증")
        void success_200_csvResponse() throws Exception {
            byte[] csvBytes = concat(BOM, SAMPLE_CSV_CONTENT.getBytes());
            given(settlementService.generateMonthlyCsv(eq(AGENCY_USER_ID), eq(2026), eq(6)))
                    .willReturn(csvBytes);

            mockMvc.perform(get("/api/agency/settlements/monthly-report/download")
                            .param("year", "2026")
                            .param("month", "6"))
                    .andExpect(status().isOk())
                    .andExpect(header().string("Content-Disposition",
                            "attachment; filename=\"settlement_report_2026_06.csv\""))
                    .andExpect(content().contentTypeCompatibleWith(
                            MediaType.parseMediaType("text/csv")));
        }

        @Test
        @DisplayName("성공: 응답 바디가 UTF-8 BOM 으로 시작")
        void success_responseBodyStartsWithBom() throws Exception {
            byte[] csvBytes = concat(BOM, SAMPLE_CSV_CONTENT.getBytes());
            given(settlementService.generateMonthlyCsv(eq(AGENCY_USER_ID), anyInt(), anyInt()))
                    .willReturn(csvBytes);

            byte[] body = mockMvc.perform(get("/api/agency/settlements/monthly-report/download")
                            .param("year", "2026")
                            .param("month", "6"))
                    .andExpect(status().isOk())
                    .andReturn().getResponse().getContentAsByteArray();

            assertThat(body[0]).isEqualTo((byte) 0xEF);
            assertThat(body[1]).isEqualTo((byte) 0xBB);
            assertThat(body[2]).isEqualTo((byte) 0xBF);
        }

        @Test
        @DisplayName("실패: JWT 없음 — 401 Unauthorized")
        void fail_noAuth_401() throws Exception {
            mockMvc.perform(get("/api/agency/settlements/monthly-report/download")
                            .with(anonymous())
                            .param("year", "2026")
                            .param("month", "6"))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("실패: month=0 — 서비스에서 IllegalArgumentException → 400")
        void fail_invalidMonth_400() throws Exception {
            given(settlementService.generateMonthlyCsv(eq(AGENCY_USER_ID), anyInt(), eq(0)))
                    .willThrow(new IllegalArgumentException("월은 1~12 사이여야 합니다."));

            mockMvc.perform(get("/api/agency/settlements/monthly-report/download")
                            .param("year", "2026")
                            .param("month", "0"))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("성공: 데이터 없는 월 — 빈 데이터 CSV 반환 (헤더 포함)")
        void success_emptyData_csvWithHeader() throws Exception {
            byte[] emptyCsv = concat(BOM, "[기사별 실적]\n기사ID,기사명,완료건수,평균평점,실수령액(원)\n".getBytes());
            given(settlementService.generateMonthlyCsv(eq(AGENCY_USER_ID), anyInt(), anyInt()))
                    .willReturn(emptyCsv);

            mockMvc.perform(get("/api/agency/settlements/monthly-report/download")
                            .param("year", "2026")
                            .param("month", "6"))
                    .andExpect(status().isOk());
        }

        // byte[] 두 개를 이어 붙이는 헬퍼
        private byte[] concat(byte[] a, byte[] b) {
            byte[] result = new byte[a.length + b.length];
            System.arraycopy(a, 0, result, 0, a.length);
            System.arraycopy(b, 0, result, a.length, b.length);
            return result;
        }
    }

}
