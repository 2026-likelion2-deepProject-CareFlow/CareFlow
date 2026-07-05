package com.careflow.admin.controller;

import com.careflow.admin.dto.request.BadgeCriteriaDto;
import com.careflow.auth.security.CustomUserDetails;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/badge-criteria")
@RequiredArgsConstructor
public class AdminBadgeCriteriaController {

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private static final String REDIS_KEY = "admin:badge:criteria";

    @GetMapping
    public ResponseEntity<BadgeCriteriaDto> getCriteria(@AuthenticationPrincipal CustomUserDetails userDetails) throws Exception {
        checkAdminRole(userDetails);
        String json = redisTemplate.opsForValue().get(REDIS_KEY);
        if (json == null) {
            return ResponseEntity.ok(new BadgeCriteriaDto("B", 75));
        }
        return ResponseEntity.ok(objectMapper.readValue(json, BadgeCriteriaDto.class));
    }

    @PutMapping
    public ResponseEntity<Void> updateCriteria(
            @RequestBody BadgeCriteriaDto dto,
            @AuthenticationPrincipal CustomUserDetails userDetails) throws Exception {
        checkAdminRole(userDetails);
        redisTemplate.opsForValue().set(REDIS_KEY, objectMapper.writeValueAsString(dto));
        return ResponseEntity.ok().build();
    }

    private void checkAdminRole(CustomUserDetails userDetails) throws IllegalAccessException {
        if (!"ADMIN".equals(userDetails.getRole())) throw new IllegalAccessException("관리자 권한이 필요합니다.");
    }
}