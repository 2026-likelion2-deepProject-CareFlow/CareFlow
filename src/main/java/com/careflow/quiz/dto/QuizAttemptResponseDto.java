package com.careflow.quiz.dto;

import com.careflow.quiz.entity.QuizAttempt;
import java.time.LocalDateTime;

public record QuizAttemptResponseDto(
        Long attemptId,
        Integer quizYear,
        boolean isPassed,
        LocalDateTime attemptedAt
) {
    public static QuizAttemptResponseDto from(QuizAttempt a) {
        return new QuizAttemptResponseDto(
                a.getAttemptId(), a.getQuizYear(), a.isPassed(), a.getAttemptedAt()
        );
    }
}