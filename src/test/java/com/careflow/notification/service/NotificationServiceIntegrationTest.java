package com.careflow.notification.service;

import com.careflow.common.enums.Role;
import com.careflow.notification.entity.Notification;
import com.careflow.notification.repository.NotificationRepository;
import com.careflow.user.entity.User;
import com.careflow.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("local")
@Transactional
@Sql(scripts = "/cleanup.sql", executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
@DisplayName("NotificationService 통합 테스트 (H2 DB 연동)")
class NotificationServiceIntegrationTest {

    @Autowired
    private NotificationService notificationService;

    @Autowired
    private NotificationRepository notificationRepository;

    @Autowired
    private UserRepository userRepository;

    private User testUser;

    @BeforeEach
    void setUp() {
        // H2 DB에 알림 수신용 고객 데이터 적재
        testUser = userRepository.save(User.builder()
                .email("noti_test@careflow.com")
                .passwordHash("hashed")
                .name("알림테스트유저")
                .phone("010-9999-1111")
                .role(Role.CUSTOMER)
                .build());
    }

    @Test
    @DisplayName("성공: 알림 발송(send) 시 H2 데이터베이스 notifications 테이블에 정상적으로 INSERT 된다.")
    void send_Success_Integration() {
        // Given
        long initialCount = notificationRepository.count();

        // When
        notificationService.send(testUser, "AS_STATUS", "작업 상태 변경", "수리 기사님이 현장으로 출발했습니다.");

        // Then
        // 1. 전체 카운트가 1 증가했는지 검증
        long newCount = notificationRepository.count();
        assertThat(newCount).isEqualTo(initialCount + 1);

        // 2. 해당 유저의 최신 알림이 방금 보낸 내용과 정확히 일치하는지 DB 값 검증
        List<Notification> userNotifications = notificationRepository.findByUser_IdOrderByCreatedAtDesc(testUser.getId());
        assertThat(userNotifications).hasSize(1);

        Notification savedNoti = userNotifications.get(0);
        assertThat(savedNoti.getType()).isEqualTo("AS_STATUS");
        assertThat(savedNoti.getTitle()).isEqualTo("작업 상태 변경");
        assertThat(savedNoti.getBody()).isEqualTo("수리 기사님이 현장으로 출발했습니다.");
        assertThat(savedNoti.getChannel()).isEqualTo("SSE");
    }
}