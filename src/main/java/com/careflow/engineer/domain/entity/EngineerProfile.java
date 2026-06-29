package com.careflow.engineer.domain.entity;

import com.careflow.appliance.entity.ApplianceCategory;
import com.careflow.engineer.domain.enums.SkillLevel;
import com.careflow.user.entity.User;
import jakarta.persistence.*;
import lombok.AccessLevel;
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
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_engineer_profiles_user", columnNames = "user_id")
        },
        indexes = {
                @Index(name = "idx_engineer_category", columnList = "category_id"),
                @Index(name = "idx_engineer_skill_lms", columnList = "skill_level, is_lms_completed"),
                @Index(name = "idx_engineer_avg_rating", columnList = "avg_rating")
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


    public static EngineerProfile createInitial(User user) {    // 비어있는 초기 프로필 생성
        if(user == null) {
            throw new IllegalArgumentException("프로필을 생성할 사용자 정보가 필요합니다.");
        }
        EngineerProfile profile = new EngineerProfile();
        profile.user = user;
        profile.skillLevel = SkillLevel.BEGINNER;
        profile.isLmsCompleted = false;
        profile.avgRating = BigDecimal.ZERO;
        profile.totalReviews = 0;
        return profile;
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

    public void updateBasicInfo(String introduction, String profileImageUrl) {  // 데이터 수정
        if (introduction != null) {
            this.introduction = introduction;
        }
        if (profileImageUrl != null) {
            this.profileImageUrl = profileImageUrl;
        }
    }

    // LMS 교육 이수 시 실행 메서드
    public void completeLms() {
        this.isLmsCompleted = true;
        this.updatedAt = LocalDateTime.now();
    }
}