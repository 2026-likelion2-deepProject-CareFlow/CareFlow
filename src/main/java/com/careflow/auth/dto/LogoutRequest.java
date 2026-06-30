package com.careflow.auth.dto;

/**
 * 로그아웃 요청 DTO
 * refreshToken은 선택 사항 — 전달 시 Redis에서 즉시 삭제하여 재발급 차단
 */
public record LogoutRequest(String refreshToken) {}
