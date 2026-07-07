package com.careflow.auth.service;

import com.careflow.agency.entity.Agencies;
import com.careflow.agency.repository.AgenciesRepository;
import com.careflow.auth.dto.LoginRequest;
import com.careflow.auth.security.JwtProvider;
import com.careflow.common.enums.AgencyStatus;
import com.careflow.common.enums.Role;
import com.careflow.notification.entity.Notification;
import com.careflow.notification.repository.NotificationRepository;
import com.careflow.region.repository.RegionRepository;
import com.careflow.user.entity.User;
import com.careflow.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
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

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("AuthService 단위 테스트 — login (로그인 알림)")
class AuthServiceTest {

    @InjectMocks
    private AuthService authService;

    @Mock private UserRepository userRepository;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private JwtProvider jwtProvider;
    @Mock private StringRedisTemplate redisTemplate;
    @Mock private AgenciesRepository agenciesRepository;
    @Mock private RegionRepository regionRepository;
    @Mock private NotificationRepository notificationRepository;
    @Mock private ValueOperations<String, String> valueOperations;

    private static final Long USER_ID = 1L;
    private static final String EMAIL = "customer@test.com";
    private static final String RAW_PASSWORD = "password1234";
    private static final String ENCODED_PASSWORD = "encoded-pw";

    private User customer;

    @BeforeEach
    void setUp() {
        customer = User.builder()
                .email(EMAIL).passwordHash(ENCODED_PASSWORD).name("고객")
                .phone("010-0000-0000").role(Role.CUSTOMER).build();
        ReflectionTestUtils.setField(customer, "id", USER_ID);

        given(userRepository.findByEmail(EMAIL)).willReturn(Optional.of(customer));
        given(passwordEncoder.matches(RAW_PASSWORD, ENCODED_PASSWORD)).willReturn(true);
        given(jwtProvider.generateAccessToken(anyLong(), anyString(), anyString(), any(), any()))
                .willReturn("access-token");
        given(jwtProvider.generateRefreshToken(anyLong())).willReturn("refresh-token");
        given(jwtProvider.getAccessTokenExpiration()).willReturn(1800000L);
        given(redisTemplate.opsForValue()).willReturn(valueOperations);
    }

    private LoginRequest buildLoginRequest() {
        LoginRequest request = new LoginRequest();
        ReflectionTestUtils.setField(request, "email", EMAIL);
        ReflectionTestUtils.setField(request, "password", RAW_PASSWORD);
        return request;
    }

    @Nested
    @DisplayName("로그인 알림")
    class LoginAlert {

        @Test
        @DisplayName("TC-1: login_alert_enabled=true — 알림 저장됨")
        void success_alertEnabled_savesNotification() {
            ReflectionTestUtils.setField(customer, "loginAlertEnabled", true);

            authService.login(buildLoginRequest());

            verify(notificationRepository).save(any(Notification.class));
        }

        @Test
        @DisplayName("TC-2: login_alert_enabled=false(기본값) — 알림 저장 안 됨")
        void success_alertDisabled_noNotification() {
            authService.login(buildLoginRequest());

            verify(notificationRepository, never()).save(any(Notification.class));
        }
    }

    @Nested
    @DisplayName("부가 처리 예외 격리")
    class SideEffectIsolation {

        @Test
        @DisplayName("로그인 알림 저장 중 예외가 발생해도 로그인 자체는 성공(토큰 반환)")
        void success_loginSucceeds_evenIfNotificationSaveThrows() {
            ReflectionTestUtils.setField(customer, "loginAlertEnabled", true);
            given(notificationRepository.save(any(Notification.class)))
                    .willThrow(new RuntimeException("DB 오류 시뮬레이션"));

            var response = authService.login(buildLoginRequest());

            assertThat(response.getAccessToken()).isEqualTo("access-token");
        }
    }
}
