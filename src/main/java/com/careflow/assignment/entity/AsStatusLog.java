package com.careflow.assignment.entity;

import com.careflow.as_request.entity.AsRequest;
import com.careflow.user.entity.User;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(
        name = "as_status_logs",
        indexes = {
                @Index(name = "idx_status_log_request", columnList = "request_id, created_at")
        }
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AsStatusLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "log_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "request_id", nullable = false)
    private AsRequest asRequest;

    // 상태 변경 주체 (기사 또는 관리자)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "changed_by", nullable = false)
    private User changedBy;

    // 변경 전 상태 — 최초 로그는 null 허용
    @Column(name = "from_status", length = 30)
    private String fromStatus;

    @Column(name = "to_status", nullable = false, length = 30,
            columnDefinition = "ENUM('WAITING','ENGINEER_DEPARTED','ENGINEER_ARRIVED','IN_PROGRESS','COMPLETED') DEFAULT 'WAITING'")
    private String toStatus;

    @Column(name = "memo", length = 255)
    private String memo;

    // columnDefinition 삭제하지 말아주세요(H2 DB 테스트에 필요)
    @Column(name = "created_at", nullable = false, updatable = false,
            columnDefinition = "DATETIME DEFAULT CURRENT_TIMESTAMP")
    private LocalDateTime createdAt = LocalDateTime.now();

    @Builder
    public AsStatusLog(AsRequest asRequest, User changedBy,
                       String fromStatus, String toStatus, String memo) {
        this.asRequest = asRequest;
        this.changedBy = changedBy;
        this.fromStatus = fromStatus;
        this.toStatus = toStatus;
        this.memo = memo;
    }
}
