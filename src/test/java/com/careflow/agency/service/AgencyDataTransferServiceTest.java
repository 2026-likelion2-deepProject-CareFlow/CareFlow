package com.careflow.agency.service;

import com.careflow.account_requests.entity.AccountRequests;
import com.careflow.account_requests.repository.AccountRequestsRepository;
import com.careflow.agency.dto.response.AgencyDataImportResponse;
import com.careflow.agency.entity.Agencies;
import com.careflow.agency.repository.AgenciesRepository;
import com.careflow.auth.security.CustomUserDetails;
import com.careflow.common.enums.Role;
import com.careflow.engineer.domain.entity.EngineerProfile;
import com.careflow.engineer.repository.EngineerProfileRepository;
import com.careflow.user.entity.User;
import com.careflow.user.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("AgencyDataTransferService 단위 테스트")
class AgencyDataTransferServiceTest {

    @InjectMocks
    private AgencyDataTransferService agencyDataTransferService;

    @Mock private EngineerProfileRepository engineerProfileRepository;
    @Mock private AccountRequestsRepository accountRequestsRepository;
    @Mock private AgenciesRepository agenciesRepository;
    @Mock private UserRepository userRepository;
    @Mock private PasswordEncoder passwordEncoder;

    private static final Long AGENCY_ID = 100L;
    private static final Long REP_USER_ID = 1L;

    private User buildEngineerUser(Long id, String name, String email, String phone, String status) {
        User user = User.builder()
                .email(email).passwordHash("hashed").name(name).phone(phone).role(Role.ENGINEER).build();
        ReflectionTestUtils.setField(user, "id", id);
        ReflectionTestUtils.setField(user, "status", status);
        return user;
    }

    private CustomUserDetails representativeDetails() {
        return new CustomUserDetails(REP_USER_ID, "rep@test.com", "pw", "AGENCY", AGENCY_ID);
    }

    @Nested
    @DisplayName("exportEngineerRoster")
    class ExportEngineerRoster {

        @Test
        @DisplayName("TC-1: 기사 2명 — CSV 헤더 1행 + 데이터 2행 생성")
        void success_exportsTwoEngineers() {
            User u1 = buildEngineerUser(1L, "김철수", "kim@test.com", "010-1111-1111", "ACTIVE");
            User u2 = buildEngineerUser(2L, "이영희", "lee@test.com", "010-2222-2222", "ACTIVE");
            EngineerProfile p1 = EngineerProfile.createInitial(u1);
            EngineerProfile p2 = EngineerProfile.createInitial(u2);

            given(engineerProfileRepository.findByAgencyId(AGENCY_ID)).willReturn(List.of(p1, p2));

            byte[] csv = agencyDataTransferService.exportEngineerRoster(AGENCY_ID);
            String content = new String(csv, StandardCharsets.UTF_8);
            String[] lines = content.replace("﻿", "").split("\\R");

            assertThat(lines).hasSize(3); // 헤더 + 2행
            assertThat(lines[0]).isEqualTo("engineerUserId,name,email,phone,categoryName,skillLevel,status,avgRating");
            assertThat(lines[1]).contains("김철수", "kim@test.com");
            assertThat(lines[2]).contains("이영희", "lee@test.com");
        }

        @Test
        @DisplayName("TC-2: 소속 기사 없음 — 헤더만 있는 CSV")
        void success_emptyRoster() {
            given(engineerProfileRepository.findByAgencyId(AGENCY_ID)).willReturn(List.of());

            byte[] csv = agencyDataTransferService.exportEngineerRoster(AGENCY_ID);
            String content = new String(csv, StandardCharsets.UTF_8).replace("﻿", "").trim();

            assertThat(content).isEqualTo("engineerUserId,name,email,phone,categoryName,skillLevel,status,avgRating");
        }
    }

    @Nested
    @DisplayName("importEngineerRoster")
    class ImportEngineerRoster {

        @Test
        @DisplayName("TC-1: 정상 CSV 2건 — account_requests 2건 생성, successCount=2")
        void success_importsTwoRows() throws Exception {
            given(agenciesRepository.findByRepresentativeById(REP_USER_ID))
                    .willReturn(Optional.of(Agencies.create("테스트대행사", "111-11-11111", "서울시", 5.0)));
            given(agenciesRepository.findById(AGENCY_ID))
                    .willReturn(Optional.of(Agencies.create("테스트대행사", "111-11-11111", "서울시", 5.0)));
            given(userRepository.existsByEmail(anyString())).willReturn(false);
            given(accountRequestsRepository.existsByEmail(anyString())).willReturn(false);
            given(passwordEncoder.encode(anyString())).willReturn("encoded");

            String csvContent = "name,email,phone\n홍길동,hong@test.com,01011112222\n김민수,kim2@test.com,01033334444\n";
            MockMultipartFile file = new MockMultipartFile(
                    "file", "roster.csv", "text/csv", csvContent.getBytes(StandardCharsets.UTF_8));

            AgencyDataImportResponse response =
                    agencyDataTransferService.importEngineerRoster(representativeDetails(), file);

            assertThat(response.successCount()).isEqualTo(2);
            assertThat(response.failCount()).isZero();
            verify(accountRequestsRepository, times(2)).save(any(AccountRequests.class));
        }

