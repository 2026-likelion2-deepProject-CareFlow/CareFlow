package com.careflow.common.exception;

import com.careflow.common.response.ErrorResponse;
import org.springframework.core.NestedExceptionUtils;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.authorization.AuthorizationDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.nio.file.AccessDeniedException;
import java.util.NoSuchElementException;

@RestControllerAdvice
public class GlobalExceptionHandler {

    // Bean Validation(@Valid) 실패 시 스프링 내부 예외 텍스트를 그대로 노출하지 않고
    // 첫 번째 필드 오류의 메시지만 깔끔하게 뽑아서 반환 (예: "비밀번호는 8자 이상 64자 이하로 입력해주세요.")
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException e) {
        String message = e.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(fe -> fe.getDefaultMessage())
                .orElse("입력값이 올바르지 않습니다.");
        return ResponseEntity.badRequest()
                .body(ErrorResponse.of(message));
    }

    // 필수 요청 파라미터 누락 시 400 반환
    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ErrorResponse> handleMissingParam(MissingServletRequestParameterException e) {
        return ResponseEntity.badRequest()
                .body(ErrorResponse.of(e.getMessage()));
    }

    // JSON 바디 파싱 실패 시 400 반환.
    // XssSanitizingStringDeserializer가 던진 IllegalArgumentException은 Jackson이 내부적으로
    // JsonMappingException으로 감싸서 이 예외의 원인 체인 안에 들어오므로, 가장 근본 원인을 꺼내
    // XSS 방어 메시지든 일반 파싱 오류든 일관된 형식으로 응답한다.
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleMessageNotReadable(HttpMessageNotReadableException e) {
        Throwable cause = NestedExceptionUtils.getMostSpecificCause(e);
        String message = (cause instanceof IllegalArgumentException) ? cause.getMessage() : "요청 본문을 읽을 수 없습니다.";
        return ResponseEntity.badRequest().body(ErrorResponse.of(message));
    }

    @ExceptionHandler(NoSuchElementException.class)
    public ResponseEntity<ErrorResponse> handleNoSuchElement(NoSuchElementException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ErrorResponse.of(e.getMessage()));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> handleIllegalArgument(IllegalArgumentException e) {
        return ResponseEntity.badRequest()
                .body(ErrorResponse.of(e.getMessage()));
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<ErrorResponse> handleIllegalState(IllegalStateException e) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(ErrorResponse.of(e.getMessage()));
    }

    @ExceptionHandler(IllegalAccessException.class)
    public ResponseEntity<ErrorResponse> handleIllegalAccess(IllegalAccessException e) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(ErrorResponse.of(e.getMessage()));
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ErrorResponse> handleDataIntegrity(DataIntegrityViolationException e) {
        String msg = e.getMostSpecificCause().getMessage();

        if (msg != null && msg.contains("uk_users_email")) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(ErrorResponse.of("이미 사용 중인 이메일입니다."));
        }

        if (msg != null && msg.contains("uk_eng_schedule")) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(ErrorResponse.of("해당 날짜에 이미 등록된 근무표가 존재합니다. (동시 요청 방어)"));
        }

        if (msg != null && msg.contains("uk_lms_confirm_year")) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(ErrorResponse.of("이미 이수한 콘텐츠입니다. (동시 요청 방어)"));
        }

        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ErrorResponse.of("데이터 처리 중 충돌이 발생했습니다. 잠시 후 다시 시도해주세요."));
    }

    // 예상치 못한 에러
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnexpected(Exception e) {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ErrorResponse.of(e.getMessage()));
    }

    // 🌟 이 부분을 새로 추가해 주세요! (시큐리티 권한 예외 처리)
    @ExceptionHandler({AccessDeniedException.class, AuthorizationDeniedException.class})
    public ResponseEntity<ErrorResponse> handleAccessDenied(Exception e) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(ErrorResponse.of("접근 권한이 없습니다."));
    }
}
