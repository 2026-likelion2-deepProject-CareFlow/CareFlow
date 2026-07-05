package com.careflow.notification.service;

import com.careflow.notification.dto.NotificationCategoryStat;
import com.careflow.notification.dto.NotificationResponse;
import com.careflow.notification.entity.Notification;
import com.careflow.notification.repository.EmitterRepository;
import com.careflow.notification.repository.NotificationRepository;
import com.careflow.user.entity.User;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationService {

    private final EmitterRepository emitterRepository;
    private final NotificationRepository notificationRepository;

    // 기본 타임아웃 1시간
    private static final Long DEFAULT_TIMEOUT = 60L * 1000 * 60;

    /**
     * 클라이언트 SSE 연결 구독
     */
    public SseEmitter subscribe(Long userId, String lastEventId) {
        // Emitter 고유 식별자 생성 (유실된 데이터 처리를 위해 타임스탬프 결합)
        String emitterId = userId + "_" + UUID.randomUUID().toString();
        SseEmitter emitter = emitterRepository.save(emitterId, new SseEmitter(DEFAULT_TIMEOUT));

        // 연결 종료 시 리포지토리에서 제거 콜백
        emitter.onCompletion(() -> emitterRepository.deleteById(emitterId));
        emitter.onTimeout(() -> emitterRepository.deleteById(emitterId));
        emitter.onError((e) -> emitterRepository.deleteById(emitterId));

        // 503 Service Unavailable 에러 방지를 위한 더미 이벤트 최초 발송
        String eventId = userId + "_" + System.currentTimeMillis();
        sendToClient(emitter, emitterId, eventId, "EventStream Created. [userId=" + userId + "]");

        // TODO: lastEventId 가 존재한다면, 캐시에서 유실된 이벤트 전송 로직 추가 가능

        return emitter;
    }

    /**
     * 실제 알림 전송 (비즈니스 로직에서 호출)
     */
    public void send(User receiver, String type, String title, String body) {
        // 1. DB에 알림 내역 저장
        Notification notification = Notification.builder()
                .user(receiver)
                .type(type)
                .title(title)
                .body(body)
                .channel("SSE")
                .build();
        notificationRepository.save(notification);

        // 2. 수신자의 연결된 Emitter 찾기
        String receiverId = String.valueOf(receiver.getId());
        String eventId = receiverId + "_" + System.currentTimeMillis();
        Map<String, SseEmitter> emitters = emitterRepository.findAllEmitterStartWithByUserId(receiverId);

        // 3. 연결된 모든 클라이언트 창에 이벤트 발송
        emitters.forEach((key, emitter) -> {
            // 🌟 리뷰 반영: 캐시 로직 제거됨
            sendToClient(emitter, key, eventId, notification);
        });
    }

    /**
     * Emitter로 데이터 전송
     */
    private void sendToClient(SseEmitter emitter, String emitterId, String eventId, Object data) {
        try {
            emitter.send(SseEmitter.event()
                    .id(eventId)
                    .name("sse")
                    .data(data));
        } catch (Exception exception) { // 🌟 리뷰 반영: IOException 뿐만 아니라 IllegalStateException 등 광범위 예외 포착
            emitterRepository.deleteById(emitterId);
            log.error("SSE 전송 오류 및 연결 삭제 - emitterId: {}", emitterId, exception);
        }
    }

    /**
     * [기사용 API] 알림 수신함 목록 조회 (페이징 + 타입 필터 지원)
     */
    @org.springframework.transaction.annotation.Transactional(readOnly = true)
    public Page<NotificationResponse> getNotifications(Long userId, String type, Pageable pageable) {
        // type이 빈 문자열("")로 오면 null로 변환하여 전체 검색이 되도록 처리
        String filterType = (type == null || type.isBlank()) ? null : type;

        Page<Notification> notifications = notificationRepository.findByUserIdAndTypeWithPaging(userId, filterType, pageable);
        return notifications.map(NotificationResponse::from);
    }

    /**
     * [고객용 API] 알림 수신함 목록 조회 (페이징 + 타입 필터 + "안읽음만 보기" 필터 지원)
     */
    @org.springframework.transaction.annotation.Transactional(readOnly = true)
    public Page<NotificationResponse> getNotifications(Long userId, String type, boolean unreadOnly, Pageable pageable) {
        String filterType = (type == null || type.isBlank()) ? null : type;

        Page<Notification> notifications =
                notificationRepository.findByUserIdAndTypeAndUnreadOnly(userId, filterType, unreadOnly, pageable);
        return notifications.map(NotificationResponse::from);
    }

    /**
     * [고객용 API] 알림 카테고리별(AS_STATUS/CONSUMABLE/WARRANTY) count·unreadCount 요약
     * 고객 알림센터 KPI 카드가 "N건 / 읽지 않음 N건" 형태를 요구하므로, 기사용 getNotificationSummary와
     * 별도로 유지한다 — 기존 기사 화면(Map<String, Long> 단순 합계) 계약을 건드리지 않기 위함.
     */
    @org.springframework.transaction.annotation.Transactional(readOnly = true)
    public Map<String, NotificationCategoryStat> getNotificationSummaryWithUnread(Long userId) {
        Map<String, Long> totalByType = new HashMap<>();
        for (Object[] row : notificationRepository.countGroupByTypeForUser(userId)) {
            totalByType.put((String) row[0], (Long) row[1]);
        }

        Map<String, Long> unreadByType = new HashMap<>();
        for (Object[] row : notificationRepository.countUnreadGroupByTypeForUser(userId)) {
            unreadByType.put((String) row[0], (Long) row[1]);
        }

        Map<String, NotificationCategoryStat> summary = new LinkedHashMap<>();
        long totalCount = 0L;
        long totalUnread = 0L;
        for (String type : List.of("AS_STATUS", "CONSUMABLE", "WARRANTY")) {
            long count = totalByType.getOrDefault(type, 0L);
            long unread = unreadByType.getOrDefault(type, 0L);
            summary.put(type, new NotificationCategoryStat(count, unread));
            totalCount += count;
            totalUnread += unread;
        }
        summary.put("total", new NotificationCategoryStat(totalCount, totalUnread));

        return summary;
    }

    /**
     * [기사용 API] 알림 수신함 통계 요약 (유형별 알림 건수 + 전체 건수)
     */
    @org.springframework.transaction.annotation.Transactional(readOnly = true)
    public Map<String, Long> getNotificationSummary(Long userId) {
        List<Object[]> rows = notificationRepository.countGroupByTypeForUser(userId);

        // 프론트엔드에서 undefined 에러가 나지 않도록 기본값 0으로 초기화
        Map<String, Long> summary = new LinkedHashMap<>();
        summary.put("ALL", 0L);
        summary.put("AS_STATUS", 0L);
        summary.put("LMS", 0L);
        summary.put("CONSUMABLE", 0L);
        summary.put("WARRANTY", 0L);

        long totalCount = 0L;

        // DB에서 조회된 타입별 갯수를 덮어쓰기 및 전체 갯수(ALL) 합산
        for (Object[] row : rows) {
            String type = (String) row[0];
            Long count = (Long) row[1];

            summary.put(type, count);
            totalCount += count;
        }

        // 총합을 ALL 키에 저장
        summary.put("ALL", totalCount);

        return summary;
    }

    /**
     * [기사용 API] 알림 단건 읽음 처리
     */
    @org.springframework.transaction.annotation.Transactional
    public void markAsRead(Long userId, Long notificationId) {
        Notification notification = notificationRepository.findById(notificationId)
                .orElseThrow(() -> new NoSuchElementException("존재하지 않는 알림입니다."));

        // 본인 소유의 알림인지 철저히 검증 (BOLA 취약점 방어)
        if (!notification.getUser().getId().equals(userId)) {
            throw new IllegalStateException("본인의 알림만 읽음 처리할 수 있습니다.");
        }

        notification.markAsRead(); // 엔티티 더티 체킹으로 자동 UPDATE
    }

    /**
     * [기사용 API] 알림 전체 읽음 처리
     */
    @org.springframework.transaction.annotation.Transactional
    public void markAllAsRead(Long userId) {
        // 벌크 연산 쿼리 호출로 성능 최적화
        notificationRepository.markAllAsReadByUserId(userId);
    }
}