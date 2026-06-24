package com.careflow.as_request.dto;

/**
 * A/S 접수 + 기사 배정 완료 응답 DTO
 */
public record AsRequestCreateResponseDto(Long requestId, Long assignmentId) {
}
