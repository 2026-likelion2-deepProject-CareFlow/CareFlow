package com.careflow.assignment.service;

import com.careflow.as_request.entity.AsRequest;
import com.careflow.assignment.dto.AssignmentChangeEngineerRequest;
import com.careflow.assignment.dto.AssignmentChangeEngineerResponse;
import com.careflow.assignment.entity.AsAssignment;
import com.careflow.assignment.repository.AsAssignmentRepository;
import com.careflow.auth.security.CustomUserDetails;
import com.careflow.common.enums.AssignType;
import com.careflow.notification.entity.Notification;
import com.careflow.notification.repository.NotificationRepository;
import com.careflow.user.entity.User;
import com.careflow.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.NoSuchElementException;

@Service
@RequiredArgsConstructor
public class AssignmentChangeEngineerService {

    private final AsAssignmentRepository asAssignmentRepository;
    private final UserRepository userRepository;
    private final NotificationRepository notificationRepository;

    @Transactional
    public AssignmentChangeEngineerResponse changeEngineer(
            AssignmentChangeEngineerRequest request,
            CustomUserDetails userDetails) throws IllegalAccessException {

        // 역할 검증 — AGENCY만 접근 가능
        if (!"AGENCY".equals(userDetails.getRole())) {
            throw new IllegalAccessException("대행사 관리자만 접근할 수 있습니다.");
        }

        // 기존 배정 조회
        AsAssignment existing = asAssignmentRepository.findById(request.assignmentId())
                .orElseThrow(() -> new NoSuchElementException("해당 배정 내역을 찾을 수 없습니다."));

        // 대행사 소속 확인
        if (!existing.getAgency().getId().equals(userDetails.getAgencyId())) {
            throw new IllegalAccessException("소속 대행사의 배정만 변경할 수 있습니다.");
        }

        // 수락 대기(WAITING) 또는 거절(REJECTED) 상태 배정만 기사 변경 가능
        // - WAITING: 아직 기사가 응답하지 않은 배정을 다른 기사로 교체
        // - REJECTED: 재배차 알림(ReassignModal)에서 거절된 배정에 새 기사를 수동 배정
        boolean isWaiting = "WAITING".equals(existing.getStatus());
        boolean isRejected = "REJECTED".equals(existing.getStatus());
        if (!isWaiting && !isRejected) {
            throw new IllegalStateException("수락 대기 또는 거절 상태의 배정만 기사를 변경할 수 있습니다.");
        }

        // 새 기사 조회
        User newEngineer = userRepository.findById(request.newEngineerId())
                .orElseThrow(() -> new NoSuchElementException("해당 수리 기사를 찾을 수 없습니다."));

        // 기존 배정 취소(REJECTED) — 이미 REJECTED 상태면 cancel() 재호출 시 예외가 발생하므로 WAITING일 때만 호출
        if (isWaiting) {
            existing.cancel();
        }

        // 새 배정 생성 (WAITING)
        AsAssignment newAssignment = AsAssignment.create(
                existing.getAsRequest(),
                newEngineer,
                existing.getAgency(),
                AssignType.MANUAL
        );
        AsAssignment saved = asAssignmentRepository.save(newAssignment);

        // [신규 (2)(3)] 새로 배정된 기사에게 "새 작업 배정" 알림 저장 (직접 저장 방식).
        AsRequest asRequest = existing.getAsRequest();
        String applianceInfo = asRequest.getAppliance().getBrand() + " " + asRequest.getAppliance().getModelName();
        String assignBody = String.format(
                "[%s] %s 고객님 작업이 배정되었습니다. 방문 예정: %s %s. 작업 관리에서 수락 여부를 확인해 주세요.",
                applianceInfo,
                asRequest.getCustomer().getName(),
                asRequest.getScheduledDate(),
                asRequest.getScheduledTime());
        Notification assignNotification = Notification.createAsStatusNotification(
                newEngineer, "새 작업이 배정되었습니다", assignBody);
        notificationRepository.save(assignNotification);

        return new AssignmentChangeEngineerResponse(
                saved.getId(),
                saved.getAsRequest().getId(),
                newEngineer.getId(),
                newEngineer.getName(),
                saved.getStatus(),
                saved.getAssignedAt()
        );
    }
}
