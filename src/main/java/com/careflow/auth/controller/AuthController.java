package com.careflow.auth.controller;

import com.careflow.auth.dto.*;
import com.careflow.auth.security.CustomUserDetails;
import com.careflow.auth.service.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/signup")
    public ResponseEntity<String> signUp(@Valid @RequestBody SignUpRequest request) {
        authService.signUp(request);
        return ResponseEntity.ok("회원가입이 완료되었습니다.");
    }

    @PostMapping("/login")
    public ResponseEntity<TokenResponse> login(@Valid @RequestBody LoginRequest request) {
        return ResponseEntity.ok(authService.login(request));
    }

    @PostMapping("/refresh")
    public ResponseEntity<TokenResponse> refresh(@Valid @RequestBody RefreshRequest request) {
        return ResponseEntity.ok(authService.reissue(request.getRefreshToken()));
    }

    /**
     * 로그아웃 API
     * - access token → Redis 블랙리스트 등록 (남은 만료 시간 TTL)
     * - refresh token → Redis에서 즉시 삭제
     * SecurityConfig에서 이 경로만 authenticated()로 별도 지정되어 있어 JWT 필수
     */
    @PostMapping("/logout")
    public ResponseEntity<String> logout(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @RequestHeader("Authorization") String authHeader) {
        String accessToken = authHeader.substring(7); // "Bearer " 제거
        authService.logout(userDetails.getUserId(), accessToken);
        return ResponseEntity.ok("로그아웃되었습니다.");
    }

    // OAuth2 로그인 완료 후 프론트엔드가 1회용 코드를 실제 토큰으로 교환하는 엔드포인트
    @PostMapping("/oauth2/exchange")
    public ResponseEntity<TokenResponse> exchangeOAuth2Code(@Valid @RequestBody OAuth2ExchangeRequest request) {
        return ResponseEntity.ok(authService.exchangeOAuth2Code(request.code()));
    }
}