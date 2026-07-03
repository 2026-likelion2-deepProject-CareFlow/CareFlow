package com.careflow.quiz.dto;

import com.careflow.quiz.entity.QuizQuestion.RequiredLevel;

public record QuizQuestionCreateDto(
        Integer categoryId,
        RequiredLevel requiredLevel,
        Integer quizYear,
        String questionText,
        boolean correctAnswer,
        int sortOrder
) {}
