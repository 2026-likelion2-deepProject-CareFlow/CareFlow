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
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/engineers/me/profile")
@RequiredArgsConstructor
public class EngineerProfileController {
    private final EngineerProfileService profileService;

    @PutMapping
    public ResponseEntity<ProfileResponse> completeProfile(@AuthenticationPrincipal CustomUserDetails userDetails, @RequestBody @Valid CreateProfileRequest request){   // 기사 프로필 생성
        ProfileResponse response = profileService.completeProfile(userDetails.getUserId(), request);
        return ResponseEntity.ok(response);
    }

    @GetMapping
    public ResponseEntity<ProfileResponse> getProfile(@AuthenticationPrincipal CustomUserDetails userDetails) { // 프로필 상세 조회
        ProfileResponse response = profileService.getProfile(userDetails.getUserId());
        return ResponseEntity.ok(response);
    }

    @PatchMapping
    public ResponseEntity<ProfileResponse> updateProfile(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @RequestBody UpdateProfileRequest request) {    // 프로필 수정
        ProfileResponse response = profileService.updateProfile(userDetails.getUserId(), request);
        return ResponseEntity.ok(response);
    }
}
