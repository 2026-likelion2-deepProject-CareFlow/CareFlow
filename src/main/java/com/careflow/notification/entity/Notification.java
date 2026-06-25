package com.careflow.notification.entity;

import com.careflow.user.entity.User;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "notifications",
        indexes = {
                @Index(name = "idx_notif_user", columnList = "user_id, created_at"),
                @Index(name = "idx_notif_type", columnList = "type")
        })
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Notification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "notification_id")
    private Long id;

    // 수신 대상 사용자 (users.user_id FK)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    // columnDefinition 삭제하지 말아주세요(H2 DB 테스트에 필요)
    @Column(name = "type", nullable = false, length = 20,
            columnDefinition = "ENUM('AS_STATUS','CONSUMABLE','WARRANTY','LMS') NOT NULL")
    private String type;

    @Column(name = "title", nullable = false, length = 200)
    private String title;

    @Column(name = "body", nullable = false, columnDefinition = "TEXT")
    private String body;

    // columnDefinition 삭제하지 말아주세요(H2 DB 테스트에 필요)
    @Column(name = "channel", nullable = false, length = 10,
            columnDefinition = "ENUM('SSE','PUSH','SMS','KAKAO') DEFAULT 'SSE'")
    private String channel;

    @Column(name = "created_at", nullable = false, updatable = false,
            columnDefinition = "DATETIME DEFAULT CURRENT_TIMESTAMP")
    private LocalDateTime createdAt = LocalDateTime.now();

    @Builder
    public Notification(User user, String type, String title, String body, String channel) {
        this.user    = user;
        this.type    = type;
        this.title   = title;
        this.body    = body;
        this.channel = (channel != null) ? channel : "SSE";
    }

    public static Notification createAsStatusNotification(User recipient, String title, String body) {
        return Notification.builder()
                .user(recipient)
                .type("AS_STATUS")
                .title(title)
                .body(body)
                .channel("SSE")
                .build();
    }
}
