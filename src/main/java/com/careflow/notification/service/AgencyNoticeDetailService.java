package com.careflow.notification.service;

import com.careflow.as_request.repository.AsRequestRepository;
import com.careflow.auth.security.CustomUserDetails;
import com.careflow.common.enums.Role;
import com.careflow.notification.dto.AgencyNoticeDetailResponse;
import com.careflow.notification.entity.Notification;
import com.careflow.notification.repository.NotificationRepository;
import com.careflow.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;

@Service
@RequiredArgsConstructor
public class AgencyNoticeDetailService {

    private final NotificationRepository notificationRepository;
    private final UserRepository userRepository;
    private final AsRequestRepository asRequestRepository;

    @Transactional(readOnly = true)
    public List<AgencyNoticeDetailResponse> getNoticeDetail(Long notificationId, CustomUserDetails userDetails)
            throws IllegalAccessException {

        // 역할 검증 — AGENCY만 접근 가능
        if (!"AGENCY".equals(userDetails.getRole())) {
            throw new IllegalAccessException("대행사 관리자만 접근할 수 있습니다.");
        }

        Notification notification = notificationRepository.findById(notificationId)
                .orElseThrow(() -> new NoSuchElementException("존재하지 않는 알림입니다."));

        Long agencyId = userDetails.getAgencyId();

        // 알림 수신 대상 user_id 범위 산정 — get-notifications.md와 동일 로직
        List<Long> recipientIds = new ArrayList<>();
        recipientIds.addAll(userRepository.findIdsByAgency_IdAndRole(agencyId, Role.ENGINEER));
        recipientIds.addAll(asRequestRepository.findDistinctCustomerIdsByAgencyId(agencyId));

        // 조회된 알림이 현재 대행사의 수신 대상 범위에 속하지 않으면 차단
        if (!recipientIds.contains(notification.getUser().getId())) {
            throw new IllegalAccessException("해당 대행사의 알림이 아닙니다.");
        }

        return List.of(new AgencyNoticeDetailResponse(
                notification.getId(),
                notification.getType(),
                notification.getTitle(),
                notification.getBody(),
                notification.getChannel(),
                notification.getCreatedAt()));
    }
}
