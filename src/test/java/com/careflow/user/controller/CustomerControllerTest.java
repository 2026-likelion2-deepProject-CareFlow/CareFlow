package com.careflow.user.controller;

import com.careflow.as_request.service.AsRequestService;
import com.careflow.auth.security.CustomOAuth2UserService;
import com.careflow.auth.security.CustomUserDetails;
import com.careflow.auth.security.JwtProvider;
import com.careflow.auth.security.OAuth2LoginSuccessHandler;
import com.careflow.common.config.PasswordEncoderConfig;
import com.careflow.common.config.SecurityConfig;
import com.careflow.engineer.dto.CustomerEngineerAvailabilityResponse;
import com.careflow.engineer.dto.CustomerEngineerSummaryResponse;
import com.careflow.engineer.service.CustomerEngineerQueryService;
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

import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.given;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.anonymous;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(CustomerController.class)
@Import({SecurityConfig.class, PasswordEncoderConfig.class})
@DisplayName("CustomerController 통합 테스트")
class CustomerControllerTest {

    @Autowired private MockMvc mockMvc;

    @MockitoBean private AsRequestService asRequestService;
    @MockitoBean private CustomerEngineerQueryService customerEngineerQueryService;
    @MockitoBean private JwtProvider jwtProvider;
    @MockitoBean private JpaMetamodelMappingContext jpaMetamodelMappingContext;
    @MockitoBean private CustomOAuth2UserService customOAuth2UserService;
    @MockitoBean private OAuth2LoginSuccessHandler oAuth2LoginSuccessHandler;
    @MockitoBean private ClientRegistrationRepository clientRegistrationRepository;

    private static final Long CUSTOMER_USER_ID = 1L;

    @BeforeEach
    void setUpAuth() {
        CustomUserDetails userDetails = new CustomUserDetails(
                CUSTOMER_USER_ID, "customer@test.com", "pw", "ROLE_CUSTOMER", null);
        UsernamePasswordAuthenticationToken auth =
                new UsernamePasswordAuthenticationToken(
                        userDetails, null, userDetails.getAuthorities());
        SecurityContextHolder.getContext().setAuthentication(auth);
    }

    // ══════════════════════════════════════════════════════════════
    //  GET /api/customers/{customerId}/engineers/available
    // ══════════════════════════════════════════════════════════════
    @Nested
    @DisplayName("GET /api/customers/{customerId}/engineers/available — 후보 기사 목록 조회")
    class GetAvailableEngineers {

        @Test
        @DisplayName("성공: 조건에 맞는 기사 목록 — 200 OK")
        void success_200_returnsList() throws Exception {
            CustomerEngineerSummaryResponse stub = CustomerEngineerSummaryResponse.builder()
                    .engineerId(10L)
                    .name("김민수")
                    .rating(4.8)
                    .brands(List.of("LG", "삼성"))
                    .skills("냉장고")
                    .profileImageUrl(null)
                    .build();

            given(customerEngineerQueryService.getAvailableEngineers(1, "LG", "냉장고"))
                    .willReturn(List.of(stub));

            mockMvc.perform(get("/api/customers/{customerId}/engineers/available", CUSTOMER_USER_ID)
                            .param("regionId", "1")
                            .param("brand", "LG")
                            .param("skill", "냉장고"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.length()").value(1))
                    .andExpect(jsonPath("$[0].engineerId").value(10))
                    .andExpect(jsonPath("$[0].name").value("김민수"))
                    .andExpect(jsonPath("$[0].rating").value(4.8))
                    .andExpect(jsonPath("$[0].brands[0]").value("LG"));
        }

        @Test
        @DisplayName("성공: 조건에 맞는 기사 없음 — 200 OK, 빈 배열")
        void success_200_emptyList() throws Exception {
            given(customerEngineerQueryService.getAvailableEngineers(eq(1), isNull(), isNull()))
                    .willReturn(List.of());

            mockMvc.perform(get("/api/customers/{customerId}/engineers/available", CUSTOMER_USER_ID)
                            .param("regionId", "1"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.length()").value(0));
        }

        @Test
        @DisplayName("실패: regionId 누락 — 400 Bad Request")
        void fail_missingRegionId_400() throws Exception {
            mockMvc.perform(get("/api/customers/{customerId}/engineers/available", CUSTOMER_USER_ID))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("실패: 존재하지 않는 지역 — 404 Not Found")
        void fail_regionNotFound_404() throws Exception {
            given(customerEngineerQueryService.getAvailableEngineers(eq(999), isNull(), isNull()))
                    .willThrow(new NoSuchElementException("존재하지 않는 지역입니다."));

            mockMvc.perform(get("/api/customers/{customerId}/engineers/available", CUSTOMER_USER_ID)
                            .param("regionId", "999"))
                    .andExpect(status().isNotFound());
        }

        @Test
        @DisplayName("실패: 인증 없음 — 401 Unauthorized")
        void fail_noAuth_401() throws Exception {
            mockMvc.perform(get("/api/customers/{customerId}/engineers/available", CUSTOMER_USER_ID)
                            .param("regionId", "1")
                            .with(anonymous()))
                    .andExpect(status().isUnauthorized());
        }
    }

    // ══════════════════════════════════════════════════════════════
    //  GET /api/customers/{customerId}/engineers/{engineerId}/availability
    // ══════════════════════════════════════════════════════════════
    @Nested
    @DisplayName("GET /api/customers/{customerId}/engineers/{engineerId}/availability — 가능 일정 조회")
    class GetEngineerAvailability {

        @Test
        @DisplayName("성공: 가능 일정 반환 — 200 OK")
        void success_200_returnsAvailability() throws Exception {
            CustomerEngineerAvailabilityResponse stub = CustomerEngineerAvailabilityResponse.builder()
                    .engineerId(10L)
                    .availableDates(Map.of("2026-07-01", List.of("09:00", "11:00")))
                    .build();

            given(customerEngineerQueryService.getEngineerAvailability(eq(10L), isNull(), isNull()))
                    .willReturn(stub);

            mockMvc.perform(get("/api/customers/{customerId}/engineers/{engineerId}/availability",
                            CUSTOMER_USER_ID, 10L))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.engineerId").value(10))
                    .andExpect(jsonPath("$.availableDates['2026-07-01'][0]").value("09:00"));
        }

        @Test
        @DisplayName("실패: 존재하지 않는 기사 — 404 Not Found")
        void fail_engineerNotFound_404() throws Exception {
            given(customerEngineerQueryService.getEngineerAvailability(eq(999L), isNull(), isNull()))
                    .willThrow(new NoSuchElementException("존재하지 않는 기사입니다."));

            mockMvc.perform(get("/api/customers/{customerId}/engineers/{engineerId}/availability",
                            CUSTOMER_USER_ID, 999L))
                    .andExpect(status().isNotFound());
        }

        @Test
        @DisplayName("실패: 인증 없음 — 401 Unauthorized")
        void fail_noAuth_401() throws Exception {
            mockMvc.perform(get("/api/customers/{customerId}/engineers/{engineerId}/availability",
                            CUSTOMER_USER_ID, 10L)
                            .with(anonymous()))
                    .andExpect(status().isUnauthorized());
        }
    }
}
