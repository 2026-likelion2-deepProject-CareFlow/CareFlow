package com.careflow.agency.controller;

import com.careflow.agency.dto.response.AgencyCustomerApplianceResponse;
import com.careflow.agency.dto.response.AgencyCustomerAsRequestResponse;
import com.careflow.agency.dto.response.AgencyCustomerListResponse;
import com.careflow.agency.dto.response.AgencyCustomerPaymentResponse;
import com.careflow.agency.service.AgencyCustomerService;
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
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.NoSuchElementException;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.anonymous;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(AgencyCustomerController.class)
@Import({SecurityConfig.class, PasswordEncoderConfig.class})
@DisplayName("AgencyCustomerController 통합 테스트")
class AgencyCustomerControllerTest {

    @Autowired private MockMvc mockMvc;

    @MockitoBean private AgencyCustomerService agencyCustomerService;
    @MockitoBean private JwtProvider jwtProvider;
    @MockitoBean private JpaMetamodelMappingContext jpaMetamodelMappingContext;
    @MockitoBean private CustomOAuth2UserService customOAuth2UserService;
    @MockitoBean private OAuth2LoginSuccessHandler oAuth2LoginSuccessHandler;
    @MockitoBean private ClientRegistrationRepository clientRegistrationRepository;
    @MockitoBean private org.springframework.data.redis.core.StringRedisTemplate stringRedisTemplate;

    private static final Long AGENCY_USER_ID = 1L;
    private static final Long AGENCY_ID = 100L;

    @BeforeEach
    void setUpAuth() {
        CustomUserDetails userDetails = new CustomUserDetails(
                AGENCY_USER_ID, "agency@test.com", "pw", "AGENCY", AGENCY_ID);
        UsernamePasswordAuthenticationToken auth =
                new UsernamePasswordAuthenticationToken(
                        userDetails, null, userDetails.getAuthorities());
        SecurityContextHolder.getContext().setAuthentication(auth);
    }

    private AgencyCustomerListResponse sampleResponse() {
        AgencyCustomerListResponse.Stats stats =
                new AgencyCustomerListResponse.Stats(2, 1, 1, 0, 0, 0);
        AgencyCustomerListResponse.CustomerSummary summary = new AgencyCustomerListResponse.CustomerSummary(
                1L, "김민수", "010-1234-5678", "kim@test.com", "강남구 테헤란로 123",
                LocalDateTime.now(), null, "ACTIVE", 3, LocalDateTime.now());
        return new AgencyCustomerListResponse(stats, List.of(summary), 2, 1, 0, 10);
    }

    @Nested
    @DisplayName("GET /api/agency/customers — 고객 목록 조회")
    class GetCustomers {

