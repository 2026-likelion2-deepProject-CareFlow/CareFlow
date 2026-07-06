package com.careflow.agency.controller;

import com.careflow.agency.dto.request.SecurityToggleRequest;
import com.careflow.agency.entity.Agencies;
import com.careflow.agency.repository.AgenciesRepository;
import com.careflow.auth.dto.LoginRequest;
import com.careflow.auth.repository.TrustedDeviceRepository;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 로그인 보안 설정(2단계 인증/로그인 알림/신뢰 기기) 통합 테스트 (H2)
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
    @Autowired private TrustedDeviceRepository trustedDeviceRepository;
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
    @DisplayName("로그인 시 신뢰 기기가 자동 등록되고, 동일 기기 재로그인 시 개수가 늘지 않는다")
    void login_registersTrustedDevice_andDoesNotDuplicateOnSameDevice() throws Exception {
        Agencies agency = createApprovedAgency("보안설정테스트대행사", "555-55-55555", "서울시 강남구");
        User user = createAgencyUser("security1@test.com", agency);

        LoginRequest req = new LoginRequest();
        org.springframework.test.util.ReflectionTestUtils.setField(req, "email", "security1@test.com");
        org.springframework.test.util.ReflectionTestUtils.setField(req, "password", "password1234");

        mockMvc.perform(post("/api/auth/login")
                        .header("User-Agent", "TestAgent/1.0")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk());

        assertThat(trustedDeviceRepository.countByUser_Id(user.getId())).isEqualTo(1);

        // 동일 User-Agent로 재로그인 — 새 행이 생기지 않고 개수 유지
        mockMvc.perform(post("/api/auth/login")
                        .header("User-Agent", "TestAgent/1.0")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk());

        assertThat(trustedDeviceRepository.countByUser_Id(user.getId())).isEqualTo(1);
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
                        .header("User-Agent", "TestAgent/1.0")
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
                        .header("User-Agent", "TestAgent/2.0")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk());

        assertThat(notificationRepository.count()).isEqualTo(1);
    }

    @Test
    @DisplayName("GET 보안 설정 조회 — 신뢰 기기 개수 포함 정상 반환")
    void getSecuritySettings_returnsCorrectCounts() throws Exception {
        Agencies agency = createApprovedAgency("보안조회테스트대행사", "777-77-77777", "서울시 마포구");
        User user = createAgencyUser("security3@test.com", agency);
        String token = jwtProvider.generateAccessToken(user.getId(), user.getEmail(), "AGENCY", agency.getId(), false);

        LoginRequest req = new LoginRequest();
        org.springframework.test.util.ReflectionTestUtils.setField(req, "email", "security3@test.com");
        org.springframework.test.util.ReflectionTestUtils.setField(req, "password", "password1234");

        mockMvc.perform(post("/api/auth/login")
                        .header("User-Agent", "TestAgent/1.0")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)));

        mockMvc.perform(get("/api/agency/me/security")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.twoFactorEnabled").value(false))
                .andExpect(jsonPath("$.loginAlertEnabled").value(false))
                .andExpect(jsonPath("$.trustedDeviceCount").value(1));
    }

    @Test
    @DisplayName("X-Device-Id가 동일하면 User-Agent가 바뀌어도 같은 기기로 유지되고 이름만 갱신된다")
    void login_sameDeviceToken_differentUserAgent_updatesNameNotDuplicate() throws Exception {
        Agencies agency = createApprovedAgency("기기토큰테스트대행사", "999-99-99999", "서울시 용산구");
        User user = createAgencyUser("devicetoken1@test.com", agency);

        LoginRequest req = new LoginRequest();
        org.springframework.test.util.ReflectionTestUtils.setField(req, "email", "devicetoken1@test.com");
        org.springframework.test.util.ReflectionTestUtils.setField(req, "password", "password1234");

        mockMvc.perform(post("/api/auth/login")
                        .header("User-Agent", "Chrome/125")
                        .header("X-Device-Id", "same-client-uuid")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk());

        // 브라우저 업데이트로 User-Agent가 바뀐 상황을 흉내 — 같은 X-Device-Id로 재로그인
        mockMvc.perform(post("/api/auth/login")
                        .header("User-Agent", "Chrome/126")
                        .header("X-Device-Id", "same-client-uuid")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk());

        assertThat(trustedDeviceRepository.countByUser_Id(user.getId())).isEqualTo(1);
        assertThat(trustedDeviceRepository.findByUser_IdOrderByLastUsedAtDesc(user.getId()).get(0).getDeviceName())
                .isEqualTo("Chrome/126");
    }

    @Test
    @DisplayName("X-Device-Id가 다르면 같은 User-Agent라도 별개의 기기로 각각 등록된다")
    void login_differentDeviceToken_sameUserAgent_createsSeparateEntries() throws Exception {
        Agencies agency = createApprovedAgency("기기토큰분리테스트대행사", "111-22-33333", "서울시 성동구");
        User user = createAgencyUser("devicetoken2@test.com", agency);

        LoginRequest req = new LoginRequest();
        org.springframework.test.util.ReflectionTestUtils.setField(req, "email", "devicetoken2@test.com");
        org.springframework.test.util.ReflectionTestUtils.setField(req, "password", "password1234");

        mockMvc.perform(post("/api/auth/login")
                        .header("User-Agent", "Chrome/126")
                        .header("X-Device-Id", "device-A")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/auth/login")
                        .header("User-Agent", "Chrome/126")
                        .header("X-Device-Id", "device-B")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk());

        assertThat(trustedDeviceRepository.countByUser_Id(user.getId())).isEqualTo(2);
    }

    @Test
    @DisplayName("신뢰 기기 삭제 — 정상 삭제 후 목록에서 제외됨")
    void deleteTrustedDevice_removesFromList() throws Exception {
        Agencies agency = createApprovedAgency("기기삭제테스트대행사", "888-88-88888", "서울시 송파구");
        User user = createAgencyUser("security4@test.com", agency);
        String token = jwtProvider.generateAccessToken(user.getId(), user.getEmail(), "AGENCY", agency.getId(), false);

        LoginRequest req = new LoginRequest();
        org.springframework.test.util.ReflectionTestUtils.setField(req, "email", "security4@test.com");
        org.springframework.test.util.ReflectionTestUtils.setField(req, "password", "password1234");

        mockMvc.perform(post("/api/auth/login")
                        .header("User-Agent", "TestAgent/1.0")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)));

        Long deviceId = trustedDeviceRepository.findByUser_IdOrderByLastUsedAtDesc(user.getId())
                .get(0).getId();

        mockMvc.perform(delete("/api/agency/me/trusted-devices/{deviceId}", deviceId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());

        assertThat(trustedDeviceRepository.countByUser_Id(user.getId())).isZero();
    }
}
