package com.careflow.auth.service;

import com.careflow.auth.dto.LoginRequest;
import com.careflow.auth.dto.SignUpRequest;
import com.careflow.auth.dto.TokenResponse;
import com.careflow.auth.security.JwtProvider;
import com.careflow.common.enums.Role;
import com.careflow.user.entity.User;
import com.careflow.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;

@Service
@RequiredArgsConstructor
public class AuthService {

    private static final String REFRESH_KEY_PREFIX = "refresh:token:";

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtProvider jwtProvider;
    private final StringRedisTemplate redisTemplate;

    @Transactional
    public void signUp(SignUpRequest request) {
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new IllegalArgumentException("이미 가입된 이메일입니다.");
        }

        User user = User.builder()
                .email(request.getEmail())
                .passwordHash(passwordEncoder.encode(request.getPassword()))
                .name(request.getName())
                .phone(request.getPhone())
                .role(Role.CUSTOMER)
                .regionId(request.getRegionId())
                .addressDetail(request.getAddressDetail())
                .build();

        userRepository.save(user);
    }

    @Transactional
    public TokenResponse login(LoginRequest request) {
        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 사용자입니다."));

        if (user.getPasswordHash() == null
                || !passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            throw new IllegalArgumentException("비밀번호가 일치하지 않습니다.");
        }

        // 계정 상태 확인 (정지/비활성 계정 로그인 차단)
        if (!"ACTIVE".equals(user.getStatus())) {
            throw new IllegalStateException("정지되었거나 비활성화된 계정입니다.");
        }

        // 호준님 파트: 대행사 승인 상태 검증 로직
        if (user.getRole() == Role.AGENCY) {
            // TODO(호준): agencies.approval_status 확인 후 PENDING/REJECTED면 예외 던지기
        }

        user.updateLastLogin();

        String accessToken = jwtProvider.generateAccessToken(user.getId(), user.getEmail(), user.getRole().name());
        String refreshToken = jwtProvider.generateRefreshToken(user.getId());

        redisTemplate.opsForValue().set(
                REFRESH_KEY_PREFIX + user.getId(),
                refreshToken,
                Duration.ofMillis(jwtProvider.getRefreshTokenExpiration())
        );

        return TokenResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .tokenType("Bearer")
                .expiresIn(jwtProvider.getAccessTokenExpiration())
                .build();
    }

    public TokenResponse reissue(String refreshToken) {
        if (!jwtProvider.validateToken(refreshToken)) {
            throw new IllegalArgumentException("유효하지 않은 refresh token입니다.");
        }
        Long userId = jwtProvider.getUserId(refreshToken);
        String saved = redisTemplate.opsForValue().get(REFRESH_KEY_PREFIX + userId);

        if (saved == null || !saved.equals(refreshToken)) {
            throw new IllegalArgumentException("만료되었거나 일치하지 않는 refresh token입니다.");
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 사용자입니다."));

        // 계정 상태 확인 (로그인 이후 정지된 경우 토큰 재발급 차단)
        if (!"ACTIVE".equals(user.getStatus())) {
            throw new IllegalStateException("정지되었거나 비활성화된 계정입니다.");
        }

        String newAccessToken = jwtProvider.generateAccessToken(user.getId(), user.getEmail(), user.getRole().name());

        return TokenResponse.builder()
                .accessToken(newAccessToken)
                .refreshToken(refreshToken)
                .tokenType("Bearer")
                .expiresIn(jwtProvider.getAccessTokenExpiration())
                .build();
    }
}