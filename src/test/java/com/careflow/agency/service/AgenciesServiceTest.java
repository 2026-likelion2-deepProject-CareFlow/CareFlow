package com.careflow.agency.service;

import com.careflow.account_requests.repository.AccountRequestsRepository;
import com.careflow.agency.dto.request.AgencyWithdrawRequest;
import com.careflow.agency.dto.response.AgencySecuritySettingsResponse;
import com.careflow.agency.dto.response.TrustedDeviceResponse;
import com.careflow.agency.entity.Agencies;
import com.careflow.agency.repository.AgenciesRepository;
import com.careflow.agency_bank_account.repository.AgencyBankAccountRepository;
import com.careflow.auth.entity.TrustedDevice;
import com.careflow.auth.repository.TrustedDeviceRepository;
import com.careflow.common.enums.Role;
import com.careflow.region.repository.RegionRepository;
import com.careflow.user.entity.User;
import com.careflow.user.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("AgenciesService 단위 테스트 — withdrawAccount")
class AgenciesServiceTest {

    @InjectMocks
    private AgenciesService agenciesService;

    @Mock private AgenciesRepository agenciesRepository;
    @Mock private AccountRequestsRepository accountRequestsRepository;
    @Mock private UserRepository userRepository;
    @Mock private RegionRepository regionRepository;
    @Mock private AgencyBankAccountRepository agencyBankAccountRepository;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private StringRedisTemplate redisTemplate;
    @Mock private TrustedDeviceRepository trustedDeviceRepository;

    private static final Long USER_ID = 1L;

    private User buildUser(String encodedPassword) {
        User user = User.builder()
                .email("staff@test.com").passwordHash(encodedPassword).name("일반관리자")
                .role(Role.AGENCY).build();
        ReflectionTestUtils.setField(user, "id", USER_ID);
        return user;
    }

    @Nested
    @DisplayName("withdrawAccount")
    class WithdrawAccount {

        @Test
        @DisplayName("TC-1: 정상 탈퇴 — 일반 관리자, 비밀번호 일치, 대표 아님")
        void success_deletesAndClearsRefreshToken() {
            User user = buildUser("encoded-pw");
            given(userRepository.findById(USER_ID)).willReturn(Optional.of(user));
            given(passwordEncoder.matches("현재비번", "encoded-pw")).willReturn(true);
            given(agenciesRepository.findByRepresentativeById(USER_ID)).willReturn(Optional.empty());

            agenciesService.withdrawAccount(USER_ID, new AgencyWithdrawRequest("현재비번"));

            verify(userRepository).delete(user);
            verify(redisTemplate).delete("refresh:token:" + USER_ID);
        }

        @Test
        @DisplayName("TC-2: 비밀번호 불일치 — IllegalArgumentException, delete 호출 안 됨")
        void fail_wrongPassword() {
            User user = buildUser("encoded-pw");
            given(userRepository.findById(USER_ID)).willReturn(Optional.of(user));
            given(passwordEncoder.matches("틀린비번", "encoded-pw")).willReturn(false);

            assertThatThrownBy(() ->
                    agenciesService.withdrawAccount(USER_ID, new AgencyWithdrawRequest("틀린비번")))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("현재 비밀번호가 일치하지 않습니다.");

            verify(userRepository, never()).delete(user);
        }

        @Test
        @DisplayName("TC-3: 대표 담당자(슈퍼 계정) 탈퇴 시도 — IllegalStateException, delete 호출 안 됨")
        void fail_representativeCannotWithdraw() {
            User user = buildUser("encoded-pw");
            given(userRepository.findById(USER_ID)).willReturn(Optional.of(user));
            given(passwordEncoder.matches("현재비번", "encoded-pw")).willReturn(true);

            Agencies agency = Agencies.create("테스트대행사", "111-11-11111", "서울시", 5.0);
            given(agenciesRepository.findByRepresentativeById(USER_ID)).willReturn(Optional.of(agency));

            assertThatThrownBy(() ->
                    agenciesService.withdrawAccount(USER_ID, new AgencyWithdrawRequest("현재비번")))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("대표 담당자는 탈퇴할 수 없습니다");

            verify(userRepository, never()).delete(user);
        }

        @Test
        @DisplayName("TC-4: 존재하지 않는 사용자 — NoSuchElementException")
        void fail_userNotFound() {
            given(userRepository.findById(999L)).willReturn(Optional.empty());

            assertThatThrownBy(() ->
                    agenciesService.withdrawAccount(999L, new AgencyWithdrawRequest("아무거나")))
                    .isInstanceOf(NoSuchElementException.class);
        }
    }

