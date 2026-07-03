package com.careflow.lms.entity;

import com.careflow.user.entity.User;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * LMS 이수 이력
 *
 * - 기사(ENGINEER)가 콘텐츠를 열람·완료 처리할 때 INSERT
 * - completion_year 기준으로 연간 이수 사이클 관리
 *   → 매년 1월 1일 Quartz가 engineer_profiles.is_lms_completed=0 초기화해도
 *     이 테이블의 과거 이력은 연도별로 영구 보존
 * - confirmed_version: 이수 당시 lms_contents.version 스냅샷
 *   → 콘텐츠 개정 이후에도 이수 당시 기준 버전 확인 가능
 *
 * [중복 이수 방지]
 * DB 레벨: uk_lms_confirm_user_content_year (user_id, content_id, completion_year)
 * → 동일 연도에 같은 콘텐츠 중복 이수 행 방지
 *
 * 매핑 테이블: lms_confirmations
 */
@Entity
@Table(
    name = "lms_confirmations",
    indexes = {
        @Index(name = "idx_lms_confirm_user_year", columnList = "user_id, completion_year"),
        @Index(name = "idx_lms_confirm_content",   columnList = "content_id, confirmed_at")
    }
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class LmsConfirmation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "confirmation_id", nullable = false, updatable = false)
    private Long confirmationId;


    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false, updatable = false)
    private User user;


    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "content_id", nullable = false, updatable = false)
    private LmsContent content;

    /**
     * 이수 연도 (매년 리셋 사이클 기준)
     * UK 구성 컬럼 3/3
     *
     * DB: YEAR 타입 → Java: Integer (JPA YEAR 직접 매핑 없음)
     * 매년 1월 1일 Quartz 스케줄러가 engineer_profiles.is_lms_completed=0 초기화 후
     * 신규 연도 이수 사이클 시작 → 이 컬럼으로 연도별 이수 여부 구분
     */
    @Column(name = "completion_year", nullable = false, updatable = false)
    private Integer completionYear;


    @Column(name = "confirmed_version", nullable = false, length = 10)
    private String confirmedVersion;


    @Column(name = "confirmed_at", nullable = false)
    private LocalDateTime confirmedAt;

    @Column(name = "is_active", nullable = false)
    private boolean isActive = true;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    // ─────────────────────────────────────────────
    // 빌더
    // ─────────────────────────────────────────────

    @Builder
    private LmsConfirmation(User user, LmsContent content, Integer completionYear) {
        this.user             = user;
        this.content          = content;
        this.completionYear   = completionYear;
        // 이수 당시 버전 스냅샷 — 생성 시점에 content.version을 복사
        this.confirmedVersion = content.getVersion();
        this.confirmedAt      = LocalDateTime.now();
        this.updatedAt        = LocalDateTime.now();
    }

    /**
     * 정적 팩토리 메서드
     * Service 레이어에서 아래와 같이 사용:
     *
     * <pre>
     * LmsConfirmation confirmation = LmsConfirmation.of(engineer, content, currentYear);
     * lmsConfirmationRepository.save(confirmation);
     * </pre>
     */
    public static LmsConfirmation of(User engineer, LmsContent content, int completionYear) {
        return LmsConfirmation.builder()
                .user(engineer)
                .content(content)
                .completionYear(completionYear)
                .build();
    }

    // ─────────────────────────────────────────────
    // JPA 콜백
    // ─────────────────────────────────────────────

    @PreUpdate
    private void preUpdate() {
        this.updatedAt = LocalDateTime.now();
    }

    public void deactivate() {
        this.isActive  = false;
        this.updatedAt = LocalDateTime.now();
    }

    /**
     * [신규] 재이수 강제(is_active=false)로 비활성화됐던 이수 이력을
     * "새로 이수 완료"한 것으로 재활성화한다.
     *
     * INSERT가 아니라 UPDATE이므로 uk_lms_confirm_year(user_id, content_id, completion_year)
     * UNIQUE 제약과 절대 충돌하지 않는다. LmsService.completeContent()에서
     * "같은 (user, content, year) 조합의 행이 이미 존재하지만 is_active=false"인
     * 케이스에서만 호출한다.
     *
     * @param newVersion 재이수 시점의 lms_contents.version (콘텐츠가 개정됐을 수 있으므로 스냅샷 갱신)
     */
    public void reactivate(String newVersion) {
        if (this.isActive) {
            throw new IllegalStateException("이미 활성 상태인 이수 이력입니다. confirmationId=" + this.confirmationId);
        }

        this.isActive        = true;
        this.confirmedVersion = newVersion;
        this.confirmedAt      = LocalDateTime.now();
        this.updatedAt         = LocalDateTime.now();
    }
}
