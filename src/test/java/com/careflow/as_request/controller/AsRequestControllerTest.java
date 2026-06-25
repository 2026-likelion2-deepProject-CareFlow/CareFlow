package com.careflow.as_request.controller;

import com.careflow.as_request.dto.AsRequestCreateDto;
import com.careflow.as_request.dto.AsRequestCreateResponseDto;
import com.careflow.as_request.service.AgencyAsRequestService;
import com.careflow.as_request.service.AsRequestService;
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
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(AsRequestController.class)
@Import({SecurityConfig.class, PasswordEncoderConfig.class})
@DisplayName("AsRequestController 테스트")
class AsRequestControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private AsRequestService asRequestService;
    @MockitoBean
    private AgencyAsRequestService agencyAsRequestService;
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

    private static final Long CUSTOMER_ID = 1L;

    /** 각 테스트에서 인증된 사용자로 SecurityContext 를 설정 */
    @BeforeEach
    void setUpAuth() {
        CustomUserDetails userDetails = new CustomUserDetails(
                CUSTOMER_ID, "customer@test.com", "pw", "CUSTOMER");
        UsernamePasswordAuthenticationToken auth =
                new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities());
        SecurityContextHolder.getContext().setAuthentication(auth);
    }

    // ─────────────────────────────────────────────
    //  POST /api/as-requests — A/S 접수 및 기사 배정
    // ─────────────────────────────────────────────
    @Nested
    @DisplayName("POST /api/as-requests — A/S 접수 및 기사 배정")
    class CreateAsRequest {

        private AsRequestCreateDto validAutoDto;
        private AsRequestCreateDto validManualDto;

        @BeforeEach
        void setUp() throws Exception {
            // v5 스키마: symptomCode → symptomId
            validAutoDto = objectMapper.readValue("""
                    {
                      "applianceId": 1,
                      "symptomId": 1,
                      "symptomDesc": "냉각이 안 됩니다",
                      "visitRegionId": 10,
                      "visitAddressDetail": "강남구 테헤란로 123",
                      "scheduledDate": "2026-07-01",
                      "scheduledTime": "10:00",
                      "assignType": "AUTO"
                    }
                    """, AsRequestCreateDto.class);

            validManualDto = objectMapper.readValue("""
                    {
                      "applianceId": 1,
                      "symptomId": 1,
                      "visitRegionId": 10,
                      "visitAddressDetail": "강남구 테헤란로 123",
                      "scheduledDate": "2026-07-01",
                      "scheduledTime": "10:00",
                      "assignType": "MANUAL",
                      "preferredEngineerId": 5
                    }
                    """, AsRequestCreateDto.class);
        }

        @Test
        @DisplayName("성공(AUTO): 자동 배정 요청 — 201 Created, requestId + assignmentId 반환")
        void createAsRequest_auto_success() throws Exception {
            given(asRequestService.createAsRequest(eq(CUSTOMER_ID), any()))
                    .willReturn(new AsRequestCreateResponseDto(10L, 20L,
                            "스케줄, 브랜드, 카테고리, 서비스 지역 4가지 조건 모두 충족"));

            mockMvc.perform(post("/api/as-requests")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(validAutoDto)))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.requestId").value(10))
                    .andExpect(jsonPath("$.assignmentId").value(20))
                    .andExpect(jsonPath("$.matchReason").value(
                            "스케줄, 브랜드, 카테고리, 서비스 지역 4가지 조건 모두 충족"));
        }

        @Test
        @DisplayName("성공(MANUAL): 수동 배정 요청 — 201 Created, requestId + assignmentId 반환")
        void createAsRequest_manual_success() throws Exception {
            given(asRequestService.createAsRequest(eq(CUSTOMER_ID), any()))
                    .willReturn(new AsRequestCreateResponseDto(11L, 21L, null));

            mockMvc.perform(post("/api/as-requests")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(validManualDto)))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.requestId").value(11))
                    .andExpect(jsonPath("$.assignmentId").value(21))
                    .andExpect(jsonPath("$.matchReason").isEmpty());
        }

        @Test
        @DisplayName("실패: 필수 필드(applianceId) 누락 — 400 Bad Request")
        void createAsRequest_missingApplianceId_400() throws Exception {
            String json = """
                    {
                      "symptomId": 1,
                      "visitRegionId": 10,
                      "visitAddressDetail": "강남구 테헤란로 123",
                      "scheduledDate": "2026-07-01",
                      "scheduledTime": "10:00",
                      "assignType": "AUTO"
                    }
                    """;

            mockMvc.perform(post("/api/as-requests")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("실패: AUTO 배정 시 가용 기사 없음 — 403 Forbidden (IllegalStateException)")
        void createAsRequest_auto_noEngineerAvailable_403() throws Exception {
            given(asRequestService.createAsRequest(eq(CUSTOMER_ID), any()))
                    .willThrow(new IllegalStateException("요청하신 날짜/시간에 가용한 수리 기사가 없습니다."));

            mockMvc.perform(post("/api/as-requests")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(validAutoDto)))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.success").value(false))
                    .andExpect(jsonPath("$.message").exists());
        }

        @Test
        @DisplayName("실패: MANUAL 배정 시 존재하지 않는 기사 ID — 400 Bad Request")
        void createAsRequest_manual_engineerNotFound_400() throws Exception {
            given(asRequestService.createAsRequest(eq(CUSTOMER_ID), any()))
                    .willThrow(new IllegalArgumentException("존재하지 않는 수리 기사입니다."));

            mockMvc.perform(post("/api/as-requests")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(validManualDto)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.success").value(false));
        }
    }

    // ─────────────────────────────────────────────
    //  GET /api/as-requests/me — 내 A/S 목록 조회
    // ─────────────────────────────────────────────
    @Nested
    @DisplayName("GET /api/as-requests/me — 내 A/S 목록 조회")
    class GetMyAsRequests {

        @Test
        @DisplayName("성공: 목록 반환 — 200 OK")
        void getMyAsRequests_success() throws Exception {
            given(asRequestService.getMyAsRequests(CUSTOMER_ID)).willReturn(List.of());

            mockMvc.perform(get("/api/as-requests/me"))
                    .andExpect(status().isOk());
        }
    }

    // ─────────────────────────────────────────────
    //  PATCH /api/as-requests/{id}/cancel — A/S 취소
    // ─────────────────────────────────────────────
    @Nested
    @DisplayName("PATCH /api/as-requests/{id}/cancel — A/S 취소")
    class CancelAsRequest {

        @Test
        @DisplayName("성공: 취소 — 200 OK")
        void cancelAsRequest_success() throws Exception {
            doNothing().when(asRequestService).cancelAsRequest(anyLong(), anyLong(), any());

            mockMvc.perform(patch("/api/as-requests/1/cancel"))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("실패: 이미 배정된 요청 취소 시도 — 403 Forbidden")
        void cancelAsRequest_alreadyAssigned_403() throws Exception {
            doThrow(new IllegalStateException("기사 배정이 진행된 요청은 취소할 수 없습니다."))
                    .when(asRequestService).cancelAsRequest(anyLong(), anyLong(), any());

            mockMvc.perform(patch("/api/as-requests/1/cancel"))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.success").value(false));
        }

        @Test
        @DisplayName("실패: 존재하지 않는 요청 ID — 400 Bad Request")
        void cancelAsRequest_notFound_400() throws Exception {
            doThrow(new IllegalArgumentException("존재하지 않는 A/S 요청입니다."))
                    .when(asRequestService).cancelAsRequest(anyLong(), anyLong(), any());

            mockMvc.perform(patch("/api/as-requests/999/cancel"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.success").value(false));
        }
    }
}
