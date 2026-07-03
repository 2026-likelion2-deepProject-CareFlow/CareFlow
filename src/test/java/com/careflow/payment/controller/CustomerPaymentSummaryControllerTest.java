package com.careflow.payment.controller;

import com.careflow.auth.security.CustomOAuth2UserService;
import com.careflow.auth.security.CustomUserDetails;
import com.careflow.auth.security.JwtProvider;
import com.careflow.auth.security.OAuth2LoginSuccessHandler;
import com.careflow.common.config.PasswordEncoderConfig;
import com.careflow.common.config.SecurityConfig;
import com.careflow.payment.dto.CustomerMonthlyPaymentResponse;
import com.careflow.payment.dto.CustomerPaymentSummaryResponse;
import com.careflow.payment.dto.PaymentResponse;
import com.careflow.payment.service.PaymentService;
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
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.anonymous;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(CustomerPaymentSummaryController.class)
@Import({SecurityConfig.class, PasswordEncoderConfig.class})
@DisplayName("CustomerPaymentSummaryController 단위 테스트")
class CustomerPaymentSummaryControllerTest {

    @Autowired private MockMvc mockMvc;

    @MockitoBean private PaymentService paymentService;
    @MockitoBean private JwtProvider jwtProvider;
    @MockitoBean private JpaMetamodelMappingContext jpaMetamodelMappingContext;
    @MockitoBean private CustomOAuth2UserService customOAuth2UserService;
    @MockitoBean private OAuth2LoginSuccessHandler oAuth2LoginSuccessHandler;
    @MockitoBean private ClientRegistrationRepository clientRegistrationRepository;
    @MockitoBean private StringRedisTemplate stringRedisTemplate;

    private static final Long CUSTOMER_USER_ID = 1L;
    private static final Long OTHER_CUSTOMER_USER_ID = 2L;

    // 고객 권한 픽스처
    private RequestPostProcessor customerAuth;

    @BeforeEach
    void setUp() {
        customerAuth = SecurityMockMvcRequestPostProcessors.authentication(
                new UsernamePasswordAuthenticationToken(
                        new CustomUserDetails(CUSTOMER_USER_ID, "customer@test.com", "pw", "CUSTOMER", null),
                        null, List.of(new SimpleGrantedAuthority("CUSTOMER"))
                )
        );
    }

    @Nested
    @DisplayName("GET /api/customer/payments/summary — 고객 결제 요약 KPI 조회")
    class GetPaymentSummary {

        @Test
        @DisplayName("성공: 로그인한 본인의 요약 KPI 반환 — 200 OK")
        void success_200() throws Exception {
            given(paymentService.getPaymentSummary(CUSTOMER_USER_ID))
                    .willReturn(new CustomerPaymentSummaryResponse(150_000L, 50_000L, 2L, 60_000L, 90_000L));

            mockMvc.perform(get("/api/customer/payments/summary").with(customerAuth))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.totalAmount").value(150_000))
                    .andExpect(jsonPath("$.thisMonthAmount").value(50_000))
                    .andExpect(jsonPath("$.unpaidCount").value(2))
                    .andExpect(jsonPath("$.partsAmount").value(60_000))
                    .andExpect(jsonPath("$.otherAmount").value(90_000));

            // 클라이언트 입력이 아닌 인증된 본인의 userId로만 조회했는지 검증
            verify(paymentService).getPaymentSummary(eq(CUSTOMER_USER_ID));
        }

        @Test
        @DisplayName("성공: 결제/미결제 내역이 없는 고객은 전부 0으로 반환 — 200 OK")
        void success_zeroValues_200() throws Exception {
            given(paymentService.getPaymentSummary(OTHER_CUSTOMER_USER_ID))
                    .willReturn(new CustomerPaymentSummaryResponse(0L, 0L, 0L, 0L, 0L));

            RequestPostProcessor otherCustomerAuth = SecurityMockMvcRequestPostProcessors.authentication(
                    new UsernamePasswordAuthenticationToken(
                            new CustomUserDetails(OTHER_CUSTOMER_USER_ID, "other@test.com", "pw", "CUSTOMER", null),
                            null, List.of(new SimpleGrantedAuthority("CUSTOMER"))
                    )
            );

            mockMvc.perform(get("/api/customer/payments/summary").with(otherCustomerAuth))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.totalAmount").value(0))
                    .andExpect(jsonPath("$.thisMonthAmount").value(0))
                    .andExpect(jsonPath("$.unpaidCount").value(0))
                    .andExpect(jsonPath("$.partsAmount").value(0))
                    .andExpect(jsonPath("$.otherAmount").value(0));
        }

        @Test
        @DisplayName("예외 테스트: 인증 토큰 없음 — 401 Unauthorized")
        void fail_noAuth_401() throws Exception {
            mockMvc.perform(get("/api/customer/payments/summary").with(anonymous()))
                    .andExpect(status().isUnauthorized());
        }
    }

