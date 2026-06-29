package com.careflow.auth.controller;

import com.careflow.auth.dto.*;
import com.careflow.auth.service.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
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

    // OAuth2 로그인 완료 후 프론트엔드가 1회용 코드를 실제 토큰으로 교환하는 엔드포인트
    @PostMapping("/oauth2/exchange")
    public ResponseEntity<TokenResponse> exchangeOAuth2Code(@Valid @RequestBody OAuth2ExchangeRequest request) {
        return ResponseEntity.ok(authService.exchangeOAuth2Code(request.code()));
    }
}