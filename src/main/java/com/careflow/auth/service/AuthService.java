package com.careflow.auth.service;

import com.careflow.agency.entity.Agencies;
import com.careflow.agency.repository.AgenciesRepository;
import com.careflow.auth.dto.LoginRequest;
import com.careflow.auth.dto.PasswordChangeRequest;
import com.careflow.auth.dto.SignUpRequest;
import com.careflow.auth.dto.TokenResponse;
import com.careflow.auth.security.JwtProvider;
import com.careflow.common.enums.AgencyStatus;
import com.careflow.common.enums.Role;
import com.careflow.common.util.PhoneNumberUtils;
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
import java.util.Date;
import java.util.NoSuchElementException;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AuthService {

    private static final String REFRESH_KEY_PREFIX = "refresh:token:";
    private static final String OAUTH2_EXCHANGE_PREFIX = "oauth2:exchange:";
    public static final String BLACKLIST_KEY_PREFIX = "blacklist:access:";

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

        // 비밀번호 생성 규칙 강화 — 형식(영문+숫자 8자 이상)은 SignUpRequest의 @Pattern/@Size로 검증되고,
        // 여기서는 DTO 필드만으로는 검증 불가능한 교차 필드 규칙(아이디·휴대폰번호 포함 여부)을 검사
        validatePasswordDoesNotContainSensitiveInfo(request.getPassword(), request.getEmail(), request.getPhone());

        Regions regions = regionRepository.findById(request.getRegionId()).orElseThrow(() -> new NoSuchElementException("입력받은 지역 정보가 존재하지 않습니다."));
        User user = User.builder()
                .email(request.getEmail())
                .passwordHash(passwordEncoder.encode(request.getPassword()))
                .name(request.getName())
                .phone(PhoneNumberUtils.stripHyphens(request.getPhone())) // 전화번호는 하이픈 없이 저장(사업자번호는 유지)
                .role(Role.CUSTOMER)
                .regionId(regions)
                .addressDetail(request.getAddressDetail())
                .build();

        userRepository.save(user);
    }

    /**
     * 비밀번호에 본인 식별정보가 포함되는 것을 방지 (회원가입/비밀번호 변경 공통 사용)
     * - 아이디(이메일 로컬파트, @ 앞부분)가 비밀번호에 포함된 경우 차단 (3자 미만이면 오탐 방지를 위해 검사 생략)
     * - 휴대폰번호의 연속된 숫자 4자리 이상이 비밀번호에 포함된 경우 차단 (전화번호 미입력 시 검사 생략)
     */
    private void validatePasswordDoesNotContainSensitiveInfo(String password, String email, String phone) {
        String lowerPassword = password.toLowerCase();

        String emailLocalPart = email.split("@")[0].toLowerCase();
        if (emailLocalPart.length() >= 3 && lowerPassword.contains(emailLocalPart)) {
            throw new IllegalArgumentException("비밀번호에 아이디(이메일)를 포함할 수 없습니다.");
        }

        String phoneDigits = phone == null ? "" : phone.replaceAll("[^0-9]", "");
        int minMatchLen = 4;
        for (int i = 0; i + minMatchLen <= phoneDigits.length(); i++) {
            String chunk = phoneDigits.substring(i, i + minMatchLen);
            if (lowerPassword.contains(chunk)) {
                throw new IllegalArgumentException("비밀번호에 휴대폰 번호를 포함할 수 없습니다.");
            }
        }
    }

    /**
     * 로그인된 사용자의 비밀번호 변경 (현재 비밀번호 확인 후 새 비밀번호로 교체)
     * - 모든 역할(CUSTOMER/AGENCY/ENGINEER/ADMIN) 공통 사용 엔드포인트
     * - 소셜 로그인 전용 계정(passwordHash 없음)은 변경 대상에서 제외
     */
    @Transactional
    public void changePassword(Long userId, PasswordChangeRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new NoSuchElementException("존재하지 않는 사용자입니다."));

        if (user.getPasswordHash() == null) {
            throw new IllegalStateException("소셜 로그인 계정은 비밀번호를 변경할 수 없습니다.");
        }

        if (!passwordEncoder.matches(request.currentPassword(), user.getPasswordHash())) {
            throw new IllegalArgumentException("현재 비밀번호가 일치하지 않습니다.");
        }

        if (request.currentPassword().equals(request.newPassword())) {
            throw new IllegalArgumentException("새 비밀번호는 현재 비밀번호와 달라야 합니다.");
        }

        // 형식(영문+숫자 8자 이상 64자 이하)은 PasswordChangeRequest의 @Pattern/@Size로 검증되고,
        // 여기서는 DTO만으로는 검증 불가능한 교차 필드 규칙(아이디·휴대폰번호 포함 여부)을 검사
        validatePasswordDoesNotContainSensitiveInfo(request.newPassword(), user.getEmail(), user.getPhone());

        user.updatePassword(passwordEncoder.encode(request.newPassword()));
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
     * OAuth2 로그인 성공 후 1회용 교환 코드 발급
     * - UUID 코드를 생성하고 "oauth2:exchange:{code}" 키로 access|refresh 를 Redis에 30초 TTL로 저장
     * - URL에 토큰을 직접 노출하지 않기 위한 Authorization Code Exchange 패턴
     */
    @Transactional
    public String issueOAuth2ExchangeCode(User user) {
        TokenResponse tokenResponse = issueTokenResponse(user);

        String code = UUID.randomUUID().toString();
        // JWT는 '.' 구분자만 사용하므로 '|' 로 두 토큰을 안전하게 연결
        String value = tokenResponse.getAccessToken() + "|" + tokenResponse.getRefreshToken();

        redisTemplate.opsForValue().set(
                OAUTH2_EXCHANGE_PREFIX + code,
                value,
                Duration.ofSeconds(30)
        );

        return code;
    }

    /**
     * OAuth2 1회용 코드 교환
     * - Redis에서 코드 조회 후 즉시 삭제하여 1회용 보장
     * - 코드가 없거나 TTL 만료 시 IllegalArgumentException
     */
    public TokenResponse exchangeOAuth2Code(String code) {
        String key = OAUTH2_EXCHANGE_PREFIX + code;
        String value = redisTemplate.opsForValue().get(key);

        if (value == null) {
            throw new IllegalArgumentException("유효하지 않거나 만료된 코드입니다.");
        }

        // 1회용 보장: 조회 즉시 삭제
        redisTemplate.delete(key);

        String[] parts = value.split("\\|", 2);
        return TokenResponse.builder()
                .accessToken(parts[0])
                .refreshToken(parts[1])
                .tokenType("Bearer")
                .expiresIn(jwtProvider.getAccessTokenExpiration())
                .build();
    }

    /**
     * 로그아웃 처리
     * 1. refresh token → Redis에서 삭제 (재발급 원천 차단)
     * 2. access token → Redis 블랙리스트 등록 (남은 만료 시간만큼 TTL 설정)
     */
    public void logout(Long userId, String accessToken) {
        // refresh token 삭제
        redisTemplate.delete(REFRESH_KEY_PREFIX + userId);

        // access token 블랙리스트 등록 — 만료 전까지만 유효하면 되므로 남은 TTL 계산
        Date expiration = jwtProvider.getClaims(accessToken).getExpiration();
        long remainingMs = expiration.getTime() - System.currentTimeMillis();
        if (remainingMs > 0) {
            redisTemplate.opsForValue().set(
                    BLACKLIST_KEY_PREFIX + accessToken,
                    "1",
                    Duration.ofMillis(remainingMs)
            );
        }
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
