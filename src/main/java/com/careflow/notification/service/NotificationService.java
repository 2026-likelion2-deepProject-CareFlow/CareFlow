package com.careflow.notification.service;

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
import java.util.Map;
import java.util.UUID;

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
}