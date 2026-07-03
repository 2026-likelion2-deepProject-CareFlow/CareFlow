package com.careflow.quiz.dto;

import com.careflow.quiz.entity.QuizQuestion;

public record QuizQuestionResponseDto(
        Long questionId,
        String questionText,
        int sortOrder
) {
    public static QuizQuestionResponseDto from(QuizQuestion q) {
        return new QuizQuestionResponseDto(q.getQuestionId(), q.getQuestionText(), q.getSortOrder());
    }
}
