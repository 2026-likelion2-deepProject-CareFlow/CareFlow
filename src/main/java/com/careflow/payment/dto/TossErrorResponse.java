package com.careflow.payment.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/** 토스페이먼츠 API 에러 응답: { "code": "...", "message": "..." } */
@JsonIgnoreProperties(ignoreUnknown = true)
public record TossErrorResponse(String code, String message) {}
