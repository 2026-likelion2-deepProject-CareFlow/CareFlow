package com.careflow.quiz.dto;

public record QuizQuestionUpdateDto(
        String questionText,
        boolean correctAnswer,
        int sortOrder
) {}