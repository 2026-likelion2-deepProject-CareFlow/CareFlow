package com.careflow.auth.service;

import com.careflow.agency.entity.Agencies;
import com.careflow.agency.repository.AgenciesRepository;
import com.careflow.auth.dto.LoginRequest;
import com.careflow.auth.dto.SignUpRequest;
import com.careflow.auth.dto.TokenResponse;
import com.careflow.auth.security.JwtProvider;
import com.careflow.common.enums.AgencyStatus;
import com.careflow.common.enums.Role;
import com.careflow.region.entity.Regions;
import com.careflow.region.repository.RegionRepository;
import com.careflow.user.entity.User;
import com.careflow.user.repository.UserRepository;
import com.careflow.user.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.util.NoSuchElementException;

@Service
@RequiredArgsConstructor
public class AuthService {

    private static final String REFRESH_KEY_PREFIX = "refresh:token:";

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtProvider jwtProvider;
    private final StringRedisTemplate redisTemplate;

    private final AgenciesRepository agenciesRepository;
    private final RegionRepository regionRepository;

    @Transactional
    public void signUp(SignUpRequest request) {
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new IllegalArgumentException("이미 가입된 이메일입니다.");
        }

        Regions regions = regionRepository.findByName(request.getRegionName()).orElseThrow(() -> new NoSuchElementException("입력받은 지역 정보가 존재하지 않습니다."));
        User user = User.builder()
                .email(request.getEmail())
                .passwordHash(passwordEncoder.encode(request.getPassword()))
                .name(request.getName())
                .phone(request.getPhone())
                .role(Role.CUSTOMER)
                .regionId(regions)
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

        if (!"ACTIVE".equals(user.getStatus())) {
            throw new IllegalStateException("정지되었거나 비활성화된 계정입니다.");
        }

        if (user.getRole() == Role.AGENCY) {
            // 슈퍼 계정인지 관리자 계정인지 확인 필요 X
            Agencies agencies = agenciesRepository.findById(user.getAgency().getId()).orElseThrow(() -> new NoSuchElementException("존재하지 않는 대행사 입니다."));

            if (agencies.getApprovalStatus() == AgencyStatus.REJECTED) {
                throw new IllegalStateException("대행사 정보가 등록 거부되었습니다. 자세한 사항은 관리자에게 문의하세요");
            } else  if (agencies.getApprovalStatus() == AgencyStatus.PENDING){
                throw new IllegalStateException("대행사 정보가 등록 대기중입니다. 자세한 사항은 관리자에게 문의하세요");
            }
        }

        return issueTokenResponse(user);
    }

    @Transactional(readOnly = true)
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

        if (!"ACTIVE".equals(user.getStatus())) {
            throw new IllegalStateException("정지되었거나 비활성화된 계정입니다.");
        }

        String newAccessToken = jwtProvider.generateAccessToken(
                user.getId(), user.getEmail(), user.getRole().name(), resolveAgencyId(user));

        return TokenResponse.builder()
                .accessToken(newAccessToken)
                .refreshToken(refreshToken)
                .tokenType("Bearer")
                .expiresIn(jwtProvider.getAccessTokenExpiration())
                .build();
    }

    /**
     * 구글 로그인용: 이메일로 기존 회원 조회, 없으면 새로 생성(CUSTOMER, 비밀번호 없음)
     */
    @Transactional
    public User findOrCreateGoogleUser(String email, String name) {
        return userRepository.findByEmail(email)
                .orElseGet(() -> {
                    User newUser = User.builder()
                            .email(email)
                            .passwordHash(null)
                            .name(name != null ? name : "구글 사용자")
                            .role(Role.CUSTOMER)
                            .build();
                    return userRepository.save(newUser);
                });
    }

    /**
     * role별 agencyId 결정
     * - AGENCY : 본인 담당 대행사 ID
     * - ENGINEER : 소속 대행사 ID (users.agency_id가 동일 필드에 저장됨)
     * - CUSTOMER / ADMIN : null
     */
    private Long resolveAgencyId(User user) {
        if (user.getRole() == Role.AGENCY || user.getRole() == Role.ENGINEER) {
            return user.getAgency() != null ? user.getAgency().getId() : null;
        }
        return null;
    }

    /**
     * 로그인/구글로그인 공통: JWT 발급 + Redis에 refreshToken 저장
     */
    @Transactional
    public TokenResponse issueTokenResponse(User user) {
        user.updateLastLogin();

        String accessToken = jwtProvider.generateAccessToken(
                user.getId(), user.getEmail(), user.getRole().name(), resolveAgencyId(user));
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
}