    @Nested
    @DisplayName("GET /api/customer/payments/monthly — 고객 월별 결제액 추이 조회")
    class GetMonthlyPayments {

        @Test
        @DisplayName("성공: 로그인한 본인의 최근 6개월 추이 반환 — 200 OK")
        void success_200() throws Exception {
            List<CustomerMonthlyPaymentResponse> stub = List.of(
                    new CustomerMonthlyPaymentResponse("2026-02", 0L),
                    new CustomerMonthlyPaymentResponse("2026-03", 30_000L),
                    new CustomerMonthlyPaymentResponse("2026-04", 0L),
                    new CustomerMonthlyPaymentResponse("2026-05", 45_000L),
                    new CustomerMonthlyPaymentResponse("2026-06", 0L),
                    new CustomerMonthlyPaymentResponse("2026-07", 50_000L)
            );
            given(paymentService.getMonthlyPayments(CUSTOMER_USER_ID)).willReturn(stub);

            mockMvc.perform(get("/api/customer/payments/monthly").with(customerAuth))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.length()").value(6))
                    .andExpect(jsonPath("$[0].yearMonth").value("2026-02"))
                    .andExpect(jsonPath("$[0].amount").value(0))
                    .andExpect(jsonPath("$[1].yearMonth").value("2026-03"))
                    .andExpect(jsonPath("$[1].amount").value(30_000))
                    .andExpect(jsonPath("$[5].yearMonth").value("2026-07"))
                    .andExpect(jsonPath("$[5].amount").value(50_000));

            // 클라이언트 입력이 아닌 인증된 본인의 userId로만 조회했는지 검증
            verify(paymentService).getMonthlyPayments(eq(CUSTOMER_USER_ID));
        }

        @Test
        @DisplayName("예외 테스트: 인증 토큰 없음 — 401 Unauthorized")
        void fail_noAuth_401() throws Exception {
            mockMvc.perform(get("/api/customer/payments/monthly").with(anonymous()))
                    .andExpect(status().isUnauthorized());
        }
    }

    @Nested
    @DisplayName("GET /api/customer/payments — 고객 결제 내역 전체 조회")
    class GetPaymentList {

        @Test
        @DisplayName("성공: 로그인한 본인의 결제 내역을 상태 필터 없이 최신순으로 반환 — 200 OK")
        void success_200() throws Exception {
            List<PaymentResponse> stub = List.of(
                    new PaymentResponse(3L, 30L, 50_000, "SUCCESS", "MOCK", LocalDateTime.of(2026, 7, 1, 10, 0)),
                    new PaymentResponse(2L, 20L, 30_000, "FAILED", "MOCK", null),
                    new PaymentResponse(1L, 10L, 45_000, "REFUNDED", "MOCK", LocalDateTime.of(2026, 5, 1, 9, 0))
            );
            given(paymentService.getPaymentList(CUSTOMER_USER_ID)).willReturn(stub);

            mockMvc.perform(get("/api/customer/payments").with(customerAuth))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.length()").value(3))
                    .andExpect(jsonPath("$[0].paymentId").value(3))
                    .andExpect(jsonPath("$[0].status").value("SUCCESS"))
                    .andExpect(jsonPath("$[1].status").value("FAILED"))
                    .andExpect(jsonPath("$[1].paidAt").doesNotExist())
                    .andExpect(jsonPath("$[2].status").value("REFUNDED"));

            // 클라이언트 입력이 아닌 인증된 본인의 userId로만 조회했는지 검증
            verify(paymentService).getPaymentList(eq(CUSTOMER_USER_ID));
        }

        @Test
        @DisplayName("성공: 결제 내역이 없는 고객은 빈 배열 반환 — 200 OK")
        void success_empty_200() throws Exception {
            given(paymentService.getPaymentList(OTHER_CUSTOMER_USER_ID)).willReturn(List.of());

            RequestPostProcessor otherCustomerAuth = SecurityMockMvcRequestPostProcessors.authentication(
                    new UsernamePasswordAuthenticationToken(
                            new CustomUserDetails(OTHER_CUSTOMER_USER_ID, "other@test.com", "pw", "CUSTOMER", null),
                            null, List.of(new SimpleGrantedAuthority("CUSTOMER"))
                    )
            );

            mockMvc.perform(get("/api/customer/payments").with(otherCustomerAuth))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.length()").value(0));
        }

        @Test
        @DisplayName("예외 테스트: 인증 토큰 없음 — 401 Unauthorized")
        void fail_noAuth_401() throws Exception {
            mockMvc.perform(get("/api/customer/payments").with(anonymous()))
                    .andExpect(status().isUnauthorized());
        }
    }
}
