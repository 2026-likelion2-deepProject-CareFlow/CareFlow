package com.careflow.as_request.controller;

import com.careflow.as_request.dto.AgencyAsRequestDetailResponse;
import com.careflow.as_request.dto.AgencyAsRequestListResponse;
import com.careflow.as_request.service.AgencyAsRequestService;
import com.careflow.as_request.service.AsRequestService;
import com.careflow.auth.security.CustomOAuth2UserService;
import com.careflow.auth.security.CustomUserDetails;
import com.careflow.auth.security.JwtProvider;
import com.careflow.auth.security.OAuth2LoginSuccessHandler;
import com.careflow.common.config.PasswordEncoderConfig;
import com.careflow.common.config.SecurityConfig;
import com.careflow.common.enums.AsStatus;
import com.careflow.common.enums.AssignType;
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
import java.time.LocalDateTime;
import java.util.List;
import java.util.NoSuchElementException;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(AsRequestController.class)
@Import({SecurityConfig.class, PasswordEncoderConfig.class})
@DisplayName("AgencyAsRequest 컨트롤러 단위 테스트")
class AgencyAsRequestControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean private AgencyAsRequestService agencyAsRequestService;
    @MockitoBean private AsRequestService asRequestService;
    @MockitoBean private JwtProvider jwtProvider;
    @MockitoBean private JpaMetamodelMappingContext jpaMetamodelMappingContext;
    @MockitoBean private CustomOAuth2UserService customOAuth2UserService;
    @MockitoBean private OAuth2LoginSuccessHandler oAuth2LoginSuccessHandler;
    @MockitoBean private ClientRegistrationRepository clientRegistrationRepository;

    private static final Long AGENCY_USER_ID = 1L;

    @BeforeEach
    void setUpAuth() {
        CustomUserDetails userDetails = new CustomUserDetails(
                AGENCY_USER_ID, "agency@test.com", "pw", "AGENCY");
        UsernamePasswordAuthenticationToken auth =
                new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities());
        SecurityContextHolder.getContext().setAuthentication(auth);
    }

    // ─────────────────────────────────────────────
    //  GET /api/as-requests/agency — 전체 목록 조회
    // ─────────────────────────────────────────────
    @Nested
    @DisplayName("GET /api/as-requests/agency — 전체 목록 조회")
    class GetAgencyAsRequests {

        @Test
        @DisplayName("성공: 요청 2건 — 200 OK, 목록 반환")
        void success_200() throws Exception {
            given(agencyAsRequestService.getAsRequestsByAgency(any()))
                    .willReturn(List.of(
                            stubListResponse(1L, AsStatus.ASSIGNED),
                            stubListResponse(2L, AsStatus.IN_PROGRESS)));

            mockMvc.perform(get("/api/as-requests/agency"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.length()").value(2))
                    .andExpect(jsonPath("$[0].requestId").value(1))
                    .andExpect(jsonPath("$[0].status").value("ASSIGNED"))
                    .andExpect(jsonPath("$[1].status").value("IN_PROGRESS"));
        }

        @Test
        @DisplayName("성공: 요청 없음 — 204 No Content")
        void empty_204() throws Exception {
            given(agencyAsRequestService.getAsRequestsByAgency(any())).willReturn(List.of());

            mockMvc.perform(get("/api/as-requests/agency"))
                    .andExpect(status().isNoContent());
        }

        @Test
        @DisplayName("실패: AGENCY 권한 없음(CUSTOMER) — 401 Unauthorized")
        void notAgency_401() throws Exception {
            given(agencyAsRequestService.getAsRequestsByAgency(any()))
                    .willThrow(new IllegalAccessException("대행사 관리자 권한이 없습니다."));

            mockMvc.perform(get("/api/as-requests/agency"))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.success").value(false));
        }

        @Test
        @DisplayName("실패: 소속 대행사 정보 없음 — 403 Forbidden (IllegalStateException)")
        void noAgencyInfo_403() throws Exception {
            given(agencyAsRequestService.getAsRequestsByAgency(any()))
                    .willThrow(new IllegalStateException("소속 대행사 정보가 없습니다."));

            mockMvc.perform(get("/api/as-requests/agency"))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.success").value(false));
        }
    }

    // ─────────────────────────────────────────────
    //  GET /api/as-requests/agency/search — 필터링 조회
    // ─────────────────────────────────────────────
    @Nested
    @DisplayName("GET /api/as-requests/agency/search — 필터링 조회")
    class SearchAgencyAsRequests {

        @Test
        @DisplayName("성공: 날짜 필터 — 200 OK")
        void dateFilter_200() throws Exception {
            given(agencyAsRequestService.searchAsRequests(any(), any(LocalDate.class), isNull()))
                    .willReturn(List.of(stubListResponse(1L, AsStatus.ASSIGNED)));

            mockMvc.perform(get("/api/as-requests/agency/search")
                            .param("date", "2026-07-01"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.length()").value(1));
        }

        @Test
        @DisplayName("성공: 상태 필터 — 200 OK")
        void statusFilter_200() throws Exception {
            given(agencyAsRequestService.searchAsRequests(any(), isNull(), eq("ASSIGNED")))
                    .willReturn(List.of(stubListResponse(1L, AsStatus.ASSIGNED)));

            mockMvc.perform(get("/api/as-requests/agency/search")
                            .param("status", "ASSIGNED"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$[0].status").value("ASSIGNED"));
        }

        @Test
        @DisplayName("성공: 필터 미입력(전체) — 결과 있음 200 OK")
        void noFilter_200() throws Exception {
            given(agencyAsRequestService.searchAsRequests(any(), isNull(), isNull()))
                    .willReturn(List.of(stubListResponse(1L, AsStatus.IN_PROGRESS)));

            mockMvc.perform(get("/api/as-requests/agency/search"))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("성공: 결과 없음 — 204 No Content")
        void empty_204() throws Exception {
            given(agencyAsRequestService.searchAsRequests(any(), any(), any())).willReturn(List.of());

            mockMvc.perform(get("/api/as-requests/agency/search"))
                    .andExpect(status().isNoContent());
        }

        @Test
        @DisplayName("실패: 유효하지 않은 status 값 — 400 Bad Request")
        void invalidStatus_400() throws Exception {
            given(agencyAsRequestService.searchAsRequests(any(), any(), eq("INVALID")))
                    .willThrow(new IllegalArgumentException("유효하지 않은 상태 값입니다: INVALID"));

            mockMvc.perform(get("/api/as-requests/agency/search")
                            .param("status", "INVALID"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.success").value(false));
        }
    }

    // ─────────────────────────────────────────────
    //  GET /api/as-requests/agency/{requestId} — 단건 상세
    // ─────────────────────────────────────────────
    @Nested
    @DisplayName("GET /api/as-requests/agency/{requestId} — 단건 상세 조회")
    class GetAgencyAsRequestDetail {

        @Test
        @DisplayName("성공: 존재하는 요청 — 200 OK, 상세 정보 반환")
        void success_200() throws Exception {
            given(agencyAsRequestService.getAsRequestDetail(any(), eq(1L)))
                    .willReturn(stubDetailResponse(1L, AsStatus.ASSIGNED));

            mockMvc.perform(get("/api/as-requests/agency/1"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.requestId").value(1))
                    .andExpect(jsonPath("$.status").value("ASSIGNED"))
                    .andExpect(jsonPath("$.customerName").value("테스트고객"))
                    .andExpect(jsonPath("$.brand").value("삼성"))
                    .andExpect(jsonPath("$.symptomName").value("냉방 불량"));
        }

        @Test
        @DisplayName("실패: 존재하지 않는 requestId — 404 Not Found (NoSuchElementException)")
        void notFound_404() throws Exception {
            given(agencyAsRequestService.getAsRequestDetail(any(), eq(999L)))
                    .willThrow(new NoSuchElementException("해당 A/S 요청을 찾을 수 없습니다."));

            mockMvc.perform(get("/api/as-requests/agency/999"))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.success").value(false));
        }

        @Test
        @DisplayName("실패: 타 대행사 요청 접근 — 401 Unauthorized (IllegalAccessException)")
        void wrongAgency_401() throws Exception {
            given(agencyAsRequestService.getAsRequestDetail(any(), eq(1L)))
                    .willThrow(new IllegalAccessException("본인 소속 대행사의 A/S 요청만 조회할 수 있습니다."));

            mockMvc.perform(get("/api/as-requests/agency/1"))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.success").value(false));
        }
    }

    // ── 픽스처 헬퍼 ──────────────────────────────────────────────────

    private AgencyAsRequestListResponse stubListResponse(Long requestId, AsStatus status) {
        return new AgencyAsRequestListResponse(
                requestId, status, "테스트고객", "010-1111-2222",
                "냉방 불량", "냉각이 안 됩니다",
                "강남구", "테헤란로 123",
                LocalDate.of(2026, 7, 1), "10:00",
                AssignType.MANUAL, LocalDateTime.now());
    }

    private AgencyAsRequestDetailResponse stubDetailResponse(Long requestId, AsStatus status) {
        return new AgencyAsRequestDetailResponse(
                requestId, status,
                "테스트고객", "010-1111-2222",
                "COOLING_FAIL", "냉방 불량", "냉각이 안 됩니다", null,
                "삼성", "Q9000", "SN001",
                LocalDate.of(2020, 1, 1), LocalDate.of(2023, 1, 1),
                AssignType.MANUAL, "강남구", "테헤란로 123",
                LocalDate.of(2026, 7, 1), "10:00",
                null, LocalDateTime.now(), LocalDateTime.now());
    }
}
