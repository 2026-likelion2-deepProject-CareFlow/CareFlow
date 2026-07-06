package com.careflow.agency.controller;

import com.careflow.agency.dto.request.AgencyWithdrawRequest;
import com.careflow.agency.entity.Agencies;
import com.careflow.agency.repository.AgenciesRepository;
import com.careflow.auth.security.JwtProvider;
import com.careflow.common.enums.Role;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 대행사 관리자 계정 탈퇴(DELETE /api/agency/me/withdraw) 통합 테스트 (H2)
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("local")
@Sql(scripts = "/cleanup.sql", executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
@DisplayName("대행사 계정 탈퇴 통합 테스트 (H2)")
class AgencyWithdrawIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private UserRepository userRepository;
    @Autowired private AgenciesRepository agenciesRepository;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private JwtProvider jwtProvider;

    @Test
    @DisplayName("성공: 일반 관리자 탈퇴 → 204 + deleted_at NOT NULL + 재로그인 실패")
    void withdraw_staffAccount_success() throws Exception {
        Agencies agency = agenciesRepository.save(Agencies.create(
                "탈퇴테스트대행사", "222-22-22222", "서울시 강남구", 5.0));

        User representative = userRepository.save(User.builder()
                .email("rep@test.com").passwordHash(passwordEncoder.encode("password1234"))
                .name("대표").phone("010-0000-0001").role(Role.AGENCY).agency(agency).build());
        // representativeId 설정 — 리플렉션 없이 도메인 메서드가 없으므로 재조회 후 별도 setter 대신 approve 경로를 흉내내지 않고
        // Agencies.create()가 representativeId를 안 받는 팩토리이므로 여기서는 대표를 지정하지 않고 staff만 검증한다.

        User staff = userRepository.save(User.builder()
                .email("staff@test.com").passwordHash(passwordEncoder.encode("password1234"))
                .name("일반관리자").phone("010-0000-0002").role(Role.AGENCY).agency(agency).build());

        String staffToken = jwtProvider.generateAccessToken(staff.getId(), staff.getEmail(), "AGENCY", agency.getId(), false);

        AgencyWithdrawRequest req = new AgencyWithdrawRequest("password1234");
        mockMvc.perform(delete("/api/agency/me/withdraw")
                        .header("Authorization", "Bearer " + staffToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isNoContent());

        // @SQLRestriction("deleted_at IS NULL") 때문에 일반 조회로는 더 이상 안 잡힘
        assertThat(userRepository.findById(staff.getId())).isEmpty();

        // 탈퇴 계정으로 재로그인 시도 — 존재하지 않는 사용자로 처리되어 400
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"staff@test.com\",\"password\":\"password1234\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("실패: 비밀번호 불일치 — 400 + 계정 유지")
    void withdraw_wrongPassword_accountKept() throws Exception {
        Agencies agency = agenciesRepository.save(Agencies.create(
                "탈퇴실패테스트대행사", "333-33-33333", "서울시 서초구", 5.0));

        User staff = userRepository.save(User.builder()
                .email("staff2@test.com").passwordHash(passwordEncoder.encode("password1234"))
                .name("일반관리자2").phone("010-0000-0003").role(Role.AGENCY).agency(agency).build());

        String staffToken = jwtProvider.generateAccessToken(staff.getId(), staff.getEmail(), "AGENCY", agency.getId(), false);

        AgencyWithdrawRequest req = new AgencyWithdrawRequest("틀린비밀번호");
        mockMvc.perform(delete("/api/agency/me/withdraw")
                        .header("Authorization", "Bearer " + staffToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest());

        assertThat(userRepository.findById(staff.getId())).isPresent();
    }

    @Test
    @DisplayName("실패: 대표 담당자 탈퇴 시도 — 403 + 계정 유지")
    void withdraw_representative_forbidden() throws Exception {
        User representative = userRepository.save(User.builder()
                .email("rep2@test.com").passwordHash(passwordEncoder.encode("password1234"))
                .name("대표2").phone("010-0000-0004").role(Role.AGENCY).build());

        Agencies agency = agenciesRepository.save(Agencies.builder()
                .representativeId(representative)
                .agencyName("대표탈퇴테스트대행사").businessNumber("444-44-44444")
                .agencyAddress("서울시 마포구").agencyFeeRate(5.0).build());

        String repToken = jwtProvider.generateAccessToken(
                representative.getId(), representative.getEmail(), "AGENCY", agency.getId(), true);

        AgencyWithdrawRequest req = new AgencyWithdrawRequest("password1234");
        mockMvc.perform(delete("/api/agency/me/withdraw")
                        .header("Authorization", "Bearer " + repToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isForbidden());

        assertThat(userRepository.findById(representative.getId())).isPresent();
    }
}
