package com.careflow.common.response;

public record ErrorResponse(boolean success, String message) {

    /** 에러 응답 생성 — success 는 항상 false */
    public static ErrorResponse of(String message) {
        return new ErrorResponse(false, message);
    }
}
