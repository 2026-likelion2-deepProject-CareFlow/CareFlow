package com.careflow.engineer.domain.entity;

import com.careflow.engineer.domain.enums.SkillLevel;
import com.careflow.user.entity.User;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(
        name = "engineer_profiles",
        indexes = {
                @Index(name = "idx_profile_skill", columnList = "skill_level, is_lms_completed"),
                @Index(name = "idx_profile_rating", columnList = "avg_rating")
        }
)
@EntityListeners(AuditingEntityListener.class)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class EngineerProfile {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "profile_id")
    private Long profileId;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", unique = true, nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "category_id", nullable = true)
    private ApplianceCategory category;

    @Column(name = "career_started_year", columnDefinition = "YEAR")
    private Integer careerStartedYear;

    @Enumerated(EnumType.STRING)
    @Column(name = "skill_level", nullable = false, length = 20)
    private SkillLevel skillLevel;

    @Column(name = "is_lms_completed", nullable = false, columnDefinition = "TINYINT DEFAULT 0")
    private boolean isLmsCompleted;

    @Column(name = "introduction", columnDefinition = "TEXT")
    private String introduction;

    @Column(name = "profile_image_url", length = 500)
    private String profileImageUrl;

    @Column(name = "avg_rating", precision = 3, scale = 2, columnDefinition = "DECIMAL(3,2) DEFAULT 0.00")
    private BigDecimal avgRating;

    @Column(name = "total_reviews", columnDefinition = "INT DEFAULT 0")
    private int totalReviews;

    @CreatedDate
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Builder
    public EngineerProfile(User user, ApplianceCategory category, int careerStartedYear, SkillLevel skillLevel, String introduction, String profileImageUrl) {
        this.user = user;
        this.category = category;
        this.careerStartedYear = careerStartedYear;
        this.skillLevel = skillLevel;
        this.isLmsCompleted = false;
        this.introduction = introduction;
        this.profileImageUrl = profileImageUrl;
        this.avgRating = BigDecimal.ZERO;
        this.totalReviews = 0;
    }

    public void completeProfile(ApplianceCategory category, Integer careerStartedYear, SkillLevel skillLevel, String introduction) {    // 프로필 완성(업데이트)
        if(category == null || careerStartedYear == null || skillLevel == null){
            throw new IllegalArgumentException("카테고리와 경력 시작 연도는 필수 입력값입니다.");
        }

        this.category = category;
        this.careerStartedYear = careerStartedYear;
        this.skillLevel = skillLevel;
        this.introduction = introduction;
    }

    public boolean isCompleted() {
        return this.category != null && this.careerStartedYear != null;
    }
}