        @Test
        @DisplayName("TC-C-1: 인증된 AGENCY — 200 OK, 응답 구조 검증")
        void success_200_returnsCustomerList() throws Exception {
            given(agencyCustomerService.searchCustomers(any(), any(), any()))
                    .willReturn(sampleResponse());

            mockMvc.perform(get("/api/agency/customers"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.stats.totalCount").value(2))
                    .andExpect(jsonPath("$.content[0].userId").value(1))
                    .andExpect(jsonPath("$.content[0].name").value("김민수"))
                    .andExpect(jsonPath("$.totalElements").value(2));
        }

        @Test
        @DisplayName("TC-C-2: 인증 없음 — 401")
        void fail_anonymous_returns401() throws Exception {
            mockMvc.perform(get("/api/agency/customers").with(anonymous()))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("TC-C-3: page/size 미전달 시 기본값(0,10)으로 Service 호출")
        void success_defaultPageable() throws Exception {
            given(agencyCustomerService.searchCustomers(any(), any(), any()))
                    .willReturn(sampleResponse());

            mockMvc.perform(get("/api/agency/customers"))
                    .andExpect(status().isOk());

            verify(agencyCustomerService).searchCustomers(any(), eq(PageRequest.of(0, 10)), any());
        }

        @Test
        @DisplayName("TC-C-4: 요청 바디 없이 호출해도 정상 동작")
        void success_noRequestBody() throws Exception {
            given(agencyCustomerService.searchCustomers(any(), any(), eq(null)))
                    .willReturn(sampleResponse());

            mockMvc.perform(get("/api/agency/customers?page=0&size=10"))
                    .andExpect(status().isOk());
        }
    }

    @Nested
    @DisplayName("GET /api/agency/customers/{userId}/appliances — 고객 가전 목록 조회")
    class GetCustomerAppliances {

        private static final Long CUSTOMER_ID = 1L;

        private AgencyCustomerApplianceResponse sampleAppliance() {
            return new AgencyCustomerApplianceResponse(
                    1L, "에어컨", "삼성", "바람의나라 AF17", "SN-001",
                    LocalDate.of(2022, 3, 15), LocalDate.of(2025, 3, 15),
                    "NORMAL", "MANUAL", "https://example.com/img.jpg", LocalDateTime.now());
        }

        @Test
        @DisplayName("TC-C-1: 인증된 AGENCY — 200 OK + JSON 배열 검증")
        void success_200_returnsApplianceList() throws Exception {
            given(agencyCustomerService.getCustomerAppliances(any(), eq(CUSTOMER_ID)))
                    .willReturn(List.of(sampleAppliance()));

            mockMvc.perform(get("/api/agency/customers/{userId}/appliances", CUSTOMER_ID))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$[0].applianceId").value(1))
                    .andExpect(jsonPath("$[0].categoryName").value("에어컨"))
                    .andExpect(jsonPath("$[0].brand").value("삼성"));
        }

        @Test
        @DisplayName("TC-C-2: 인증 없음 — 401")
        void fail_anonymous_returns401() throws Exception {
            mockMvc.perform(get("/api/agency/customers/{userId}/appliances", CUSTOMER_ID).with(anonymous()))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("TC-C-3: Service에서 NoSuchElementException 발생 — 404")
        void fail_userNotFound_404() throws Exception {
            given(agencyCustomerService.getCustomerAppliances(any(), eq(999L)))
                    .willThrow(new NoSuchElementException("해당 고객 정보를 찾을 수 없습니다."));

            mockMvc.perform(get("/api/agency/customers/{userId}/appliances", 999L))
                    .andExpect(status().isNotFound());
        }

        @Test
        @DisplayName("TC-C-4: Service에서 IllegalAccessException 발생 — 401")
        void fail_noAccess_401() throws Exception {
            given(agencyCustomerService.getCustomerAppliances(any(), eq(CUSTOMER_ID)))
                    .willThrow(new IllegalAccessException("본인 대행사로부터 서비스를 받은 고객만 조회할 수 있습니다."));

            mockMvc.perform(get("/api/agency/customers/{userId}/appliances", CUSTOMER_ID))
                    .andExpect(status().isUnauthorized());
        }
    }

    @Nested
    @DisplayName("GET /api/agency/customers/{userId}/as-requests — 고객 A/S 이력 조회")
    class GetCustomerAsRequests {

        private static final Long CUSTOMER_ID = 1L;

        private AgencyCustomerAsRequestResponse sampleAsRequest() {
            return new AgencyCustomerAsRequestResponse(
                    1L, "삼성", "바람의나라 AF17", "냉방 불량", "에어컨이 작동은 되는데 냉방이 안돼요",
                    "서울특별시 강남구 테헤란로 123", LocalDate.of(2024, 6, 20), "14:00",
                    "COMPLETED", LocalDateTime.now());
        }

        @Test
        @DisplayName("TC-C-1: 인증된 AGENCY — 200 OK + JSON 배열 검증")
        void success_200_returnsAsRequestList() throws Exception {
            given(agencyCustomerService.getCustomerAsRequests(any(), eq(CUSTOMER_ID)))
                    .willReturn(List.of(sampleAsRequest()));

            mockMvc.perform(get("/api/agency/customers/{userId}/as-requests", CUSTOMER_ID))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$[0].requestId").value(1))
                    .andExpect(jsonPath("$[0].applianceBrand").value("삼성"))
                    .andExpect(jsonPath("$[0].status").value("COMPLETED"));
        }

        @Test
        @DisplayName("TC-C-2: 인증 없음 — 401")
        void fail_anonymous_returns401() throws Exception {
            mockMvc.perform(get("/api/agency/customers/{userId}/as-requests", CUSTOMER_ID).with(anonymous()))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("TC-C-3: Service에서 NoSuchElementException 발생 — 404")
        void fail_userNotFound_404() throws Exception {
            given(agencyCustomerService.getCustomerAsRequests(any(), eq(999L)))
                    .willThrow(new NoSuchElementException("해당 고객 정보를 찾을 수 없습니다."));

            mockMvc.perform(get("/api/agency/customers/{userId}/as-requests", 999L))
                    .andExpect(status().isNotFound());
        }

        @Test
        @DisplayName("TC-C-4: Service에서 IllegalAccessException 발생 — 401")
        void fail_noAccess_401() throws Exception {
            given(agencyCustomerService.getCustomerAsRequests(any(), eq(CUSTOMER_ID)))
                    .willThrow(new IllegalAccessException("본인 대행사로부터 서비스를 받은 고객만 조회할 수 있습니다."));

            mockMvc.perform(get("/api/agency/customers/{userId}/as-requests", CUSTOMER_ID))
                    .andExpect(status().isUnauthorized());
        }
    }

    @Nested
    @DisplayName("GET /api/agency/customers/{userId}/payments — 고객 결제 내역 조회")
    class GetCustomerPayments {

        private static final Long CUSTOMER_ID = 1L;

        private AgencyCustomerPaymentResponse samplePayment() {
            return new AgencyCustomerPaymentResponse(
                    1L, 1L, "삼성", "바람의나라 AF17", 85000, "KAKAO", "SUCCESS",
                    LocalDateTime.of(2024, 6, 20, 15, 30), LocalDateTime.of(2024, 6, 18, 10, 0));
        }

        @Test
        @DisplayName("TC-C-1: 인증된 AGENCY — 200 OK + JSON 배열 검증")
        void success_200_returnsPaymentList() throws Exception {
            given(agencyCustomerService.getCustomerPayments(any(), eq(CUSTOMER_ID)))
                    .willReturn(List.of(samplePayment()));

            mockMvc.perform(get("/api/agency/customers/{userId}/payments", CUSTOMER_ID))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$[0].paymentId").value(1))
                    .andExpect(jsonPath("$[0].amount").value(85000))
                    .andExpect(jsonPath("$[0].status").value("SUCCESS"));
        }

        @Test
        @DisplayName("TC-C-2: 인증 없음 — 401")
        void fail_anonymous_returns401() throws Exception {
            mockMvc.perform(get("/api/agency/customers/{userId}/payments", CUSTOMER_ID).with(anonymous()))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("TC-C-3: Service에서 NoSuchElementException 발생 — 404")
        void fail_userNotFound_404() throws Exception {
            given(agencyCustomerService.getCustomerPayments(any(), eq(999L)))
                    .willThrow(new NoSuchElementException("해당 고객 정보를 찾을 수 없습니다."));

            mockMvc.perform(get("/api/agency/customers/{userId}/payments", 999L))
                    .andExpect(status().isNotFound());
        }

        @Test
        @DisplayName("TC-C-4: Service에서 IllegalAccessException 발생 — 401")
        void fail_noAccess_401() throws Exception {
            given(agencyCustomerService.getCustomerPayments(any(), eq(CUSTOMER_ID)))
                    .willThrow(new IllegalAccessException("본인 대행사로부터 서비스를 받은 고객만 조회할 수 있습니다."));

            mockMvc.perform(get("/api/agency/customers/{userId}/payments", CUSTOMER_ID))
                    .andExpect(status().isUnauthorized());
        }
    }
}
