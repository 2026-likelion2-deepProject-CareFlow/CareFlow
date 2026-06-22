package com.careflow.engineer.controller;

import com.careflow.account_requests.service.AccountRequestsService;
import com.careflow.engineer.dto.CreateProfileRequest;
import com.careflow.engineer.dto.EngineerAccountRequest;
import com.careflow.engineer.dto.ProfileResponse;
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

    @PostMapping
    public ResponseEntity<ProfileResponse> createProfile(@AuthenticationPrincipal Long userId, @RequestBody @Valid CreateProfileRequest request){   // 기사 프로필 생성
        ProfileResponse response = profileService.updateProfile(userId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PostMapping("/signup")
    public ResponseEntity<Long> engineerSignUpRequest(@Valid @RequestBody EngineerAccountRequest request){
        Long accountRequestId = profileService.requestEngineerAccount(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(accountRequestId);
    }

}
