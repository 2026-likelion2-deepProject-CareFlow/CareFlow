package com.careflow.account_requests.controller;

import com.careflow.account_requests.controller.AgencyAccountRequestController;
import com.careflow.account_requests.dto.AccountRequestReject;
import com.careflow.account_requests.service.AccountRequestsService;
import com.careflow.account_requests.service.AgencyAccountRequestService;
import com.careflow.agency.service.AgenciesService;
import com.careflow.auth.security.CustomOAuth2UserService;
import com.careflow.auth.security.CustomUserDetails;
import com.careflow.auth.security.JwtProvider;
import com.careflow.auth.security.OAuth2LoginSuccessHandler;
import com.careflow.common.config.PasswordEncoderConfig;
import com.careflow.common.config.SecurityConfig;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import com.careflow.user.service.UserService;
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
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.NoSuchElementException;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.willDoNothing;
import static org.mockito.BDDMockito.willThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AgencyAccountRequestController.class)
@Import({SecurityConfig.class, PasswordEncoderConfig.class})
@DisplayName("AgencyAccountRequestController 테스트")
class AccountRequestsControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private AgencyAccountRequestService agencyAccountRequestService;
    @MockitoBean
    private AccountRequestsService accountRequestsService;
    @MockitoBean
    private AgenciesService agenciesService;
    @MockitoBean
    private UserService userService;
    @MockitoBean
    private JwtProvider jwtProvider;
    @MockitoBean
    private JpaMetamodelMappingContext jpaMetamodelMappingContext;
    // develop 병합으로 추가된 OAuth2 의존성 — SecurityConfig 생성 및 oauth2Login() 설정에 필요
    @MockitoBean
    private CustomOAuth2UserService customOAuth2UserService;
    @MockitoBean
    private OAuth2LoginSuccessHandler oAuth2LoginSuccessHandler;
    @MockitoBean
    private ClientRegistrationRepository clientRegistrationRepository;
    @MockitoBean
    private org.springframework.data.redis.core.StringRedisTemplate stringRedisTemplate;

    // 역할별 인증 Principal 헬퍼
    private RequestPostProcessor adminAuth;
    private RequestPostProcessor agencyAuth;
    private RequestPostProcessor customerAuth;

    @BeforeEach
    void setUp() {
        adminAuth = buildAuth(1L, "admin@careflow.com", "ADMIN");
        agencyAuth = buildAuth(2L, "agency@careflow.com", "AGENCY");
        customerAuth = buildAuth(3L, "customer@careflow.com", "CUSTOMER");
    }

    // SecurityContext 에 CustomUserDetails 를 직접 주입하는 헬퍼
    private RequestPostProcessor buildAuth(Long userId, String email, String role) {
        CustomUserDetails userDetails = new CustomUserDetails(userId, email, "", role, null);
        return SecurityMockMvcRequestPostProcessors.authentication(
                new UsernamePasswordAuthenticationToken(
                        userDetails, null, List.of(new SimpleGrantedAuthority(role))
                )
        );
    }

    // ─────────────────────────────────────────────
    //  POST /api/account-requests/agency/approval
    // ─────────────────────────────────────────────
    @Nested
    @DisplayName("POST /api/account-requests/agency/approval — 대행사 계정 생성 요청 승인")
    class ApproveAgencyAccount {

        @Test
        @DisplayName("성공: ADMIN이 슈퍼 계정 요청 승인 — 204 No Content 반환")
        void approve_byAdmin_success() throws Exception {
            willDoNothing().given(agencyAccountRequestService)
                    .approveAgencyAccount(any(CustomUserDetails.class), eq(1L));

            mockMvc.perform(post("/api/account-requests/agency/approval")
                            .param("accountId", "1")
                            .with(adminAuth))
                    .andExpect(status().isNoContent());
        }

        @Test
        @DisplayName("성공: AGENCY 슈퍼 계정이 자기 대행사 일반 관리자 요청 승인 — 204 No Content 반환")
        void approve_byAgencySuper_success() throws Exception {
            willDoNothing().given(agencyAccountRequestService)
                    .approveAgencyAccount(any(CustomUserDetails.class), eq(2L));

            mockMvc.perform(post("/api/account-requests/agency/approval")
                            .param("accountId", "2")
                            .with(agencyAuth))
                    .andExpect(status().isNoContent());
        }

        @Test
        @DisplayName("실패: CUSTOMER 역할로 승인 시도 — 401 Unauthorized 반환")
        void approve_byCustomer_401() throws Exception {
            mockMvc.perform(post("/api/account-requests/agency/approval")
                            .param("accountId", "1")
                            .with(customerAuth))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("실패: AGENCY가 다른 대행사 요청 승인 시도 — 401 Unauthorized 반환")
        void approve_byOtherAgency_401() throws Exception {
            willThrow(new IllegalAccessException("자신이 소속된 대행사의 요청만 승인할 수 있습니다."))
                    .given(agencyAccountRequestService)
                    .approveAgencyAccount(any(CustomUserDetails.class), eq(99L));

            mockMvc.perform(post("/api/account-requests/agency/approval")
                            .param("accountId", "99")
                            .with(agencyAuth))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("실패: AGENCY가 슈퍼 계정 요청(PENDING 대행사) 승인 시도 — 401 Unauthorized 반환")
        void approve_agencyTriesSuperRequest_401() throws Exception {
            willThrow(new IllegalAccessException("슈퍼 계정 요청은 CareFlow 관리자만 승인할 수 있습니다."))
                    .given(agencyAccountRequestService)
                    .approveAgencyAccount(any(CustomUserDetails.class), eq(5L));

            mockMvc.perform(post("/api/account-requests/agency/approval")
                            .param("accountId", "5")
                            .with(agencyAuth))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("실패: 존재하지 않는 요청 ID — 404 Not Found 반환")
        void approve_notFound_404() throws Exception {
            willThrow(new NoSuchElementException("요청 정보를 찾을 수 없습니다."))
                    .given(agencyAccountRequestService)
                    .approveAgencyAccount(any(CustomUserDetails.class), eq(999L));

            mockMvc.perform(post("/api/account-requests/agency/approval")
                            .param("accountId", "999")
                            .with(adminAuth))
                    .andExpect(status().isNotFound());
        }

        @Test
        @DisplayName("실패: 이미 처리된 요청 승인 시도 — 400 Bad Request 반환")
        void approve_alreadyProcessed_400() throws Exception {
            willThrow(new IllegalArgumentException("이미 승인되었거나 거부된 요청입니다."))
                    .given(agencyAccountRequestService)
                    .approveAgencyAccount(any(CustomUserDetails.class), eq(1L));

            mockMvc.perform(post("/api/account-requests/agency/approval")
                            .param("accountId", "1")
                            .with(adminAuth))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("실패: 인증 없이 요청 — 401 Unauthorized 반환")
        void approve_noAuth_403() throws Exception {
            mockMvc.perform(post("/api/account-requests/agency/approval")
                            .param("accountId", "1"))
                    .andExpect(status().isUnauthorized());
        }
    }

    // ─────────────────────────────────────────────
    //  POST /api/account-requests/agency/rejection
    // ─────────────────────────────────────────────
    @Nested
    @DisplayName("POST /api/account-requests/agency/rejection — 대행사 계정 생성 요청 거부")
    class RejectAgencyAccount {

        private AccountRequestReject validRejectBody;
        private String validRejectJson;

        @BeforeEach
        void setUp() throws Exception {
            validRejectBody = new AccountRequestReject("요청 내용이 미흡합니다.");
            validRejectJson = objectMapper.writeValueAsString(validRejectBody);
        }

        @Test
        @DisplayName("성공: ADMIN이 슈퍼 계정 요청 거부 — 204 No Content 반환")
        void reject_byAdmin_success() throws Exception {
            willDoNothing().given(agencyAccountRequestService)
                    .rejectAgencyAccount(any(CustomUserDetails.class), eq(1L), any(AccountRequestReject.class));

            mockMvc.perform(post("/api/account-requests/agency/rejection")
                            .param("accountId", "1")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(validRejectJson)
                            .with(adminAuth))
                    .andExpect(status().isNoContent());
        }

        @Test
        @DisplayName("성공: AGENCY 슈퍼 계정이 자기 대행사 일반 관리자 요청 거부 — 204 No Content 반환")
        void reject_byAgencySuper_success() throws Exception {
            willDoNothing().given(agencyAccountRequestService)
                    .rejectAgencyAccount(any(CustomUserDetails.class), eq(2L), any(AccountRequestReject.class));

            mockMvc.perform(post("/api/account-requests/agency/rejection")
                            .param("accountId", "2")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(validRejectJson)
                            .with(agencyAuth))
                    .andExpect(status().isNoContent());
        }

        @Test
        @DisplayName("실패: CUSTOMER 역할로 거부 시도 — 401 Unauthorized 반환")
        void reject_byCustomer_401() throws Exception {
            mockMvc.perform(post("/api/account-requests/agency/rejection")
                            .param("accountId", "1")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(validRejectJson)
                            .with(customerAuth))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("실패: 존재하지 않는 요청 ID 거부 — 404 Not Found 반환")
        void reject_notFound_404() throws Exception {
            willThrow(new NoSuchElementException("요청 정보를 찾을 수 없습니다."))
                    .given(agencyAccountRequestService)
                    .rejectAgencyAccount(any(CustomUserDetails.class), eq(999L), any(AccountRequestReject.class));

            mockMvc.perform(post("/api/account-requests/agency/rejection")
                            .param("accountId", "999")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(validRejectJson)
                            .with(adminAuth))
                    .andExpect(status().isNotFound());
        }

        @Test
        @DisplayName("실패: 이미 승인된 요청 거부 시도 — 401 Unauthorized 반환")
        void reject_alreadyApproved_401() throws Exception {
            willThrow(new IllegalAccessException("이미 등록 승인된 대행사 입니다."))
                    .given(agencyAccountRequestService)
                    .rejectAgencyAccount(any(CustomUserDetails.class), eq(1L), any(AccountRequestReject.class));

            mockMvc.perform(post("/api/account-requests/agency/rejection")
                            .param("accountId", "1")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(validRejectJson)
                            .with(adminAuth))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("실패: 이미 거부된 요청 재거부 시도 — 401 Unauthorized 반환")
        void reject_alreadyRejected_401() throws Exception {
            willThrow(new IllegalAccessException("이미 등록 거부된 대행사 입니다."))
                    .given(agencyAccountRequestService)
                    .rejectAgencyAccount(any(CustomUserDetails.class), eq(1L), any(AccountRequestReject.class));

            mockMvc.perform(post("/api/account-requests/agency/rejection")
                            .param("accountId", "1")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(validRejectJson)
                            .with(adminAuth))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("실패: 인증 없이 요청 — 401 Unauthorized 반환")
        void reject_noAuth_403() throws Exception {
            mockMvc.perform(post("/api/account-requests/agency/rejection")
                            .param("accountId", "1")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(validRejectJson))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("유효성 실패: 거부 사유가 255자 초과 — 400 Bad Request 반환")
        void reject_reasonTooLong_400() throws Exception {
            AccountRequestReject longReason = new AccountRequestReject("가".repeat(256));

            mockMvc.perform(post("/api/account-requests/agency/rejection")
                            .param("accountId", "1")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(longReason))
                            .with(adminAuth))
                    .andExpect(status().isBadRequest());
        }
    }
}
