package com.careflow.assignment.dto;

// 재배차 처리 결과 응답 DTO
// result = "REASSIGNED"        → AUTO 자동 재배차 완료, newAssignmentId + matchReason 반환
// result = "MANUAL_NOTIFIED"   → MANUAL 배차 건이라 재배차 불가, 고객 알림 발송, notificationId 반환
public record AssignmentReassignResponse(
        String result,
        Long newAssignmentId,
        String matchReason,   // AUTO 재배차 성공 시 어떤 Fallback 조건으로 매칭됐는지 사유 (MANUAL 시 null)
        Long notificationId,
        String message
) {
    public static AssignmentReassignResponse reassigned(Long newAssignmentId, String matchReason) {
        return new AssignmentReassignResponse(
                "REASSIGNED", newAssignmentId, matchReason, null, "재배차가 완료되었습니다.");
    }

    public static AssignmentReassignResponse manualNotified(Long notificationId) {
        return new AssignmentReassignResponse(
                "MANUAL_NOTIFIED", null, null, notificationId, "고객에게 재신청 안내 알림을 발송했습니다.");
    }
}
