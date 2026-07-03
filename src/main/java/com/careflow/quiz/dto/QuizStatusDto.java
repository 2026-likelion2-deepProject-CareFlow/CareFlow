package com.careflow.quiz.dto;

public record QuizStatusDto(
        boolean isContentCompleted,
        boolean isQuestionRegistered,
        boolean isPassed,
        int attemptCount,
        int maxAttempts,
        boolean isForceReLearning
) {}
