package com.careflow.notification.service;

import com.careflow.notification.entity.Notification;
import com.careflow.notification.repository.EmitterRepository;
import com.careflow.notification.repository.NotificationRepository;
import com.careflow.user.entity.User;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("NotificationService 단위 테스트 (SSE 알림)")
class NotificationServiceTest {

    @InjectMocks
    private NotificationService notificationService;

    @Mock private EmitterRepository emitterRepository;
    @Mock private NotificationRepository notificationRepository;

    @Test
    @DisplayName("성공: 클라이언트가 SSE 구독을 요청하면 SseEmitter를 반환하고 저장한다.")
    void subscribe_Success() {
        // Given
        Long userId = 1L;
        SseEmitter mockEmitter = new SseEmitter();
        given(emitterRepository.save(anyString(), any(SseEmitter.class))).willReturn(mockEmitter);

        // When
        SseEmitter result = notificationService.subscribe(userId, "");

        // Then
        assertThat(result).isNotNull();
        verify(emitterRepository, times(1)).save(anyString(), any(SseEmitter.class));
    }

    @Test
    @DisplayName("성공: 알림 전송 시 DB에 저장하고, 연결된 모든 Emitter로 알림을 발송한다.")
    void send_Success() throws Exception {
        // Given
        User receiver = mock(User.class);
        given(receiver.getId()).willReturn(1L);

        SseEmitter mockEmitter = mock(SseEmitter.class);
        // userId 1L에 연결된 Emitter가 1개 있다고 가정
        given(emitterRepository.findAllEmitterStartWithByUserId("1"))
                .willReturn(Map.of("1_123456789", mockEmitter));

        // When
        notificationService.send(receiver, "AS_STATUS", "상태 업데이트", "출발했습니다.");

        // Then
        // 1. DB에 Notification 객체가 저장되었는지 확인
        verify(notificationRepository, times(1)).save(any(Notification.class));

        // (메모리 누수 방지를 위해 eventCache 저장 로직은 삭제되었으므로 검증 생략!)

        // 2. Emitter를 통해 실제 데이터가 전송되었는지 확인
        verify(mockEmitter, times(1)).send(any(SseEmitter.SseEventBuilder.class));
    }
}