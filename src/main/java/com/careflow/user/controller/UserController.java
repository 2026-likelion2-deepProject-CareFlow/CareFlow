package com.careflow.user.controller;

import com.careflow.user.dto.UserAddressResponse;
import com.careflow.user.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    /**
     * 고객 주소 정보 조회 API
     * TODO: @RequestParam Long userId → 추후 @AuthenticationPrincipal로 대체 예정
     */
    @GetMapping("/me/address")
    public ResponseEntity<UserAddressResponse> getMyAddress(@RequestParam Long userId) {
        return ResponseEntity.ok(userService.getMyAddress(userId));
    }
}
