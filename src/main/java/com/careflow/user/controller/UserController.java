package com.careflow.user.controller;

import com.careflow.auth.security.CustomUserDetails;
import com.careflow.user.dto.UserAddressResponse;
import com.careflow.user.dto.UserProfileResponse;
import com.careflow.user.dto.UserUpdateRequest;
import com.careflow.user.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    /**
     * 로그인한 사용자 본인 프로필 조회 API
     * 이름, 이메일, 전화번호, 지역, 상세 주소, 역할 반환
     */
    @GetMapping("/me")
    public ResponseEntity<UserProfileResponse> getMyProfile(
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        return ResponseEntity.ok(userService.getMyProfile(userDetails.getUserId()));
    }

    /**
     * 로그인한 사용자 본인 정보 수정 API
     * null 필드는 변경하지 않음 (PATCH 의미론)
     */
    @PatchMapping("/me")
    public ResponseEntity<UserProfileResponse> updateMyProfile(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @Valid @RequestBody UserUpdateRequest request) {
        return ResponseEntity.ok(userService.updateMyProfile(userDetails.getUserId(), request));
    }

    /**
     * 고객 주소 정보 조회 API
     * TODO: @RequestParam Long userId → 추후 @AuthenticationPrincipal로 대체 예정
     */
    @GetMapping("/me/address")
    public ResponseEntity<UserAddressResponse> getMyAddress(@RequestParam Long userId) {
        return ResponseEntity.ok(userService.getMyAddress(userId));
    }
}
