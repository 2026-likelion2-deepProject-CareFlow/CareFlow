package com.careflow.quiz.entity;

import com.careflow.user.entity.User;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "quiz_attempts",
        indexes = {
                @Index(name = "idx_quiz_attempt_user",
                        columnList = "user_id, category_id, required_level, quiz_year"),
                @Index(name = "idx_quiz_attempt_passed", columnList = "user_id, is_passed"),
                @Index(name = "idx_quiz_year",           columnList = "quiz_year, user_id")
        }
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class QuizAttempt {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "attempt_id")
    private Long attemptId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "category_id", nullable = false)
    private Integer categoryId;

    @Enumerated(EnumType.STRING)
    @Column(name = "required_level", nullable = false, length = 20)
    private QuizQuestion.RequiredLevel requiredLevel;

    @Column(name = "quiz_year", nullable = false)
    private Integer quizYear;

    @Column(name = "score", nullable = false)
    private int score;

    @Column(name = "is_passed", nullable = false)
    private boolean isPassed;

    @Column(name = "attempted_at", nullable = false, updatable = false)
    private LocalDateTime attemptedAt;

    @Builder
    public QuizAttempt(User user, Integer categoryId,
                       QuizQuestion.RequiredLevel requiredLevel,
                       Integer quizYear, int score, boolean isPassed) {
        this.user          = user;
        this.categoryId    = categoryId;
        this.requiredLevel = requiredLevel;
        this.quizYear      = quizYear;
        this.score         = score;
        this.isPassed      = isPassed;
        this.attemptedAt   = LocalDateTime.now();
    }
}
