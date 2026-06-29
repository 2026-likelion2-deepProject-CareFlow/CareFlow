package com.careflow.agency.controller;

import com.careflow.agency.dto.request.AgencyEngineerProfileUpdateRequest;
import com.careflow.agency.dto.response.AgencyEngineerDetailResponse;
import com.careflow.agency.dto.response.AgencyEngineerSummaryResponse;
import com.careflow.agency.dto.response.EngineerLmsStatusResponse;
import com.careflow.agency.dto.response.EngineerRealtimeStatusResponse;
import com.careflow.agency.dto.response.EngineerRecommendResponse;
import com.careflow.agency.dto.response.EngineerReviewListResponse;
import com.careflow.agency.dto.response.EngineerSettlementResponse;
import com.careflow.agency.service.AgencyEngineerService;
import com.careflow.auth.security.CustomOAuth2UserService;
import com.careflow.auth.security.CustomUserDetails;
import com.careflow.auth.security.JwtProvider;
import com.careflow.auth.security.OAuth2LoginSuccessHandler;
import com.careflow.common.config.PasswordEncoderConfig;
import com.careflow.common.config.SecurityConfig;
import com.careflow.engineer.dto.ScheduleResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import org.springframework.security.test.context.support.WithAnonymousUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;

import java.lang.reflect.Constructor;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.NoSuchElementException;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.given;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.anonymous;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(AgencyEngineerController.class)
@Import({SecurityConfig.class, PasswordEncoderConfig.class})
@DisplayName("AgencyEngineerController 통합 테스트")
class AgencyEngineerControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;

    @MockitoBean private AgencyEngineerService agencyEngineerService;
    @MockitoBean private JwtProvider jwtProvider;
    @MockitoBean private JpaMetamodelMappingContext jpaMetamodelMappingContext;
    @MockitoBean private CustomOAuth2UserService customOAuth2UserService;
    @MockitoBean private OAuth2LoginSuccessHandler oAuth2LoginSuccessHandler;
    @MockitoBean private ClientRegistrationRepository clientRegistrationRepository;

    private static final Long AGENCY_USER_ID   = 1L;
    private static final Long ENGINEER_USER_ID = 10L;

    // 모든 테스트에서 AGENCY 역할로 인증된 상태를 기본으로 설정
    @BeforeEach
    void setUpAuth() {
        CustomUserDetails userDetails = new CustomUserDetails(
                AGENCY_USER_ID, "agency@test.com", "pw", "ROLE_AGENCY", null);
        UsernamePasswordAuthenticationToken auth =
                new UsernamePasswordAuthenticationToken(
                        userDetails, null, userDetails.getAuthorities());
        SecurityContextHolder.getContext().setAuthentication(auth);
    }

    // ══════════════════════════════════════════════════════════════
    //  1. GET /api/agencies/me/engineers — 소속 기사 목록 조회
    // ══════════════════════════════════════════════════════════════
    @Nested
    @DisplayName("GET /api/agencies/me/engineers — 소속 기사 목록 조회")
    class GetAgencyEngineers {

        @Test
        @DisplayName("성공: 소속 기사 2명 — 200 OK, JSON 배열 반환")
        void success_200_returnsList() throws Exception {
            given(agencyEngineerService.getAgencyEngineers(AGENCY_USER_ID))
                    .willReturn(List.of(
                            stubSummary(10L, "홍길동", "INTERMEDIATE", "AVAILABLE"),
                            stubSummary(11L, "김수리", "BEGINNER", "BOOKED")));

            mockMvc.perform(get("/api/agency/engineers"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.length()").value(2))
                    .andExpect(jsonPath("$[0].engineerUserId").value(10))
                    .andExpect(jsonPath("$[0].name").value("홍길동"))
                    .andExpect(jsonPath("$[0].skillLevel").value("INTERMEDIATE"))
                    .andExpect(jsonPath("$[0].currentWorkStatus").value("AVAILABLE"))
                    .andExpect(jsonPath("$[1].engineerUserId").value(11))
                    .andExpect(jsonPath("$[1].currentWorkStatus").value("BOOKED"));
        }

        @Test
        @DisplayName("성공: 소속 기사 없음 — 200 OK, 빈 배열 반환")
        void success_200_emptyList() throws Exception {
            given(agencyEngineerService.getAgencyEngineers(AGENCY_USER_ID))
                    .willReturn(List.of());

            mockMvc.perform(get("/api/agency/engineers"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.length()").value(0));
        }

        @Test
        @DisplayName("실패: 인증 없음 — 401 Unauthorized")
        void fail_noAuth_401() throws Exception {
            mockMvc.perform(get("/api/agency/engineers").with(anonymous()))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("실패: 대행사 정보 없는 유저 — 404 Not Found")
        void fail_noAgency_404() throws Exception {
            given(agencyEngineerService.getAgencyEngineers(AGENCY_USER_ID))
                    .willThrow(new NoSuchElementException("소속 대행사 정보가 없습니다."));

            mockMvc.perform(get("/api/agency/engineers"))
                    .andExpect(status().isNotFound());
        }
    }

    // ══════════════════════════════════════════════════════════════
    //  2. GET /api/agencies/me/engineers/{engineerUserId} — 기사 상세 조회
    // ══════════════════════════════════════════════════════════════
    @Nested
    @DisplayName("GET /api/agencies/me/engineers/{id} — 소속 기사 상세 조회")
    class GetAgencyEngineerDetail {

        @Test
        @DisplayName("성공: 소속 기사 상세 반환 — 200 OK")
        void success_200_returnsDetail() throws Exception {
            given(agencyEngineerService.getAgencyEngineerDetail(AGENCY_USER_ID, ENGINEER_USER_ID))
                    .willReturn(stubDetail(ENGINEER_USER_ID, "홍길동"));

            mockMvc.perform(get("/api/agency/engineers/{id}/profile", ENGINEER_USER_ID))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.engineerUserId").value(ENGINEER_USER_ID))
                    .andExpect(jsonPath("$.name").value("홍길동"))
                    .andExpect(jsonPath("$.email").value("engineer@test.com"))
                    .andExpect(jsonPath("$.skillLevel").value("INTERMEDIATE"))
                    .andExpect(jsonPath("$.expertBrands[0]").value("삼성"))
                    .andExpect(jsonPath("$.serviceRegionIds[0]").value(101));
        }

        @Test
        @DisplayName("실패: 타 대행사 소속 기사 조회 — 401 Unauthorized")
        void fail_otherAgency_401() throws Exception {
            given(agencyEngineerService.getAgencyEngineerDetail(AGENCY_USER_ID, 20L))
                    .willThrow(new IllegalAccessException("소속 대행사의 기사만 조회/수정할 수 있습니다."));

            mockMvc.perform(get("/api/agency/engineers/{id}/profile", 20L))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("실패: 존재하지 않는 기사 — 404 Not Found")
        void fail_notFound_404() throws Exception {
            given(agencyEngineerService.getAgencyEngineerDetail(AGENCY_USER_ID, 999L))
                    .willThrow(new NoSuchElementException("해당 기사의 프로필 정보가 존재하지 않습니다."));

            mockMvc.perform(get("/api/agency/engineers/{id}/profile", 999L))
                    .andExpect(status().isNotFound());
        }

        @Test
        @DisplayName("실패: 인증 없음 — 401 Unauthorized")
        void fail_noAuth_401() throws Exception {
            mockMvc.perform(get("/api/agency/engineers/{id}/profile", ENGINEER_USER_ID).with(anonymous()))
                    .andExpect(status().isUnauthorized());
        }
    }

    // ══════════════════════════════════════════════════════════════
    //  3. PATCH /api/agencies/me/engineers/{id}/profile — 기사 프로필 수정
    // ══════════════════════════════════════════════════════════════
    @Nested
    @DisplayName("PATCH /api/agencies/me/engineers/{id}/profile — 소속 기사 프로필 수정")
    class UpdateAgencyEngineerProfile {

        @Test
        @DisplayName("성공: 카테고리·브랜드·지역 수정 — 200 OK, 수정된 값 반환")
        void success_200_updatesProfile() throws Exception {
            AgencyEngineerProfileUpdateRequest request =
                    buildUpdateRequest(7, List.of("다이슨"), List.of(201));

            AgencyEngineerDetailResponse updated = AgencyEngineerDetailResponse.builder()
                    .engineerUserId(ENGINEER_USER_ID)
                    .name("홍길동")
                    .categoryId(7)
                    .categoryName("에어컨")
                    .skillLevel("INTERMEDIATE")
                    .expertBrands(List.of("다이슨"))
                    .serviceRegionIds(List.of(201))
                    .build();

            given(agencyEngineerService.updateAgencyEngineerProfile(
                    eq(AGENCY_USER_ID), eq(ENGINEER_USER_ID), any()))
                    .willReturn(updated);

            mockMvc.perform(put("/api/agency/engineers/{id}/profile", ENGINEER_USER_ID)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.categoryId").value(7))
                    .andExpect(jsonPath("$.expertBrands[0]").value("다이슨"))
                    .andExpect(jsonPath("$.serviceRegionIds[0]").value(201));
        }

        @Test
        @DisplayName("실패: depth=1(대분류) 카테고리 지정 — 400 Bad Request")
        void fail_invalidCategory_400() throws Exception {
            AgencyEngineerProfileUpdateRequest request =
                    buildUpdateRequest(1, null, null);

            given(agencyEngineerService.updateAgencyEngineerProfile(
                    eq(AGENCY_USER_ID), eq(ENGINEER_USER_ID), any()))
                    .willThrow(new IllegalArgumentException("전문 분야는 소분류(depth=2) 카테고리만 선택 가능합니다."));

            mockMvc.perform(put("/api/agency/engineers/{id}/profile", ENGINEER_USER_ID)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("실패: 타 대행사 소속 기사 수정 시도 — 401 Unauthorized")
        void fail_otherAgency_401() throws Exception {
            AgencyEngineerProfileUpdateRequest request =
                    buildUpdateRequest(null, List.of("삼성"), null);

            given(agencyEngineerService.updateAgencyEngineerProfile(
                    eq(AGENCY_USER_ID), eq(20L), any()))
                    .willThrow(new IllegalAccessException("소속 대행사의 기사만 조회/수정할 수 있습니다."));

            mockMvc.perform(put("/api/agency/engineers/{id}/profile", 20L)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("실패: 존재하지 않는 기사 — 404 Not Found")
        void fail_notFound_404() throws Exception {
            AgencyEngineerProfileUpdateRequest request =
                    buildUpdateRequest(null, List.of("삼성"), null);

            given(agencyEngineerService.updateAgencyEngineerProfile(
                    eq(AGENCY_USER_ID), eq(999L), any()))
                    .willThrow(new NoSuchElementException("해당 기사의 프로필 정보가 존재하지 않습니다."));

            mockMvc.perform(put("/api/agency/engineers/{id}/profile", 999L)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isNotFound());
        }

        @Test
        @DisplayName("실패: 인증 없음 — 401 Unauthorized")
        void fail_noAuth_401() throws Exception {
            mockMvc.perform(put("/api/agency/engineers/{id}/profile", ENGINEER_USER_ID)
                            .with(anonymous())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{}"))
                    .andExpect(status().isUnauthorized());
        }
    }

    // ══════════════════════════════════════════════════════════════
    //  4. GET /api/agencies/me/engineers/{id}/schedules — 월간 근무표 조회
    // ══════════════════════════════════════════════════════════════
    @Nested
    @DisplayName("GET /api/agencies/me/engineers/{id}/schedules — 소속 기사 월간 근무표 조회")
    class GetAgencyEngineerSchedules {

        @Test
        @DisplayName("성공: 근무표 2건 — 200 OK, 배열 반환")
        void success_200_returnsSchedules() throws Exception {
            given(agencyEngineerService.getAgencyEngineerSchedules(
                    AGENCY_USER_ID, ENGINEER_USER_ID, 2026, 6))
                    .willReturn(List.of(
                            stubSchedule(201L, "2026-06-03", "AVAILABLE"),
                            stubSchedule(202L, "2026-06-05", "BOOKED")));

            mockMvc.perform(get("/api/agency/engineers/{id}/schedules", ENGINEER_USER_ID)
                            .param("year", "2026")
                            .param("month", "6"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.length()").value(2))
                    .andExpect(jsonPath("$[0].scheduleId").value(201))
                    .andExpect(jsonPath("$[0].workDate").value("2026-06-03"))
                    .andExpect(jsonPath("$[0].status").value("AVAILABLE"))
                    .andExpect(jsonPath("$[1].status").value("BOOKED"));
        }

        @Test
        @DisplayName("성공: 근무표 없음 — 200 OK, 빈 배열 반환")
        void success_200_emptySchedule() throws Exception {
            given(agencyEngineerService.getAgencyEngineerSchedules(
                    AGENCY_USER_ID, ENGINEER_USER_ID, 2026, 6))
                    .willReturn(List.of());

            mockMvc.perform(get("/api/agency/engineers/{id}/schedules", ENGINEER_USER_ID)
                            .param("year", "2026")
                            .param("month", "6"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.length()").value(0));
        }

        @Test
        @DisplayName("실패: year 파라미터 누락 — 400 Bad Request")
        void fail_missingYear_400() throws Exception {
            mockMvc.perform(get("/api/agency/engineers/{id}/schedules", ENGINEER_USER_ID)
                            .param("month", "6"))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("실패: month 파라미터 누락 — 400 Bad Request")
        void fail_missingMonth_400() throws Exception {
            mockMvc.perform(get("/api/agency/engineers/{id}/schedules", ENGINEER_USER_ID)
                            .param("year", "2026"))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("실패: 타 대행사 소속 기사 근무표 조회 — 401 Unauthorized")
        void fail_otherAgency_401() throws Exception {
            given(agencyEngineerService.getAgencyEngineerSchedules(
                    AGENCY_USER_ID, 20L, 2026, 6))
                    .willThrow(new IllegalAccessException("소속 대행사의 기사만 조회할 수 있습니다."));

            mockMvc.perform(get("/api/agency/engineers/{id}/schedules", 20L)
                            .param("year", "2026")
                            .param("month", "6"))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("실패: 존재하지 않는 기사 — 404 Not Found")
        void fail_notFound_404() throws Exception {
            given(agencyEngineerService.getAgencyEngineerSchedules(
                    AGENCY_USER_ID, 999L, 2026, 6))
                    .willThrow(new NoSuchElementException("해당 기사 정보가 존재하지 않습니다."));

            mockMvc.perform(get("/api/agency/engineers/{id}/schedules", 999L)
                            .param("year", "2026")
                            .param("month", "6"))
                    .andExpect(status().isNotFound());
        }

        @Test
        @DisplayName("실패: 인증 없음 — 401 Unauthorized")
        void fail_noAuth_401() throws Exception {
            mockMvc.perform(get("/api/agency/engineers/{id}/schedules", ENGINEER_USER_ID)
                            .with(anonymous())
                            .param("year", "2026")
                            .param("month", "6"))
                    .andExpect(status().isUnauthorized());
        }
    }

    // ══════════════════════════════════════════════════════════════
    //  5. GET /api/agency/engineers/recommended — 추천 기사 목록
    // ══════════════════════════════════════════════════════════════
    @Nested
    @DisplayName("GET /api/agency/engineers/recommended — 추천 기사 목록 조회")
    class GetRecommendedEngineers {

        @Test
        @DisplayName("성공: 추천 기사 2명 — 200 OK, JSON 배열 반환")
        void success_200_returnsList() throws Exception {
            given(agencyEngineerService.getRecommendedEngineers(AGENCY_USER_ID, 1L))
                    .willReturn(List.of(
                            stubRecommend(10L, "홍길동", "LG 전문", "강남구", 4.9),
                            stubRecommend(11L, "이수리", "삼성 전문", "서초구", 4.7)));

            mockMvc.perform(get("/api/agency/engineers/recommended").param("requestId", "1"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.length()").value(2))
                    .andExpect(jsonPath("$[0].id").value(10))
                    .andExpect(jsonPath("$[0].name").value("홍길동"))
                    .andExpect(jsonPath("$[0].brand").value("LG 전문"))
                    .andExpect(jsonPath("$[0].isLmsCompleted").value(true));
        }

        @Test
        @DisplayName("성공: 조건 맞는 기사 없음 — 200 OK, 빈 배열 반환")
        void success_200_emptyList() throws Exception {
            given(agencyEngineerService.getRecommendedEngineers(AGENCY_USER_ID, 1L))
                    .willReturn(List.of());

            mockMvc.perform(get("/api/agency/engineers/recommended").param("requestId", "1"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.length()").value(0));
        }

        @Test
        @DisplayName("실패: 존재하지 않는 requestId — 404 Not Found")
        void fail_notFoundRequest_404() throws Exception {
            given(agencyEngineerService.getRecommendedEngineers(AGENCY_USER_ID, 999L))
                    .willThrow(new NoSuchElementException("해당 A/S 요청이 존재하지 않습니다."));

            mockMvc.perform(get("/api/agency/engineers/recommended").param("requestId", "999"))
                    .andExpect(status().isNotFound());
        }

        @Test
        @DisplayName("실패: 인증 없음 — 401 Unauthorized")
        void fail_noAuth_401() throws Exception {
            mockMvc.perform(get("/api/agency/engineers/recommended")
                            .with(anonymous())
                            .param("requestId", "1"))
                    .andExpect(status().isUnauthorized());
        }
    }

    // ══════════════════════════════════════════════════════════════
    //  6. GET /api/agency/engineers/realtime-status — 실시간 배정 현황
    // ══════════════════════════════════════════════════════════════
    @Nested
    @DisplayName("GET /api/agency/engineers/realtime-status — 실시간 배정 현황 조회")
    class GetEngineersRealtimeStatus {

        @Test
        @DisplayName("성공: 기사 2명 현황 반환 — 200 OK")
        void success_200_returnsList() throws Exception {
            given(agencyEngineerService.getEngineersRealtimeStatus(AGENCY_USER_ID))
                    .willReturn(List.of(
                            stubRealtime(10L, "홍길동", "IN_PROGRESS"),
                            stubRealtime(11L, "이수리", null)));

            mockMvc.perform(get("/api/agency/engineers/realtime-status"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.length()").value(2))
                    .andExpect(jsonPath("$[0].id").value(10))
                    .andExpect(jsonPath("$[0].asStatus").value("IN_PROGRESS"))
                    .andExpect(jsonPath("$[1].asStatus").doesNotExist());
        }

        @Test
        @DisplayName("실패: 인증 없음 — 401 Unauthorized")
        void fail_noAuth_401() throws Exception {
            mockMvc.perform(get("/api/agency/engineers/realtime-status").with(anonymous()))
                    .andExpect(status().isUnauthorized());
        }
    }

    // ══════════════════════════════════════════════════════════════
    //  7. GET /api/agency/engineers/{id}/settlements — 기사 정산 목록
    // ══════════════════════════════════════════════════════════════
    @Nested
    @DisplayName("GET /api/agency/engineers/{id}/settlements — 기사 정산 목록 조회")
    class GetEngineerSettlements {

        @Test
        @DisplayName("성공: 정산 1건 반환 — 200 OK")
        void success_200_returnsList() throws Exception {
            given(agencyEngineerService.getEngineerSettlements(AGENCY_USER_ID, ENGINEER_USER_ID))
                    .willReturn(List.of(stubSettlement(1L, 150000, "PAID")));

            mockMvc.perform(get("/api/agency/engineers/{id}/settlements", ENGINEER_USER_ID))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.length()").value(1))
                    .andExpect(jsonPath("$[0].settlementId").value(1))
                    .andExpect(jsonPath("$[0].grossAmount").value(150000))
                    .andExpect(jsonPath("$[0].status").value("PAID"));
        }

        @Test
        @DisplayName("성공: 정산 없음 — 200 OK, 빈 배열")
        void success_200_emptyList() throws Exception {
            given(agencyEngineerService.getEngineerSettlements(AGENCY_USER_ID, ENGINEER_USER_ID))
                    .willReturn(List.of());

            mockMvc.perform(get("/api/agency/engineers/{id}/settlements", ENGINEER_USER_ID))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.length()").value(0));
        }

        @Test
        @DisplayName("실패: 타 대행사 기사 접근 — 401 Unauthorized")
        void fail_otherAgency_401() throws Exception {
            given(agencyEngineerService.getEngineerSettlements(AGENCY_USER_ID, 20L))
                    .willThrow(new IllegalAccessException("소속 대행사의 기사만 조회/수정할 수 있습니다."));

            mockMvc.perform(get("/api/agency/engineers/{id}/settlements", 20L))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("실패: 기사 프로필 없음 — 404 Not Found")
        void fail_notFound_404() throws Exception {
            given(agencyEngineerService.getEngineerSettlements(AGENCY_USER_ID, 999L))
                    .willThrow(new NoSuchElementException("해당 기사의 프로필 정보가 존재하지 않습니다."));

            mockMvc.perform(get("/api/agency/engineers/{id}/settlements", 999L))
                    .andExpect(status().isNotFound());
        }

        @Test
        @DisplayName("실패: 인증 없음 — 401 Unauthorized")
        void fail_noAuth_401() throws Exception {
            mockMvc.perform(get("/api/agency/engineers/{id}/settlements", ENGINEER_USER_ID)
                            .with(anonymous()))
                    .andExpect(status().isUnauthorized());
        }
    }

    // ══════════════════════════════════════════════════════════════
    //  8. GET /api/agency/engineers/{id}/lms — 기사 LMS 이수 현황
    // ══════════════════════════════════════════════════════════════
    @Nested
    @DisplayName("GET /api/agency/engineers/{id}/lms — 기사 LMS 이수 현황 조회")
    class GetEngineerLmsStatus {

        @Test
        @DisplayName("성공: LMS 이수 완료 기사 — 200 OK")
        void success_200_lmsCompleted() throws Exception {
            EngineerLmsStatusResponse resp = EngineerLmsStatusResponse.builder()
                    .isLmsCompleted(true)
                    .currentYear(2026)
                    .confirmations(List.of(
                            EngineerLmsStatusResponse.ConfirmationItem.builder()
                                    .contentId(1L).title("냉장고 수리 기초").requiredLevel("BEGINNER")
                                    .confirmedAt(LocalDateTime.of(2026, 3, 10, 14, 0))
                                    .confirmedVersion("1.0").build()))
                    .build();

            given(agencyEngineerService.getEngineerLmsStatus(AGENCY_USER_ID, ENGINEER_USER_ID))
                    .willReturn(resp);

            mockMvc.perform(get("/api/agency/engineers/{id}/lms", ENGINEER_USER_ID))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.isLmsCompleted").value(true))
                    .andExpect(jsonPath("$.currentYear").value(2026))
                    .andExpect(jsonPath("$.confirmations.length()").value(1))
                    .andExpect(jsonPath("$.confirmations[0].title").value("냉장고 수리 기초"));
        }

        @Test
        @DisplayName("성공: LMS 미이수 기사 — 200 OK, 이수 이력 빈 배열")
        void success_200_lmsNotCompleted() throws Exception {
            EngineerLmsStatusResponse resp = EngineerLmsStatusResponse.builder()
                    .isLmsCompleted(false).currentYear(2026).confirmations(List.of()).build();

            given(agencyEngineerService.getEngineerLmsStatus(AGENCY_USER_ID, ENGINEER_USER_ID))
                    .willReturn(resp);

            mockMvc.perform(get("/api/agency/engineers/{id}/lms", ENGINEER_USER_ID))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.isLmsCompleted").value(false))
                    .andExpect(jsonPath("$.confirmations.length()").value(0));
        }

        @Test
        @DisplayName("실패: 타 대행사 기사 접근 — 401 Unauthorized")
        void fail_otherAgency_401() throws Exception {
            given(agencyEngineerService.getEngineerLmsStatus(AGENCY_USER_ID, 20L))
                    .willThrow(new IllegalAccessException("소속 대행사의 기사만 조회/수정할 수 있습니다."));

            mockMvc.perform(get("/api/agency/engineers/{id}/lms", 20L))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("실패: 인증 없음 — 401 Unauthorized")
        void fail_noAuth_401() throws Exception {
            mockMvc.perform(get("/api/agency/engineers/{id}/lms", ENGINEER_USER_ID).with(anonymous()))
                    .andExpect(status().isUnauthorized());
        }
    }

    // ══════════════════════════════════════════════════════════════
    //  9. GET /api/agency/engineers/{id}/reviews — 기사 수신 리뷰
    // ══════════════════════════════════════════════════════════════
    @Nested
    @DisplayName("GET /api/agency/engineers/{id}/reviews — 기사 수신 리뷰 목록 조회")
    class GetEngineerReviews {

        @Test
        @DisplayName("성공: 리뷰 2건 반환 — 200 OK")
        void success_200_returnsList() throws Exception {
            EngineerReviewListResponse resp = EngineerReviewListResponse.builder()
                    .totalReviews(2).avgRating(4.5)
                    .reviews(List.of(
                            EngineerReviewListResponse.ReviewItem.builder()
                                    .reviewId(1L).customerName("김고객").rating(5)
                                    .content("친절했어요").createdAt(LocalDateTime.now()).build(),
                            EngineerReviewListResponse.ReviewItem.builder()
                                    .reviewId(2L).customerName("박고객").rating(4)
                                    .content("빠른 수리").createdAt(LocalDateTime.now()).build()))
                    .build();

            given(agencyEngineerService.getEngineerReviews(AGENCY_USER_ID, ENGINEER_USER_ID))
                    .willReturn(resp);

            mockMvc.perform(get("/api/agency/engineers/{id}/reviews", ENGINEER_USER_ID))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.totalReviews").value(2))
                    .andExpect(jsonPath("$.avgRating").value(4.5))
                    .andExpect(jsonPath("$.reviews.length()").value(2))
                    .andExpect(jsonPath("$.reviews[0].customerName").value("김고객"));
        }

        @Test
        @DisplayName("성공: 리뷰 없음 — 200 OK, 빈 배열")
        void success_200_emptyList() throws Exception {
            given(agencyEngineerService.getEngineerReviews(AGENCY_USER_ID, ENGINEER_USER_ID))
                    .willReturn(EngineerReviewListResponse.builder()
                            .totalReviews(0).avgRating(0.0).reviews(List.of()).build());

            mockMvc.perform(get("/api/agency/engineers/{id}/reviews", ENGINEER_USER_ID))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.totalReviews").value(0))
                    .andExpect(jsonPath("$.reviews.length()").value(0));
        }

        @Test
        @DisplayName("실패: 타 대행사 기사 접근 — 401 Unauthorized")
        void fail_otherAgency_401() throws Exception {
            given(agencyEngineerService.getEngineerReviews(AGENCY_USER_ID, 20L))
                    .willThrow(new IllegalAccessException("소속 대행사의 기사만 조회/수정할 수 있습니다."));

            mockMvc.perform(get("/api/agency/engineers/{id}/reviews", 20L))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("실패: 기사 프로필 없음 — 404 Not Found")
        void fail_notFound_404() throws Exception {
            given(agencyEngineerService.getEngineerReviews(AGENCY_USER_ID, 999L))
                    .willThrow(new NoSuchElementException("해당 기사의 프로필 정보가 존재하지 않습니다."));

            mockMvc.perform(get("/api/agency/engineers/{id}/reviews", 999L))
                    .andExpect(status().isNotFound());
        }

        @Test
        @DisplayName("실패: 인증 없음 — 401 Unauthorized")
        void fail_noAuth_401() throws Exception {
            mockMvc.perform(get("/api/agency/engineers/{id}/reviews", ENGINEER_USER_ID).with(anonymous()))
                    .andExpect(status().isUnauthorized());
        }
    }

    // ═══════════════════════════════════════════════════════════
    //  픽스처 헬퍼
    // ═══════════════════════════════════════════════════════════

    private AgencyEngineerSummaryResponse stubSummary(
            Long userId, String name, String skillLevel, String status) {
        return AgencyEngineerSummaryResponse.builder()
                .engineerUserId(userId)
                .name(name)
                .categoryId(5)
                .categoryName("냉장고")
                .skillLevel(skillLevel)
                .serviceRegionIds(List.of(101))
                .isLmsCompleted(true)
                .currentWorkStatus(status)
                .build();
    }

    private AgencyEngineerDetailResponse stubDetail(Long userId, String name) {
        return AgencyEngineerDetailResponse.builder()
                .engineerUserId(userId)
                .name(name)
                .email("engineer@test.com")
                .phone("010-1234-5678")
                .categoryId(5)
                .categoryName("냉장고")
                .careerStartedYear(2015)
                .skillLevel("INTERMEDIATE")
                .introduction("소개글")
                .avgRating(new BigDecimal("4.80"))
                .totalReviews(32)
                .isLmsCompleted(true)
                .expertBrands(List.of("삼성"))
                .serviceRegionIds(List.of(101))
                .build();
    }

    private ScheduleResponse stubSchedule(Long scheduleId, String workDate, String status) {
        return ScheduleResponse.builder()
                .scheduleId(scheduleId)
                .workDate(LocalDate.parse(workDate))
                .timeSlots(List.of())
                .status(status)
                .build();
    }

    private EngineerRecommendResponse stubRecommend(Long id, String name, String brand, String region, double rating) {
        return EngineerRecommendResponse.builder()
                .id(id).name(name).brand(brand).region(region)
                .rating(rating).availableFrom("09:00 이후 가능")
                .profileImageUrl(null).isLmsCompleted(true).build();
    }

    private EngineerRealtimeStatusResponse stubRealtime(Long id, String name, String asStatus) {
        return EngineerRealtimeStatusResponse.builder()
                .id(id).name(name).specialty("냉장고").region("강남구")
                .product(asStatus != null ? "LG 냉장고 수리" : null)
                .timeRange(asStatus != null ? "10:00 ~ 12:00" : null)
                .progress(asStatus != null ? 50 : null)
                .isLmsCompleted(true).profileImageUrl(null).asStatus(asStatus).build();
    }

    private EngineerSettlementResponse stubSettlement(Long settlementId, int grossAmount, String status) {
        return EngineerSettlementResponse.builder()
                .settlementId(settlementId).requestId(100L)
                .scheduledDate(LocalDate.of(2026, 6, 15))
                .grossAmount(grossAmount).platformFee(15000).agencyFee(7500)
                .engineerNetAmount(grossAmount - 15000 - 7500)
                .status(status).createdAt(LocalDateTime.now()).build();
    }

    private AgencyEngineerProfileUpdateRequest buildUpdateRequest(
            Integer categoryId, List<String> brands, List<Integer> regionIds) {
        try {
            Constructor<AgencyEngineerProfileUpdateRequest> ctor =
                    AgencyEngineerProfileUpdateRequest.class.getDeclaredConstructor();
            ctor.setAccessible(true);
            AgencyEngineerProfileUpdateRequest req = ctor.newInstance();
            ReflectionTestUtils.setField(req, "categoryId", categoryId);
            ReflectionTestUtils.setField(req, "expertBrands", brands);
            ReflectionTestUtils.setField(req, "serviceRegionIds", regionIds);
            return req;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