        @Test
        @DisplayName("TC-2: 중복 이메일 포함 — 정상 행만 생성, failCount 반영")
        void success_partialFailure_duplicateEmail() throws Exception {
            given(agenciesRepository.findByRepresentativeById(REP_USER_ID))
                    .willReturn(Optional.of(Agencies.create("테스트대행사", "111-11-11111", "서울시", 5.0)));
            given(agenciesRepository.findById(AGENCY_ID))
                    .willReturn(Optional.of(Agencies.create("테스트대행사", "111-11-11111", "서울시", 5.0)));
            given(userRepository.existsByEmail("dup@test.com")).willReturn(true);
            given(userRepository.existsByEmail("new@test.com")).willReturn(false);
            given(accountRequestsRepository.existsByEmail(anyString())).willReturn(false);
            given(passwordEncoder.encode(anyString())).willReturn("encoded");

            String csvContent = "name,email,phone\n중복,dup@test.com,01011112222\n신규,new@test.com,01033334444\n";
            MockMultipartFile file = new MockMultipartFile(
                    "file", "roster.csv", "text/csv", csvContent.getBytes(StandardCharsets.UTF_8));

            AgencyDataImportResponse response =
                    agencyDataTransferService.importEngineerRoster(representativeDetails(), file);

            assertThat(response.successCount()).isEqualTo(1);
            assertThat(response.failCount()).isEqualTo(1);
            assertThat(response.errors().get(0)).contains("이미 가입된 이메일");
            verify(accountRequestsRepository, times(1)).save(any(AccountRequests.class));
        }

        @Test
        @DisplayName("TC-3: 대표 담당자 아님 — IllegalAccessException, save 호출 안 됨")
        void fail_notRepresentative() {
            given(agenciesRepository.findByRepresentativeById(REP_USER_ID)).willReturn(Optional.empty());

            String csvContent = "name,email,phone\n홍길동,hong@test.com,01011112222\n";
            MockMultipartFile file = new MockMultipartFile(
                    "file", "roster.csv", "text/csv", csvContent.getBytes(StandardCharsets.UTF_8));

            assertThatThrownBy(() ->
                    agencyDataTransferService.importEngineerRoster(representativeDetails(), file))
                    .isInstanceOf(IllegalAccessException.class);

            verify(accountRequestsRepository, never()).save(any());
        }

        @Test
        @DisplayName("TC-4: AGENCY 역할 아님 — IllegalAccessException")
        void fail_notAgencyRole() {
            CustomUserDetails engineerDetails =
                    new CustomUserDetails(2L, "engineer@test.com", "pw", "ENGINEER", AGENCY_ID);

            MockMultipartFile file = new MockMultipartFile(
                    "file", "roster.csv", "text/csv", "name,email,phone\n".getBytes(StandardCharsets.UTF_8));

            assertThatThrownBy(() ->
                    agencyDataTransferService.importEngineerRoster(engineerDetails, file))
                    .isInstanceOf(IllegalAccessException.class);
        }

        @Test
        @DisplayName("TC-5: account_requests에 이미 존재하는 이메일 — failCount 반영")
        void success_duplicateInAccountRequests() throws Exception {
            given(agenciesRepository.findByRepresentativeById(REP_USER_ID))
                    .willReturn(Optional.of(Agencies.create("테스트대행사", "111-11-11111", "서울시", 5.0)));
            given(agenciesRepository.findById(AGENCY_ID))
                    .willReturn(Optional.of(Agencies.create("테스트대행사", "111-11-11111", "서울시", 5.0)));
            given(userRepository.existsByEmail(anyString())).willReturn(false);
            given(accountRequestsRepository.existsByEmail("pending@test.com")).willReturn(true);

            String csvContent = "name,email,phone\n대기중,pending@test.com,01011112222\n";
            MockMultipartFile file = new MockMultipartFile(
                    "file", "roster.csv", "text/csv", csvContent.getBytes(StandardCharsets.UTF_8));

            AgencyDataImportResponse response =
                    agencyDataTransferService.importEngineerRoster(representativeDetails(), file);

            assertThat(response.successCount()).isZero();
            assertThat(response.failCount()).isEqualTo(1);
            verify(accountRequestsRepository, never()).save(any());
        }
    }
}
