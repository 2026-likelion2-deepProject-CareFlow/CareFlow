package com.careflow.notification.service;

import com.careflow.as_request.repository.AsRequestRepository;
import com.careflow.auth.security.CustomUserDetails;
import com.careflow.common.enums.Role;
import com.careflow.notification.dto.AgencyNotificationResponse;
import com.careflow.notification.entity.Notification;
import com.careflow.notification.repository.NotificationRepository;
import com.careflow.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class AgencyNotificationService {

    private final NotificationRepository notificationRepository;
    private final UserRepository userRepository;
    private final AsRequestRepository asRequestRepository;

    // notifications.type ENUM('AS_STATUS','CONSUMABLE','WARRANTY','LMS')과 동일하게 유지
    private static final Set<String> ALLOWED_TYPES = Set.of("AS_STATUS", "CONSUMABLE", "WARRANTY", "LMS");

    @Transactional(readOnly = true)
    public AgencyNotificationResponse getNotifications(CustomUserDetails userDetails, Pageable pageable, String type)
            throws IllegalAccessException {

        // 역할 검증 — AGENCY만 접근 가능
        if (!"AGENCY".equals(userDetails.getRole())) {
            throw new IllegalAccessException("대행사 관리자만 접근할 수 있습니다.");
        }

        // type 파라미터 검증 — 허용된 ENUM 값이 아니면 400
        if (type != null && !ALLOWED_TYPES.contains(type)) {
            throw new IllegalArgumentException("type 값이 올바르지 않습니다. (허용: AS_STATUS, CONSUMABLE, WARRANTY, LMS)");
        }

        Long agencyId = userDetails.getAgencyId();

        // 알림 수신 대상 user_id 범위 산정 — 대행사 소속 수리기사 + 그 대행사로부터 A/S를 받은 고객 + 로그인한 본인 계정
        List<Long> recipientIds = resolveRecipientIds(agencyId, userDetails.getUserId());

        if (recipientIds.isEmpty()) {
            return new AgencyNotificationResponse(
                    new AgencyNotificationResponse.Stats(0, 0, 0),
                    List.of(),
                    0, 0, pageable.getPageNumber(), pageable.getPageSize());
        }

        // content/페이징은 type 필터 적용, stats(전체 통계)는 type과 무관하게 항상 전체 범위 기준
        Page<Notification> page = notificationRepository
                .findByUser_IdInAndTypeOrderByCreatedAtDesc(recipientIds, type, pageable);

        long totalCount = notificationRepository.countByUser_IdIn(recipientIds);
        long unreadCount = notificationRepository.countByUser_IdInAndIsReadFalse(recipientIds);

        LocalDateTime startOfDay = LocalDate.now().atStartOfDay();
        LocalDateTime endOfDay = startOfDay.plusDays(1);
        long todayCount = notificationRepository.countByUser_IdInAndCreatedAtBetween(recipientIds, startOfDay, endOfDay);

        List<AgencyNotificationResponse.NotificationItem> content = page.getContent().stream()
                .map(n -> new AgencyNotificationResponse.NotificationItem(
                        n.getId(), n.getType(), n.getTitle(), n.getBody(), n.getChannel(), n.isRead(), n.getCreatedAt()))
                .toList();

        return new AgencyNotificationResponse(
                new AgencyNotificationResponse.Stats(totalCount, unreadCount, todayCount),
                content,
                page.getTotalElements(),
                page.getTotalPages(),
                page.getNumber(),
                page.getSize());
    }

    /**
     * 알림 읽음 처리 (PATCH /api/agency/notifications/{notificationId}/read)
     *
     * unreadCount는 이 API가 직접 내려주지 않는다 — 프론트엔드가 처리 후
     * GET /api/agency/notifications를 재호출해 최신 stats.unreadCount를 반영한다.
     */
    @Transactional
    public void markAsRead(CustomUserDetails userDetails, Long notificationId) throws IllegalAccessException {

        // 역할 검증 — AGENCY만 접근 가능
        if (!"AGENCY".equals(userDetails.getRole())) {
            throw new IllegalAccessException("대행사 관리자만 접근할 수 있습니다.");
        }

        Notification notification = notificationRepository.findById(notificationId)
                .orElseThrow(() -> new NoSuchElementException("존재하지 않는 알림입니다."));

        // 소속 검증 — 이 대행사의 알림 수신 대상 범위(소속 기사 + 그 기사에게 A/S를 받은 고객 + 본인 계정)에 속해야 함
        List<Long> recipientIds = resolveRecipientIds(userDetails.getAgencyId(), userDetails.getUserId());
        if (!recipientIds.contains(notification.getUser().getId())) {
            throw new IllegalAccessException("본인 대행사 소속 알림만 읽음 처리할 수 있습니다.");
        }

        notification.markAsRead();
    }

    /**
     * 알림 전체 읽음 처리 (PATCH /api/agency/notifications/read-all)
     *
     * read-all은 결과가 결정적(전체 읽음 → unreadCount는 반드시 0)이므로,
     * markAsRead()와 달리 GET 재호출 안내 없이 프론트엔드가 즉시 로컬 상태를 갱신한다.
     */
    @Transactional
    public void markAllAsRead(CustomUserDetails userDetails) throws IllegalAccessException {

        // 역할 검증 — AGENCY만 접근 가능
        if (!"AGENCY".equals(userDetails.getRole())) {
            throw new IllegalAccessException("대행사 관리자만 접근할 수 있습니다.");
        }

        List<Long> recipientIds = resolveRecipientIds(userDetails.getAgencyId(), userDetails.getUserId());
        if (recipientIds.isEmpty()) {
            return;
        }

        notificationRepository.markAllAsReadByUserIds(recipientIds);
    }

    // 알림 수신 대상 user_id 범위 산정 — 대행사 소속 수리기사 + 그 대행사로부터 A/S를 받은 고객 + 로그인한 본인 계정
    // (본인 계정 포함 — 로그인 알림 등 대행사 관리자 본인에게 직접 발송되는 알림도 이 알림센터에서 조회되어야 함)
    private List<Long> resolveRecipientIds(Long agencyId, Long userId) {
        List<Long> recipientIds = new ArrayList<>();
        recipientIds.addAll(userRepository.findIdsByAgency_IdAndRole(agencyId, Role.ENGINEER));
        recipientIds.addAll(asRequestRepository.findDistinctCustomerIdsByAgencyId(agencyId));
        recipientIds.add(userId);
        return recipientIds;
    }
}
