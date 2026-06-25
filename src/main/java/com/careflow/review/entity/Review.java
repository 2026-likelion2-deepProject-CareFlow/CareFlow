package com.careflow.review.entity;

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
        name = "reviews",
        uniqueConstraints = @UniqueConstraint(name = "uk_review_request", columnNames = "request_id"),
        indexes = {
                @Index(name = "idx_review_engineer", columnList = "engineer_id, created_at"),
                @Index(name = "idx_review_customer", columnList = "customer_id")
        }
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Review {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "review_id")
    private Long id;

    // A/S 신청 (1:1)
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "request_id", nullable = false, unique = true)
    private AsRequest asRequest;

    // 리뷰 작성 고객
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "customer_id", nullable = false)
    private User customer;

    // 평가 대상 기사
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "engineer_id", nullable = false)
    private User engineer;

    // 평점 1~5
    @Column(nullable = false)
    private Integer rating;

    @Column(columnDefinition = "TEXT")
    private String content;

    // columnDefinition 삭제하지 말아주세요(H2 DB 테스트에 필요)
    @Column(name = "is_visible", nullable = false,
            columnDefinition = "TINYINT(1) DEFAULT 1")
    private Boolean isVisible = true;

    // columnDefinition 삭제하지 말아주세요(H2 DB 테스트에 필요)
    @Column(name = "created_at", nullable = false, updatable = false,
            columnDefinition = "DATETIME DEFAULT CURRENT_TIMESTAMP")
    private LocalDateTime createdAt = LocalDateTime.now();

    @Builder
    public Review(AsRequest asRequest, User customer, User engineer,
                  Integer rating, String content) {
        this.asRequest = asRequest;
        this.customer = customer;
        this.engineer = engineer;
        this.rating = rating;
        this.content = content;
        this.isVisible = true;
        this.createdAt = LocalDateTime.now();
    }

    public static Review create(AsRequest asRequest, User customer, User engineer,
                                Integer rating, String content) {
        return Review.builder()
                .asRequest(asRequest)
                .customer(customer)
                .engineer(engineer)
                .rating(rating)
                .content(content)
                .build();
    }
}
