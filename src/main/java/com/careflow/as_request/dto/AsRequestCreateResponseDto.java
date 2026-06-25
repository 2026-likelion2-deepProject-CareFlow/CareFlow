package com.careflow.as_request.dto;

/**
 * A/S 접수 + 기사 배정 완료 응답 DTO
 * matchReason — AUTO 배차 시 어떤 Fallback 조건으로 매칭이 성사됐는지 사유 (MANUAL 배차 시 null)
 */
public record AsRequestCreateResponseDto(Long requestId, Long assignmentId, String matchReason) {
}
