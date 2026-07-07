package com.careflow.agency.controller;

import com.careflow.agency.dto.request.SecurityToggleRequest;
import com.careflow.agency.entity.Agencies;
import com.careflow.agency.repository.AgenciesRepository;
import com.careflow.auth.dto.LoginRequest;
import com.careflow.auth.security.JwtProvider;
import com.careflow.common.enums.AgencyStatus;
import com.careflow.common.enums.Role;
import com.careflow.notification.repository.NotificationRepository;
import com.careflow.user.entity.User;
import com.careflow.user.repository.UserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 로그인 보안 설정(2단계 인증/로그인 알림) 통합 테스트 (H2)
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("local")
@Sql(scripts = "/cleanup.sql", executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
@DisplayName("대행사 로그인 보안 설정 통합 테스트 (H2)")
class AgencySecuritySettingsIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private UserRepository userRepository;
    @Autowired private AgenciesRepository agenciesRepository;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private JwtProvider jwtProvider;
    @Autowired private NotificationRepository notificationRepository;

    // 로그인 흐름을 거치는 테스트이므로 APPROVED 상태로 명시 생성해야 함(Agencies.create()는 기본 PENDING)
    private Agencies createApprovedAgency(String name, String businessNumber, String address) {
        return agenciesRepository.save(Agencies.builder()
                .agencyName(name).businessNumber(businessNumber).agencyAddress(address)
                .agencyFeeRate(5.0).approvalStatus(AgencyStatus.APPROVED).build());
    }

    private User createAgencyUser(String email, Agencies agency) {
        return userRepository.save(User.builder()
                .email(email).passwordHash(passwordEncoder.encode("password1234"))
                .name("담당자").phone("010-0000-0000").role(Role.AGENCY).agency(agency).build());
    }

    @Test
    @DisplayName("로그인 알림이 켜져 있으면 로그인 시 알림이 생성되고, 꺼져 있으면 생성되지 않는다")
    void login_sendsAlert_onlyWhenEnabled() throws Exception {
        Agencies agency = createApprovedAgency("로그인알림테스트대행사", "666-66-66666", "서울시 서초구");
        User user = createAgencyUser("security2@test.com", agency);
        String token = jwtProvider.generateAccessToken(user.getId(), user.getEmail(), "AGENCY", agency.getId(), false);

        LoginRequest req = new LoginRequest();
        org.springframework.test.util.ReflectionTestUtils.setField(req, "email", "security2@test.com");
        org.springframework.test.util.ReflectionTestUtils.setField(req, "password", "password1234");

        // 기본값(false)인 상태에서 로그인 — 알림 생성 안 됨
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk());
        long beforeCount = notificationRepository.count();
        assertThat(beforeCount).isZero();

        // 로그인 알림 활성화
        mockMvc.perform(patch("/api/agency/me/security/login-alert")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new SecurityToggleRequest(true))))
                .andExpect(status().isNoContent());

        // 다시 로그인 — 이번엔 알림 생성됨
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk());

        assertThat(notificationRepository.count()).isEqualTo(1);
    }

    @Test
    @DisplayName("GET 보안 설정 조회 — 상태값 정상 반환")
    void getSecuritySettings_returnsCorrectCounts() throws Exception {
        Agencies agency = createApprovedAgency("보안조회테스트대행사", "777-77-77777", "서울시 마포구");
        User user = createAgencyUser("security3@test.com", agency);
        String token = jwtProvider.generateAccessToken(user.getId(), user.getEmail(), "AGENCY", agency.getId(), false);

        mockMvc.perform(get("/api/agency/me/security")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.twoFactorEnabled").value(false))
                .andExpect(jsonPath("$.loginAlertEnabled").value(false));
    }
}
