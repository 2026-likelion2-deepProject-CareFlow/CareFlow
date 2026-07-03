package com.careflow.quiz.entity;

import com.careflow.appliance.entity.ApplianceCategory;
import com.careflow.user.entity.User;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "quiz_questions",
        indexes = {
                @Index(name = "idx_quiz_category_level", columnList = "category_id, required_level, quiz_year, is_active")
        },
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_quiz_tier_order",
                        columnNames = {"category_id", "required_level", "quiz_year", "sort_order"})
        }
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class QuizQuestion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "question_id")
    private Long questionId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "category_id", nullable = false)
    private ApplianceCategory category;

    @Enumerated(EnumType.STRING)
    @Column(name = "required_level", nullable = false, length = 20)
    private RequiredLevel requiredLevel;

    @Column(name = "quiz_year", nullable = false)
    private Integer quizYear;

    @Lob
    @Column(name = "question_text", nullable = false)
    private String questionText;

    @Column(name = "correct_answer", nullable = false)
    private boolean correctAnswer;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    @Column(name = "is_active", nullable = false)
    private boolean isActive = true;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by", nullable = false)
    private User createdBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    public enum RequiredLevel {
        BEGINNER, INTERMEDIATE, ADVANCED
    }

    @Builder
    public QuizQuestion(ApplianceCategory category, RequiredLevel requiredLevel,
                        Integer quizYear, String questionText, boolean correctAnswer,
                        int sortOrder, User createdBy) {
        this.category      = category;
        this.requiredLevel = requiredLevel;
        this.quizYear      = quizYear;
        this.questionText  = questionText;
        this.correctAnswer = correctAnswer;
        this.sortOrder     = sortOrder;
        this.isActive      = true;
        this.createdBy     = createdBy;
        this.createdAt     = LocalDateTime.now();
        this.updatedAt     = LocalDateTime.now();
    }

    public void update(String questionText, boolean correctAnswer, int sortOrder) {
        this.questionText = questionText;
        this.correctAnswer = correctAnswer;
        this.sortOrder    = sortOrder;
        this.updatedAt    = LocalDateTime.now();
    }

    public void deactivate() {
        this.isActive  = false;
        this.updatedAt = LocalDateTime.now();
    }

    public void activate() {
        this.isActive  = true;
        this.updatedAt = LocalDateTime.now();
    }
}