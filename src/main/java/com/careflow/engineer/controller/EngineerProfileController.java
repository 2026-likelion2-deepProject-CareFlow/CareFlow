package com.careflow.engineer.controller;

import com.careflow.account_requests.service.AccountRequestsService;
import com.careflow.auth.security.CustomUserDetails;
import com.careflow.engineer.dto.CreateProfileRequest;
import com.careflow.engineer.dto.EngineerAccountRequest;
import com.careflow.engineer.dto.ProfileResponse;
import com.careflow.engineer.dto.UpdateProfileRequest;
import com.careflow.engineer.service.EngineerProfileService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/engineer/profile")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ENGINEER')")
public class EngineerProfileController {
    private final EngineerProfileService profileService;

    // 기사 프로필 최초 생성 (계정 생성 후 초기 정보 입력)
    @PostMapping
    public ResponseEntity<ProfileResponse> completeProfile(@AuthenticationPrincipal CustomUserDetails userDetails, @RequestBody @Valid CreateProfileRequest request){
        ProfileResponse response = profileService.completeProfile(userDetails.getUserId(), request);
        return ResponseEntity.ok(response);
    }

    // 기사 프로필 상세 조회
    @GetMapping
    public ResponseEntity<ProfileResponse> getProfile(@AuthenticationPrincipal CustomUserDetails userDetails) {
        ProfileResponse response = profileService.getProfile(userDetails.getUserId());
        return ResponseEntity.ok(response);
    }

    // 기사 프로필 수정
    @PutMapping
    public ResponseEntity<ProfileResponse> updateProfile(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @RequestBody UpdateProfileRequest request) {
        ProfileResponse response = profileService.updateProfile(userDetails.getUserId(), request);
        return ResponseEntity.ok(response);
    }

    // 기사 내비게이션 바 요약 프로필 조회
    @GetMapping("/me")
    public ResponseEntity<com.careflow.engineer.dto.EngineerNavbarResponse> getNavbarProfile(
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        return ResponseEntity.ok(profileService.getNavbarProfile(userDetails.getUserId()));
    }
}
