package com.careflow.notification.controller;

import com.careflow.auth.security.JwtProvider;
import com.careflow.common.enums.Role;
import com.careflow.notification.entity.Notification;
import com.careflow.notification.repository.NotificationRepository;
import com.careflow.user.entity.User;
import com.careflow.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("local")
@Sql(scripts = "/cleanup.sql", executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
@DisplayName("EngineerNotificationController 통합 테스트 (H2)")
class EngineerNotificationControllerIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private JwtProvider jwtProvider;
    @Autowired private UserRepository userRepository;
    @Autowired private NotificationRepository notificationRepository;
    @Autowired private JdbcTemplate jdbcTemplate;

    private User engineer;
    private String engineerToken;

    @BeforeEach
    void setUp() {
        engineer = userRepository.save(User.builder()
                .email("engineer@noti.com").passwordHash("hashed")
                .name("알림기사").role(Role.ENGINEER).build());

        engineerToken = jwtProvider.generateAccessToken(
                engineer.getId(), engineer.getEmail(), "ENGINEER", null);
    }

    @Nested
    @DisplayName("GET /api/engineer/notifications")
    class GetNotifications {

        @Test
        @DisplayName("성공: 알림 3건 저장 후 size=2 페이징 요청 → 최신순 2건만 반환")
        void getNotifications_paging_success() throws Exception {
            // Given: 시간순으로 3개의 알림 생성 (JPA Auditing 우회를 위해 JdbcTemplate 사용)
            Notification n1 = notificationRepository.save(Notification.builder()
                    .user(engineer).type("AS_STATUS").title("알림 1").body("내용1").build());
            Notification n2 = notificationRepository.save(Notification.builder()
                    .user(engineer).type("LMS").title("알림 2").body("내용2").build());
            Notification n3 = notificationRepository.save(Notification.builder()
                    .user(engineer).type("CONSUMABLE").title("알림 3").body("내용3").build());

            // n3이 가장 최신이 되도록 시간 강제 조작
            jdbcTemplate.update("UPDATE notifications SET created_at = '2024-06-16 10:00:00' WHERE notification_id = ?", n1.getId());
            jdbcTemplate.update("UPDATE notifications SET created_at = '2024-06-17 10:00:00' WHERE notification_id = ?", n2.getId());
            jdbcTemplate.update("UPDATE notifications SET created_at = '2024-06-18 10:00:00' WHERE notification_id = ?", n3.getId());

            // When & Then: page=0, size=2 요청
            mockMvc.perform(get("/api/engineer/notifications")
                            .header("Authorization", "Bearer " + engineerToken)
                            .param("page", "0")
                            .param("size", "2"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.totalElements").value(3)) // 전체는 3개
                    .andExpect(jsonPath("$.content.length()").value(2)) // 현재 페이지는 2개
                    // 최신순(DESC) 이므로 알림3 -> 알림2 순서로 나와야 함
                    .andExpect(jsonPath("$.content[0].title").value("알림 3"))
                    .andExpect(jsonPath("$.content[0].createdAt").value("2024.06.18 10:00")) // 날짜 변환 검증
                    .andExpect(jsonPath("$.content[1].title").value("알림 2"));
        }

        @Test
        @DisplayName("성공: 타인의 알림은 조회되지 않음 (데이터 격리 검증)")
        void getNotifications_isolation_success() throws Exception {
            // 다른 기사 생성 및 알림 추가
            User otherEngineer = userRepository.save(User.builder()
                    .email("other@noti.com").passwordHash("hashed")
                    .name("타기사").role(Role.ENGINEER).build());
            notificationRepository.save(Notification.builder()
                    .user(otherEngineer).type("LMS").title("타인 알림").body("내용").build());

            // 현재 기사 토큰으로 조회 시 비어있어야 함
            mockMvc.perform(get("/api/engineer/notifications")
                            .header("Authorization", "Bearer " + engineerToken))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.totalElements").value(0))
                    .andExpect(jsonPath("$.content.length()").value(0));
        }
    }

    @Nested
    @DisplayName("PATCH /api/engineer/notifications/read — 선택 알림 일괄 읽음")
    class MarkSelectedRead {

        @Test
        @DisplayName("성공: 선택한 본인 알림만 읽음 처리, 미선택·타인 알림은 그대로 (BOLA 방어)")
        void markSelected_success_andIsolation() throws Exception {
            // Given: 본인 알림 3건 + 타인 알림 1건
            Notification n1 = notificationRepository.save(Notification.builder()
                    .user(engineer).type("AS_STATUS").title("알림1").body("내용1").build());
            Notification n2 = notificationRepository.save(Notification.builder()
                    .user(engineer).type("LMS").title("알림2").body("내용2").build());
            Notification n3 = notificationRepository.save(Notification.builder()
                    .user(engineer).type("CONSUMABLE").title("알림3").body("내용3").build());

            User other = userRepository.save(User.builder()
                    .email("other@noti.com").passwordHash("hashed")
                    .name("타기사").role(Role.ENGINEER).build());
            Notification otherNoti = notificationRepository.save(Notification.builder()
                    .user(other).type("LMS").title("타인알림").body("내용").build());

            // When: n1, n2 + 타인 알림 id 를 함께 전달 (타인 알림은 무시되어야 함)
            String body = String.format("{\"notificationIds\": [%d, %d, %d]}",
                    n1.getId(), n2.getId(), otherNoti.getId());

            mockMvc.perform(patch("/api/engineer/notifications/read")
                            .header("Authorization", "Bearer " + engineerToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isNoContent());

            // Then: 본인 선택 알림(n1, n2)만 읽음
            assertThat(notificationRepository.findById(n1.getId()).orElseThrow().isRead()).isTrue();
            assertThat(notificationRepository.findById(n2.getId()).orElseThrow().isRead()).isTrue();
            // 미선택(n3)은 그대로
            assertThat(notificationRepository.findById(n3.getId()).orElseThrow().isRead()).isFalse();
            // 타인 알림은 절대 변경되지 않음 (BOLA 방어)
            assertThat(notificationRepository.findById(otherNoti.getId()).orElseThrow().isRead()).isFalse();
        }

        @Test
        @DisplayName("실패: 인증 토큰 없음 — 401 Unauthorized")
        void fail_noAuth_401() throws Exception {
            mockMvc.perform(patch("/api/engineer/notifications/read")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"notificationIds\": [1]}"))
                    .andExpect(status().isUnauthorized());
        }
    }
}