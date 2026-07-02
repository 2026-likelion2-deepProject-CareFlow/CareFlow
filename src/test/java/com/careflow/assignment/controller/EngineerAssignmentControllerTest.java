package com.careflow.assignment.controller;

import com.careflow.assignment.dto.AssignmentRejectRequest;
import com.careflow.assignment.service.EngineerAssignmentService;
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
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.willDoNothing;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(EngineerAssignmentController.class)
@Import({SecurityConfig.class, PasswordEncoderConfig.class})
@DisplayName("EngineerAssignmentController 단위 테스트")
class EngineerAssignmentControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;

    @MockitoBean private EngineerAssignmentService engineerAssignmentService;
    @MockitoBean private JwtProvider jwtProvider;
    @MockitoBean private StringRedisTemplate stringRedisTemplate; // 🌟 핵심 추가: Redis Mock 객체 주입!
    @MockitoBean private JpaMetamodelMappingContext jpaMetamodelMappingContext;
    @MockitoBean private CustomOAuth2UserService customOAuth2UserService;
    @MockitoBean private OAuth2LoginSuccessHandler oAuth2LoginSuccessHandler;
    @MockitoBean private ClientRegistrationRepository clientRegistrationRepository;

    private RequestPostProcessor engineerAuth;
    private RequestPostProcessor customerAuth;

    @BeforeEach
    void setUp() {
        engineerAuth = buildAuth(1L, "engineer@test.com", "ENGINEER");
        customerAuth = buildAuth(2L, "customer@test.com", "CUSTOMER");
    }

    private RequestPostProcessor buildAuth(Long userId, String email, String role) {
        CustomUserDetails userDetails = new CustomUserDetails(userId, email, "", role, 100L);
        return SecurityMockMvcRequestPostProcessors.authentication(
                new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities())
        );
    }

    @Nested
    @DisplayName("PUT /api/engineer/assignments/{assignmentId}/accept - 수락")
    class AcceptAssignment {
        @Test
        @DisplayName("성공: ENGINEER 역할 요청 - 200 OK")
        void success_200() throws Exception {
            willDoNothing().given(engineerAssignmentService).acceptAssignment(any(), any());

            mockMvc.perform(put("/api/engineer/assignments/1/accept").with(engineerAuth))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("실패: CUSTOMER 역할 요청 - 401/403 권한 없음")
        void fail_notEngineer_403() throws Exception {
            mockMvc.perform(put("/api/engineer/assignments/1/accept").with(customerAuth))
                    .andExpect(status().isForbidden());
        }
    }

    @Nested
    @DisplayName("PUT /api/engineer/assignments/{assignmentId}/reject - 거절")
    class RejectAssignment {
        @Test
        @DisplayName("성공: 올바른 거절 사유 - 200 OK")
        void success_200() throws Exception {
            AssignmentRejectRequest request = new AssignmentRejectRequest("일정이 맞지 않습니다.");

            mockMvc.perform(put("/api/engineer/assignments/1/reject")
                            .with(engineerAuth)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("유효성 실패: 거절 사유 누락 - 400 Bad Request")
        void fail_emptyReason_400() throws Exception {
            AssignmentRejectRequest request = new AssignmentRejectRequest("");

            mockMvc.perform(put("/api/engineer/assignments/1/reject")
                            .with(engineerAuth)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest());
        }
    }
}