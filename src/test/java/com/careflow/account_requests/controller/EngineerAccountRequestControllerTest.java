package com.careflow.account_requests.controller;

import com.careflow.account_requests.dto.AccountRequestReject;
import com.careflow.account_requests.service.AccountRequestsService;
import com.careflow.account_requests.service.EngineerAccountRequestService;
import com.careflow.agency.entity.Agencies;
import com.careflow.agency.service.AgenciesService;
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
import static org.mockito.BDDMockito.willThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(EngineerAccountRequestController.class)
@Import({SecurityConfig.class, PasswordEncoderConfig.class})
@DisplayName("EngineerAccountRequestController 테스트")
class EngineerAccountRequestControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private EngineerAccountRequestService engineerAccountRequestService;
    @MockitoBean
    private AccountRequestsService accountRequestsService;
    @MockitoBean
    private AgenciesService agenciesService;
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
    @MockitoBean
    private org.springframework.data.redis.core.StringRedisTemplate stringRedisTemplate;

    private RequestPostProcessor agencyAuth;
    private RequestPostProcessor adminAuth;
    private RequestPostProcessor customerAuth;

    @BeforeEach
    void setUp() {
        agencyAuth = buildAuth(1L, "super@agency.com", "AGENCY");
        adminAuth = buildAuth(2L, "admin@careflow.com", "ADMIN");
        customerAuth = buildAuth(3L, "customer@test.com", "CUSTOMER");
    }

    private RequestPostProcessor buildAuth(Long userId, String email, String role) {
        CustomUserDetails userDetails = new CustomUserDetails(userId, email, "", role, null);
        return SecurityMockMvcRequestPostProcessors.authentication(
                new UsernamePasswordAuthenticationToken(
                        userDetails, null, List.of(new SimpleGrantedAuthority(role))
                )
        );
    }

    // ─────────────────────────────────────────────
    //  GET /api/account-requests/engineerlist
    // ─────────────────────────────────────────────
    @Nested
    @DisplayName("GET /api/account-requests/engineerlist — 수리기사 요청 목록 조회")
    class EngineerList {

        @Test
        @DisplayName("성공: AGENCY 슈퍼 계정 조회 — 200 OK 반환")
        void list_byAgency_200() throws Exception {
            Agencies mockAgency = Agencies.builder()
                    .agencyName("테스트대행사").businessNumber("BIZ-001")
                    .agencyAddress("서울").agencyFeeRate(5.0).build();

            given(agenciesService.findRepresentativeIdById(1L)).willReturn(mockAgency);
            given(accountRequestsService.findRequestByRoleAndStatus(any())).willReturn(List.of());

            mockMvc.perform(get("/api/account-requests/engineerlist").with(agencyAuth))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("실패: AGENCY 역할 아닌 사용자 — 401 Unauthorized 반환")
        void list_byAdmin_401() throws Exception {
            mockMvc.perform(get("/api/account-requests/engineerlist").with(adminAuth))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("실패: 인증 없이 요청 — 401 Unauthorized 반환")
        void list_noAuth_401() throws Exception {
            mockMvc.perform(get("/api/account-requests/engineerlist"))
                    .andExpect(status().isUnauthorized());
        }
    }

    // ─────────────────────────────────────────────
    //  POST /api/account-requests/engineer/approval
    // ─────────────────────────────────────────────
    @Nested
    @DisplayName("POST /api/account-requests/engineer/approval — 수리기사 계정 요청 승인")
    class ApproveEngineerAccount {

        @Test
        @DisplayName("성공: AGENCY 슈퍼 계정 승인 — 204 No Content 반환")
        void approve_byAgency_success() throws Exception {
            willDoNothing().given(engineerAccountRequestService)
                    .approveEngineerAccount(any(CustomUserDetails.class), eq(1L));

            mockMvc.perform(post("/api/account-requests/engineer/approval")
                            .param("accountId", "1")
                            .with(agencyAuth))
                    .andExpect(status().isNoContent());
        }

        @Test
        @DisplayName("실패: ADMIN 역할로 승인 시도 — 401 Unauthorized 반환")
        void approve_byAdmin_401() throws Exception {
            mockMvc.perform(post("/api/account-requests/engineer/approval")
                            .param("accountId", "1")
                            .with(adminAuth))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("실패: CUSTOMER 역할로 승인 시도 — 401 Unauthorized 반환")
        void approve_byCustomer_401() throws Exception {
            mockMvc.perform(post("/api/account-requests/engineer/approval")
                            .param("accountId", "1")
                            .with(customerAuth))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("실패: 존재하지 않는 요청 ID — 404 Not Found 반환")
        void approve_notFound_404() throws Exception {
            willThrow(new NoSuchElementException("요청 정보를 찾을 수 없습니다."))
                    .given(engineerAccountRequestService)
                    .approveEngineerAccount(any(CustomUserDetails.class), eq(999L));

            mockMvc.perform(post("/api/account-requests/engineer/approval")
                            .param("accountId", "999")
                            .with(agencyAuth))
                    .andExpect(status().isNotFound());
        }

        @Test
        @DisplayName("실패: 이미 처리된 요청 승인 시도 — 400 Bad Request 반환")
        void approve_alreadyProcessed_400() throws Exception {
            willThrow(new IllegalArgumentException("이미 승인되었거나 거부된 요청입니다."))
                    .given(engineerAccountRequestService)
                    .approveEngineerAccount(any(CustomUserDetails.class), eq(1L));

            mockMvc.perform(post("/api/account-requests/engineer/approval")
                            .param("accountId", "1")
                            .with(agencyAuth))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("실패: 인증 없이 요청 — 401 Unauthorized 반환")
        void approve_noAuth_401() throws Exception {
            mockMvc.perform(post("/api/account-requests/engineer/approval")
                            .param("accountId", "1"))
                    .andExpect(status().isUnauthorized());
        }
    }

    // ─────────────────────────────────────────────
    //  POST /api/account-requests/engineer/rejection
    // ─────────────────────────────────────────────
    @Nested
    @DisplayName("POST /api/account-requests/engineer/rejection — 수리기사 계정 요청 거부")
    class RejectEngineerAccount {

        private String validRejectJson;

        @BeforeEach
        void setUp() throws Exception {
            validRejectJson = objectMapper.writeValueAsString(new AccountRequestReject("요건 미충족으로 거부합니다."));
        }

        @Test
        @DisplayName("성공: AGENCY 슈퍼 계정 거부 — 204 No Content 반환")
        void reject_byAgency_success() throws Exception {
            willDoNothing().given(engineerAccountRequestService)
                    .rejectEngineerAccount(any(CustomUserDetails.class), eq(1L), any(AccountRequestReject.class));

            mockMvc.perform(post("/api/account-requests/engineer/rejection")
                            .param("accountId", "1")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(validRejectJson)
                            .with(agencyAuth))
                    .andExpect(status().isNoContent());
        }

        @Test
        @DisplayName("실패: ADMIN 역할로 거부 시도 — 401 Unauthorized 반환")
        void reject_byAdmin_401() throws Exception {
            mockMvc.perform(post("/api/account-requests/engineer/rejection")
                            .param("accountId", "1")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(validRejectJson)
                            .with(adminAuth))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("실패: 존재하지 않는 요청 ID 거부 — 404 Not Found 반환")
        void reject_notFound_404() throws Exception {
            willThrow(new NoSuchElementException("요청 정보를 찾을 수 없습니다."))
                    .given(engineerAccountRequestService)
                    .rejectEngineerAccount(any(CustomUserDetails.class), eq(999L), any(AccountRequestReject.class));

            mockMvc.perform(post("/api/account-requests/engineer/rejection")
                            .param("accountId", "999")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(validRejectJson)
                            .with(agencyAuth))
                    .andExpect(status().isNotFound());
        }

        @Test
        @DisplayName("실패: 이미 승인된 요청 거부 시도 — 401 Unauthorized 반환")
        void reject_alreadyApproved_401() throws Exception {
            willThrow(new IllegalAccessException("이미 등록 승인된 수리기사 계정입니다."))
                    .given(engineerAccountRequestService)
                    .rejectEngineerAccount(any(CustomUserDetails.class), eq(1L), any(AccountRequestReject.class));

            mockMvc.perform(post("/api/account-requests/engineer/rejection")
                            .param("accountId", "1")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(validRejectJson)
                            .with(agencyAuth))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("실패: 이미 거부된 요청 재거부 시도 — 401 Unauthorized 반환")
        void reject_alreadyRejected_401() throws Exception {
            willThrow(new IllegalAccessException("이미 등록 거부된 수리기사 계정입니다."))
                    .given(engineerAccountRequestService)
                    .rejectEngineerAccount(any(CustomUserDetails.class), eq(1L), any(AccountRequestReject.class));

            mockMvc.perform(post("/api/account-requests/engineer/rejection")
                            .param("accountId", "1")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(validRejectJson)
                            .with(agencyAuth))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("유효성 실패: 거부 사유가 255자 초과 — 400 Bad Request 반환")
        void reject_reasonTooLong_400() throws Exception {
            mockMvc.perform(post("/api/account-requests/engineer/rejection")
                            .param("accountId", "1")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(new AccountRequestReject("가".repeat(256))))
                            .with(agencyAuth))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("실패: 인증 없이 요청 — 401 Unauthorized 반환")
        void reject_noAuth_401() throws Exception {
            mockMvc.perform(post("/api/account-requests/engineer/rejection")
                            .param("accountId", "1")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(validRejectJson))
                    .andExpect(status().isUnauthorized());
        }
    }
}
