package com.careflow.engineer.domain;

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
@Table(name = "engineerprofiles")
@EntityListeners(AuditingEntityListener.class)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class EngineerProfile {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "profileid")
    private Long profileId;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "userid", unique = true, nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "categoryid", nullable = false)
    private ApplianceCategory category;

    @Column(name = "careerstartedyear", nullable = false, columnDefinition = "YEAR")
    private int careerStartedYear;

    @Enumerated(EnumType.STRING)
    @Column(name = "skilllevel", nullable = false, length = 20)
    private SkillLevel skillLevel;

    @Column(name = "islmscompleted", nullable = false, columnDefinition = "TINYINT(1) DEFAULT 0")
    private boolean isLmsCompleted;

    @Column(name = "introduction", columnDefinition = "TEXT")
    private String introduction;

    @Column(name = "profileimageurl", length = 500)
    private String profileImageUrl;

    @Column(name = "avgrating", precision = 3, scale = 2, columnDefinition = "DECIMAL(3,2) DEFAULT 0.00")
    private BigDecimal avgRating;

    @Column(name = "totalreviews", columnDefinition = "INT DEFAULT 0")
    private int totalReviews;

    @CreatedDate
    @Column(name = "createdat", updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column(name = "updatedat")
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
}