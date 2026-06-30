package com.careflow.appliance.entity;

import com.careflow.user.entity.User;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(
        name = "consumable_alerts",
        indexes = {
                // Quartz 배치 스케줄러가 매일 대상 소모품을 찾을 때 풀스캔을 방지하는 핵심 인덱스
                @Index(name = "idx_consumable_alert_date", columnList = "next_alert_date, is_active"),
                // 고객이 마이페이지에서 자신의 소모품 목록을 볼 때 사용하는 인덱스
                @Index(name = "idx_consumable_user", columnList = "user_id")
        }
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ConsumableAlert {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "alert_id")
    private Long alertId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "appliance_id", nullable = false)
    private Appliance appliance;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "consumable_name", nullable = false, length = 100)
    private String consumableName;

    // 교체 주기 (개월 단위)
    @Column(name = "cycle_months", nullable = false, columnDefinition = "TINYINT UNSIGNED")
    private Integer cycleMonths;

    // 최근 교체일 (최초 등록 시엔 null일 수 있음)
    @Column(name = "last_changed_at")
    private LocalDate lastChangedAt;

    // 다음 알림 발송 예정일
    @Column(name = "next_alert_date", nullable = false)
    private LocalDate nextAlertDate;

    // 알림 활성화 여부
    @Column(name = "is_active", nullable = false, columnDefinition = "TINYINT(1) DEFAULT 1")
    private boolean isActive = true;

    // H2 DB 테스트 에러 방지를 위한 기본값 처리 유지
    @Column(name = "created_at", nullable = false, updatable = false,
            columnDefinition = "DATETIME DEFAULT CURRENT_TIMESTAMP")
    private LocalDateTime createdAt = LocalDateTime.now();

    @Builder
    public ConsumableAlert(Appliance appliance, User user, String consumableName,
                           Integer cycleMonths, LocalDate lastChangedAt, LocalDate nextAlertDate) {
        this.appliance = appliance;
        this.user = user;
        this.consumableName = consumableName;
        this.cycleMonths = cycleMonths;
        this.lastChangedAt = lastChangedAt;
        this.nextAlertDate = nextAlertDate;
        this.isActive = true;
        this.createdAt = LocalDateTime.now();
    }

    // 🌟 [도메인 메서드] 소모품 교체 후 알림 주기 리셋 (더티 체킹용)
    public void updateAfterReplacement() {
        this.lastChangedAt = LocalDate.now();
        this.nextAlertDate = this.lastChangedAt.plusMonths(this.cycleMonths);
    }

    // 알림 ON/OFF 토글 메서드
    public void toggleActive(boolean active) {
        this.isActive = active;
    }
}