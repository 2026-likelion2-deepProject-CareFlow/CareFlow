package com.careflow.agency.controller;

import com.careflow.account_requests.entity.AccountRequests;
import com.careflow.account_requests.repository.AccountRequestsRepository;
import com.careflow.agency.entity.Agencies;
import com.careflow.agency.repository.AgenciesRepository;
import com.careflow.auth.security.JwtProvider;
import com.careflow.common.enums.AccountRequestsStatus;
import com.careflow.common.enums.AgencyStatus;
import com.careflow.common.enums.Role;
import com.careflow.engineer.domain.entity.EngineerProfile;
import com.careflow.engineer.repository.EngineerProfileRepository;
import com.careflow.user.entity.User;
import com.careflow.user.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 대행사 데이터 내보내기/가져오기 통합 테스트 (H2)
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("local")
@Sql(scripts = "/cleanup.sql", executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
@DisplayName("대행사 데이터 내보내기/가져오기 통합 테스트 (H2)")
class AgencyDataTransferIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private UserRepository userRepository;
    @Autowired private AgenciesRepository agenciesRepository;
    @Autowired private EngineerProfileRepository engineerProfileRepository;
    @Autowired private AccountRequestsRepository accountRequestsRepository;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private JwtProvider jwtProvider;

    @Test
    @DisplayName("내보내기 정상 흐름 — 200 + CSV 바디에 기사 정보 포함")
    void exportData_success() throws Exception {
        Agencies agency = agenciesRepository.save(Agencies.builder()
                .agencyName("내보내기테스트대행사").businessNumber("111-11-11111")
                .agencyAddress("서울시").agencyFeeRate(5.0).approvalStatus(AgencyStatus.APPROVED).build());

        User rep = userRepository.save(User.builder()
                .email("exportrep@test.com").passwordHash(passwordEncoder.encode("password1234"))
                .name("대표").role(Role.AGENCY).agency(agency).build());

        User engineer = userRepository.save(User.builder()
                .email("exporteng@test.com").passwordHash("hashed")
                .name("김기사").phone("010-1111-2222").role(Role.ENGINEER).agency(agency).build());
        engineerProfileRepository.save(EngineerProfile.createInitial(engineer));

        String token = jwtProvider.generateAccessToken(rep.getId(), rep.getEmail(), "AGENCY", agency.getId(), true);

        String csv = mockMvc.perform(get("/api/agency/me/data-export")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);

        assertThat(csv).contains("김기사");
        assertThat(csv).contains("exporteng@test.com");
    }

    @Test
    @DisplayName("가져오기 정상 흐름 — 200 + account_requests에 PENDING 행 생성")
    void importData_success() throws Exception {
        User rep = userRepository.save(User.builder()
                .email("importrep@test.com").passwordHash(passwordEncoder.encode("password1234"))
                .name("대표").role(Role.AGENCY).build());

        Agencies agency = agenciesRepository.save(Agencies.builder()
                .representativeId(rep)
                .agencyName("가져오기테스트대행사").businessNumber("222-22-22222")
                .agencyAddress("서울시").agencyFeeRate(5.0).approvalStatus(AgencyStatus.APPROVED).build());

        String token = jwtProvider.generateAccessToken(rep.getId(), rep.getEmail(), "AGENCY", agency.getId(), true);

        String csvContent = "name,email,phone\n신규기사,newengineer@test.com,01099998888\n";
        MockMultipartFile file = new MockMultipartFile(
                "file", "roster.csv", "text/csv", csvContent.getBytes(StandardCharsets.UTF_8));

        mockMvc.perform(multipart("/api/agency/me/data-import")
                        .file(file)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.successCount").value(1))
                .andExpect(jsonPath("$.failCount").value(0));

        List<AccountRequests> requests = accountRequestsRepository.findAll().stream()
                .filter(r -> r.getEmail().equals("newengineer@test.com"))
                .toList();
        assertThat(requests).hasSize(1);
        assertThat(requests.get(0).getStatus()).isEqualTo(AccountRequestsStatus.PENDING);
        assertThat(requests.get(0).getName()).isEqualTo("신규기사");
    }

    @Test
    @DisplayName("가져오기 후 기존 승인 API로 정상 승인되어 ENGINEER 계정까지 생성됨")
    void importData_thenApprove_createsEngineerAccount() throws Exception {
        User rep = userRepository.save(User.builder()
                .email("chainrep@test.com").passwordHash(passwordEncoder.encode("password1234"))
                .name("대표").role(Role.AGENCY).build());

        Agencies agency = agenciesRepository.save(Agencies.builder()
                .representativeId(rep)
                .agencyName("연계테스트대행사").businessNumber("333-33-33333")
                .agencyAddress("서울시").agencyFeeRate(5.0).approvalStatus(AgencyStatus.APPROVED).build());

        String token = jwtProvider.generateAccessToken(rep.getId(), rep.getEmail(), "AGENCY", agency.getId(), true);

        String csvContent = "name,email,phone\n연계기사,chainengineer@test.com,01077776666\n";
        MockMultipartFile file = new MockMultipartFile(
                "file", "roster.csv", "text/csv", csvContent.getBytes(StandardCharsets.UTF_8));

        mockMvc.perform(multipart("/api/agency/me/data-import")
                        .file(file)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());

        AccountRequests request = accountRequestsRepository.findAll().stream()
                .filter(r -> r.getEmail().equals("chainengineer@test.com"))
                .findFirst().orElseThrow();

        mockMvc.perform(post("/api/account-requests/engineer/approval")
                        .header("Authorization", "Bearer " + token)
                        .param("accountId", String.valueOf(request.getId())))
                .andExpect(status().isNoContent());

        User created = userRepository.findByEmail("chainengineer@test.com").orElseThrow();
        assertThat(created.getRole()).isEqualTo(Role.ENGINEER);
    }
}
