// 파일 경로: src/test/java/com/careflow/notification/service/NotificationServiceTest.java
package com.careflow.notification.service;

import com.careflow.notification.dto.NotificationResponse;
import com.careflow.notification.entity.Notification;
import com.careflow.notification.repository.EmitterRepository;
import com.careflow.notification.repository.NotificationRepository;
import com.careflow.user.entity.User;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("NotificationService 단위 테스트 (SSE 알림 및 수신함 조회)")
class NotificationServiceTest {

    @InjectMocks
    private NotificationService notificationService;

    @Mock private EmitterRepository emitterRepository;
    @Mock private NotificationRepository notificationRepository;

    // ─────────────────────────────────────────────
    // 기존 테스트: SSE 구독 및 알림 발송
    // ─────────────────────────────────────────────
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
        given(emitterRepository.findAllEmitterStartWithByUserId("1"))
                .willReturn(Map.of("1_123456789", mockEmitter));

        // When
        notificationService.send(receiver, "AS_STATUS", "상태 업데이트", "출발했습니다.");

        // Then
        verify(notificationRepository, times(1)).save(any(Notification.class));
        verify(mockEmitter, times(1)).send(any(SseEmitter.SseEventBuilder.class));
    }

    // ─────────────────────────────────────────────
    // 신규 추가 (Phase 1): 알림 수신함 페이징 조회
    // ─────────────────────────────────────────────
    @Nested
    @DisplayName("getNotifications — 알림 수신함 페이징 조회")
    class GetNotifications {

        @Test
        @DisplayName("성공: 엔티티 목록을 받아 포맷팅된 DTO Page로 정상 변환한다")
        void success_convertsToFormattedDtoPage() {
            // Given
            Long userId = 1L;
            PageRequest pageRequest = PageRequest.of(0, 10);

            Notification noti1 = mock(Notification.class);
            given(noti1.getId()).willReturn(5L);
            given(noti1.getType()).willReturn("AS_STATUS");
            given(noti1.getTitle()).willReturn("배정 알림");
            given(noti1.getBody()).willReturn("새 작업 배정");
            given(noti1.getChannel()).willReturn("SSE");
            // 2024년 6월 18일 09시 30분
            given(noti1.getCreatedAt()).willReturn(LocalDateTime.of(2024, 6, 18, 9, 30));

            Page<Notification> mockPage = new PageImpl<>(List.of(noti1), pageRequest, 1);

            // 🌟 핵심 수정 포인트: 바뀐 레포지토리 메서드(findByUserIdAndTypeWithPaging)와 파라미터 매핑!
            // type을 null(전체 조회)로 넘겼다고 가정하고 모킹합니다.
            given(notificationRepository.findByUserIdAndTypeWithPaging(eq(userId), isNull(), eq(pageRequest)))
                    .willReturn(mockPage);

            // When
            // 🌟 핵심 수정 포인트: 서비스 호출 시 중간에 null(type 파라미터)을 넣어줍니다!
            Page<NotificationResponse> result = notificationService.getNotifications(userId, null, pageRequest);

            // Then
            assertThat(result.getTotalElements()).isEqualTo(1);
            assertThat(result.getContent()).hasSize(1);

            NotificationResponse dto = result.getContent().get(0);
            assertThat(dto.getId()).isEqualTo(5L);
            // ID 포맷팅 검증 (NOT-yyyyMMdd-005)
            assertThat(dto.getNotificationId()).isEqualTo("NOT-20240618-005");
            // 날짜 포맷팅 검증
            assertThat(dto.getCreatedAt()).isEqualTo("2024.06.18 09:30");
            assertThat(dto.getType()).isEqualTo("AS_STATUS");
        }
    }
}