    @Nested
    @DisplayName("getSecuritySettings / toggleTwoFactor / toggleLoginAlert")
    class SecuritySettings {

        @Test
        @DisplayName("TC-1: 조회 성공 — 상태값 + 신뢰기기 개수 반환")
        void success_getSecuritySettings() {
            User user = buildUser("encoded-pw");
            given(userRepository.findById(USER_ID)).willReturn(Optional.of(user));
            given(trustedDeviceRepository.countByUser_Id(USER_ID)).willReturn(3L);

            AgencySecuritySettingsResponse response = agenciesService.getSecuritySettings(USER_ID);

            assertThat(response.twoFactorEnabled()).isFalse();
            assertThat(response.loginAlertEnabled()).isFalse();
            assertThat(response.trustedDeviceCount()).isEqualTo(3L);
        }

        @Test
        @DisplayName("TC-2: 2단계 인증 토글 — true로 변경")
        void success_toggleTwoFactor() {
            User user = buildUser("encoded-pw");
            given(userRepository.findById(USER_ID)).willReturn(Optional.of(user));

            agenciesService.toggleTwoFactor(USER_ID, true);

            assertThat(user.isTwoFactorEnabled()).isTrue();
        }

        @Test
        @DisplayName("TC-3: 로그인 알림 토글 — true로 변경")
        void success_toggleLoginAlert() {
            User user = buildUser("encoded-pw");
            given(userRepository.findById(USER_ID)).willReturn(Optional.of(user));

            agenciesService.toggleLoginAlert(USER_ID, true);

            assertThat(user.isLoginAlertEnabled()).isTrue();
        }

        @Test
        @DisplayName("TC-4: 존재하지 않는 사용자 — NoSuchElementException")
        void fail_userNotFound() {
            given(userRepository.findById(999L)).willReturn(Optional.empty());

            assertThatThrownBy(() -> agenciesService.getSecuritySettings(999L))
                    .isInstanceOf(NoSuchElementException.class);
        }
    }

    @Nested
    @DisplayName("getTrustedDevices / deleteTrustedDevice")
    class TrustedDevices {

        @Test
        @DisplayName("TC-1: 목록 조회 성공")
        void success_getTrustedDevices() {
            User user = buildUser("encoded-pw");
            TrustedDevice device = TrustedDevice.create(user, "Mozilla/5.0 Chrome/126", "test-device-token");
            ReflectionTestUtils.setField(device, "id", 10L);
            given(trustedDeviceRepository.findByUser_IdOrderByLastUsedAtDesc(USER_ID))
                    .willReturn(List.of(device));

            List<TrustedDeviceResponse> result = agenciesService.getTrustedDevices(USER_ID);

            assertThat(result).hasSize(1);
            assertThat(result.get(0).deviceId()).isEqualTo(10L);
            assertThat(result.get(0).deviceName()).isEqualTo("Mozilla/5.0 Chrome/126");
        }

        @Test
        @DisplayName("TC-2: 본인 기기 삭제 성공")
        void success_deleteOwnDevice() throws IllegalAccessException {
            User user = buildUser("encoded-pw");
            TrustedDevice device = TrustedDevice.create(user, "Mozilla/5.0 Chrome/126", "test-device-token");
            ReflectionTestUtils.setField(device, "id", 10L);
            given(trustedDeviceRepository.findById(10L)).willReturn(Optional.of(device));

            agenciesService.deleteTrustedDevice(USER_ID, 10L);

            verify(trustedDeviceRepository).delete(device);
        }

        @Test
        @DisplayName("TC-3: 타인 소유 기기 삭제 시도 — IllegalAccessException")
        void fail_otherUsersDevice() {
            User owner = buildUser("encoded-pw");
            ReflectionTestUtils.setField(owner, "id", 999L);
            TrustedDevice device = TrustedDevice.create(owner, "Mozilla/5.0 Chrome/126", "test-device-token");
            ReflectionTestUtils.setField(device, "id", 10L);
            given(trustedDeviceRepository.findById(10L)).willReturn(Optional.of(device));

            assertThatThrownBy(() -> agenciesService.deleteTrustedDevice(USER_ID, 10L))
                    .isInstanceOf(IllegalAccessException.class);

            verify(trustedDeviceRepository, never()).delete(device);
        }

        @Test
        @DisplayName("TC-4: 존재하지 않는 기기 삭제 시도 — NoSuchElementException")
        void fail_deviceNotFound() {
            given(trustedDeviceRepository.findById(999L)).willReturn(Optional.empty());

            assertThatThrownBy(() -> agenciesService.deleteTrustedDevice(USER_ID, 999L))
                    .isInstanceOf(NoSuchElementException.class);
        }
    }
}
