package com.careflow.notification.controller;

import com.careflow.auth.security.CustomOAuth2UserService;
import com.careflow.auth.security.CustomUserDetails;
import com.careflow.auth.security.JwtProvider;
import com.careflow.auth.security.OAuth2LoginSuccessHandler;
import com.careflow.common.config.PasswordEncoderConfig;
import com.careflow.common.config.SecurityConfig;
import com.careflow.notification.dto.NotificationResponse;
import com.careflow.notification.service.NotificationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.mapping.JpaMetamodelMappingContext;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.anonymous;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(EngineerNotificationController.class)
@Import({SecurityConfig.class, PasswordEncoderConfig.class})
@DisplayName("EngineerNotificationController 단위 테스트")
class EngineerNotificationControllerTest {

    @Autowired private MockMvc mockMvc;

    @MockitoBean private NotificationService notificationService;
    @MockitoBean private JwtProvider jwtProvider;
    @MockitoBean private JpaMetamodelMappingContext jpaMetamodelMappingContext;
    @MockitoBean private CustomOAuth2UserService customOAuth2UserService;
    @MockitoBean private OAuth2LoginSuccessHandler oAuth2LoginSuccessHandler;
    @MockitoBean private ClientRegistrationRepository clientRegistrationRepository;

    private static final Long ENGINEER_USER_ID = 10L;

    @BeforeEach
    void setUpAuth() {
        CustomUserDetails userDetails = new CustomUserDetails(
                ENGINEER_USER_ID, "engineer@test.com", "pw", "ENGINEER", null);
        UsernamePasswordAuthenticationToken auth =
                new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities());
        SecurityContextHolder.getContext().setAuthentication(auth);
    }

    @Nested
    @DisplayName("GET /api/engineer/notifications — 수신함 목록 조회")
    class GetNotifications {

        @Test
        @DisplayName("성공: 페이징된 알림 목록 반환 (200 OK)")
        void success_200() throws Exception {
            NotificationResponse stubResponse = NotificationResponse.builder()
                    .id(1L).notificationId("NOT-20240618-001")
                    .type("AS_STATUS").title("배정 알림")
                    .body("냉장고 수리 작업이 배정되었습니다.")
                    .channel("SSE").createdAt("2024.06.18 09:30").build();

            PageImpl<NotificationResponse> mockPage = new PageImpl<>(List.of(stubResponse), PageRequest.of(0, 10), 1);

            given(notificationService.getNotifications(eq(ENGINEER_USER_ID), any()))
                    .willReturn(mockPage);

            mockMvc.perform(get("/api/engineer/notifications")
                            .param("page", "0")
                            .param("size", "10"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content.length()").value(1))
                    .andExpect(jsonPath("$.content[0].notificationId").value("NOT-20240618-001"))
                    .andExpect(jsonPath("$.content[0].title").value("배정 알림"))
                    .andExpect(jsonPath("$.totalElements").value(1));
        }

        @Test
        @DisplayName("실패: 인증 토큰 없음 — 401 Unauthorized")
        void fail_noAuth_401() throws Exception {
            mockMvc.perform(get("/api/engineer/notifications").with(anonymous()))
                    .andExpect(status().isUnauthorized());
        }
    }
}