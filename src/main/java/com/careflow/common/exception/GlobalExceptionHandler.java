package com.careflow.common.exception;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.NoSuchElementException;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(value = {MethodArgumentNotValidException.class})
    public ResponseEntity<String> handleException(Exception e) {
        return ResponseEntity.badRequest().body(e.getMessage());
    }

    @ExceptionHandler(value = {NoSuchElementException.class})
    public ResponseEntity<String> handleNoSuchElementException(Exception e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(e.getMessage());
    }

    @ExceptionHandler(value = {IllegalArgumentException.class})
    public ResponseEntity<String> handleIllegalArgumentException(IllegalArgumentException e) {
        return ResponseEntity.badRequest().body(e.getMessage());
    }

    @ExceptionHandler(value = {IllegalStateException.class})
    public ResponseEntity<String> handleIllegalStateException(IllegalStateException e) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(e.getMessage());
    }

    @ExceptionHandler(value = {IllegalAccessException.class})
    public ResponseEntity<String> handleIllegalAccessException(IllegalAccessException e) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(e.getMessage());
    }

    // 예상치 못한 에러 발생 시
    @ExceptionHandler(value = {Exception.class})
    public ResponseEntity<String> handleNotExpectedException(Exception e) {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<String> handleDataIntegrityException(DataIntegrityViolationException e) {
        String msg = e.getMostSpecificCause().getMessage();

        if (msg != null && msg.contains("uk_eng_schedule")) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body("해당 날짜에 이미 등록된 근무표가 존재합니다. (동시 요청 방어)");
        }

        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body("데이터 처리 중 충돌이 발생했습니다. 잠시 후 다시 시도해주세요.");
    }
}