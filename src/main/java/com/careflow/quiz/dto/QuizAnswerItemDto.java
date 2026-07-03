package com.careflow.quiz.dto;

public record QuizAnswerItemDto(
        Long questionId,
        boolean submittedAnswer
) {}