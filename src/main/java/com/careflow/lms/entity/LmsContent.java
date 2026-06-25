package com.careflow.lms.entity;

import com.careflow.appliance.entity.ApplianceCategory;
import com.careflow.user.entity.User;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;


@Entity
@Table(
    name = "lms_contents",
    indexes = {
        // WHERE category_id = ? AND required_level IN (?, 'ALL') AND is_active = 1
        @Index(name = "idx_lms_category_level", columnList = "category_id, required_level, is_active"),
        @Index(name = "idx_lms_active",          columnList = "is_active")
    }
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class LmsContent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "content_id", nullable = false, updatable = false)
    private Long contentId;


    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "category_id")
    private ApplianceCategory category;

    @Column(name = "title", nullable = false, length = 200)
    private String title;


    @Lob
    @Column(name = "body", nullable = false, columnDefinition = "TEXT")
    private String body;

    /**
     * 필수 이수 대상 등급
     * - BEGINNER / INTERMEDIATE / ADVANCED: 해당 등급 기사만 이수 대상
     * - ALL: 해당 카테고리 전 등급 공통 이수 (예: 고객 응대 가이드)
     *
     * 기사 이수 대상 쿼리 조건:
     * WHERE category_id = :categoryId
     *   AND required_level IN (:skillLevel, 'ALL')
     *   AND is_active = true
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "required_level", nullable = false, length = 20)
    private RequiredLevel requiredLevel;


    @Enumerated(EnumType.STRING)
    @Column(name = "content_type", nullable = false, length = 10)
    private ContentType contentType;


    @Column(name = "version", nullable = false, length = 10)
    private String version;


    @Column(name = "is_active", nullable = false)
    private boolean isActive;


    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by", nullable = false, updatable = false)
    private User createdBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    // ─────────────────────────────────────────────
    // ENUM 정의
    // ─────────────────────────────────────────────

    public enum RequiredLevel {
        BEGINNER,
        INTERMEDIATE,
        ADVANCED,
        ALL
    }

    public enum ContentType {
        TEXT,
        VIDEO
    }

    // ─────────────────────────────────────────────
    // 빌더
    // ─────────────────────────────────────────────

    @Builder
    private LmsContent(
            ApplianceCategory category,
            String title,
            String body,
            RequiredLevel requiredLevel,
            ContentType contentType,
            String version,
            boolean isActive,
            User createdBy
    ) {
        this.category      = category;
        this.title         = title;
        this.body          = body;
        this.requiredLevel = requiredLevel;
        this.contentType   = contentType;
        this.version       = version;
        this.isActive      = isActive;
        this.createdBy     = createdBy;
        this.createdAt     = LocalDateTime.now();
        this.updatedAt     = LocalDateTime.now();
    }

    // ─────────────────────────────────────────────
    // 도메인 메서드
    // ─────────────────────────────────────────────

    /** 관리자가 콘텐츠를 수정할 때 사용 */
    public void update(String title, String body, RequiredLevel requiredLevel, String version) {
        this.title         = title;
        this.body          = body;
        this.requiredLevel = requiredLevel;
        this.version       = version;
        this.updatedAt     = LocalDateTime.now();
    }

    /** 콘텐츠 비활성화 (기사 목록 및 이수 대상에서 제외) */
    public void deactivate() {
        this.isActive  = false;
        this.updatedAt = LocalDateTime.now();
    }

    /** 콘텐츠 활성화 */
    public void activate() {
        this.isActive  = true;
        this.updatedAt = LocalDateTime.now();
    }

    // ─────────────────────────────────────────────
    // JPA 콜백
    // ─────────────────────────────────────────────

    @PrePersist
    private void prePersist() {
        if (this.contentType == null) {
            this.contentType = ContentType.TEXT;
        }
        if (this.version == null) {
            this.version = "1.0";
        }
        this.isActive   = true;
        this.createdAt  = LocalDateTime.now();
        this.updatedAt  = LocalDateTime.now();
    }

    @PreUpdate
    private void preUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
}
