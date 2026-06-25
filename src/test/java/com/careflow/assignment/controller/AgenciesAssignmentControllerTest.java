package com.careflow.assignment.controller;

import com.careflow.assignment.dto.AgencyAssignmentResponse;
import com.careflow.assignment.service.AgenciesAssignmentService;
import com.careflow.auth.security.CustomOAuth2UserService;
import com.careflow.auth.security.CustomUserDetails;
import com.careflow.auth.security.JwtProvider;
import com.careflow.auth.security.OAuth2LoginSuccessHandler;
import com.careflow.common.config.PasswordEncoderConfig;
import com.careflow.common.config.SecurityConfig;
import com.careflow.common.enums.AssignType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.mapping.JpaMetamodelMappingContext;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.BDDMockito.given;
import static org.mockito.ArgumentMatchers.any;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AgenciesAssignmentController.class)
@Import({SecurityConfig.class, PasswordEncoderConfig.class})
@DisplayName("AgenciesAssignmentController 테스트")
class AgenciesAssignmentControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AgenciesAssignmentService agenciesAssignmentService;
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

    // AGENCY 권한 테스트용 사용자
    private CustomUserDetails agencyUser() {
        return new CustomUserDetails(1L, "agency@test.com", "pw", "AGENCY");
    }

    // CUSTOMER 권한 테스트용 사용자 (권한 없음)
    private CustomUserDetails customerUser() {
        return new CustomUserDetails(2L, "customer@test.com", "pw", "CUSTOMER");
    }

    private AgencyAssignmentResponse sampleResponse() {
        return new AgencyAssignmentResponse(
                1L, 10L, 5L, "홍길동",
                3L, AssignType.MANUAL, "WAITING",
                LocalDateTime.of(2026, 6, 25, 10, 0),
                null, null, null
        );
    }

    @Nested
    @DisplayName("GET /api/agencies/assignment — 대행사 배차 내역 조회")
    class GetAgenciesAssignment {

        @Test
        @DisplayName("성공: 배차 내역 존재 — 200 OK 및 리스트 반환")
        void getAssignment_withData_200() throws Exception {
            given(agenciesAssignmentService.getAssignmentsByAgency(any()))
                    .willReturn(List.of(sampleResponse()));

            mockMvc.perform(get("/api/agencies/assignment")
                            .with(user(agencyUser())))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$[0].assignmentId").value(1L))
                    .andExpect(jsonPath("$[0].engineerName").value("홍길동"))
                    .andExpect(jsonPath("$[0].status").value("WAITING"));
        }

        @Test
        @DisplayName("성공: 배차 내역 없음 — 204 No Content 반환")
        void getAssignment_noData_204() throws Exception {
            given(agenciesAssignmentService.getAssignmentsByAgency(any()))
                    .willReturn(List.of());

            mockMvc.perform(get("/api/agencies/assignment")
                            .with(user(agencyUser())))
                    .andExpect(status().isNoContent());
        }

        @Test
        @DisplayName("실패: AGENCY 권한 없는 사용자 — 401 Unauthorized 반환")
        void getAssignment_noAuthority_401() throws Exception {
            given(agenciesAssignmentService.getAssignmentsByAgency(any()))
                    .willThrow(new IllegalAccessException("대행사 관리자 권한이 없습니다."));

            mockMvc.perform(get("/api/agencies/assignment")
                            .with(user(customerUser())))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("실패: 비인증 요청 — 401 Unauthorized 반환")
        void getAssignment_unauthenticated_401() throws Exception {
            mockMvc.perform(get("/api/agencies/assignment"))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("실패: 소속 대행사 없는 관리자 — 403 Forbidden 반환")
        void getAssignment_noAgency_403() throws Exception {
            given(agenciesAssignmentService.getAssignmentsByAgency(any()))
                    .willThrow(new IllegalStateException("소속 대행사 정보가 없습니다."));

            mockMvc.perform(get("/api/agencies/assignment")
                            .with(user(agencyUser())))
                    .andExpect(status().isForbidden());
        }
    }
}
