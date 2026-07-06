package com.careflow.auth.entity;

import com.careflow.user.entity.User;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 로그인 시 자동 등록되는 신뢰 기기 — 설정 화면의 "신뢰 기기 관리"용
 * - deviceToken: 프론트가 localStorage에 발급/보관하는 UUID(X-Device-Id 헤더로 전달) — 실제 매칭 키.
 *   브라우저 업데이트 등으로 User-Agent 문자열이 바뀌어도 같은 기기로 계속 인식된다.
 * - deviceName: User-Agent 기반 표시용 라벨. 같은 deviceToken으로 재로그인하면 최신 값으로 갱신된다.
 */
@Entity
@Table(name = "trusted_devices",
        uniqueConstraints = @UniqueConstraint(name = "uk_trusted_device_user_token", columnNames = {"user_id", "device_token"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TrustedDevice {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "device_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "device_name", nullable = false, length = 255)
    private String deviceName;

    @Column(name = "device_token", nullable = false, length = 64)
    private String deviceToken;

    @Column(name = "last_used_at", nullable = false)
    private LocalDateTime lastUsedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Builder
    private TrustedDevice(User user, String deviceName, String deviceToken) {
        this.user = user;
        this.deviceName = deviceName;
        this.deviceToken = deviceToken;
        this.lastUsedAt = LocalDateTime.now();
        this.createdAt = LocalDateTime.now();
    }

    public static TrustedDevice create(User user, String deviceName, String deviceToken) {
        return TrustedDevice.builder().user(user).deviceName(deviceName).deviceToken(deviceToken).build();
    }

    // 동일 기기(deviceToken)로 재로그인 시 표시용 이름을 최신 User-Agent로 갱신 + 사용 일시 갱신 — 더티 체킹
    public void touch(String latestDeviceName) {
        if (latestDeviceName != null && !latestDeviceName.isBlank()) {
            this.deviceName = latestDeviceName;
        }
        this.lastUsedAt = LocalDateTime.now();
    }
}
