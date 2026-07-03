package com.careflow.quiz.dto;

import com.careflow.quiz.entity.QuizQuestion;

public record QuizQuestionAdminResponseDto(
        Long questionId,
        Integer categoryId,
        String categoryName,
        QuizQuestion.RequiredLevel requiredLevel,
        Integer quizYear,
        String questionText,
        boolean correctAnswer,   // 관리자는 정답 확인 가능
        int sortOrder,
        boolean isActive
) {
    public static QuizQuestionAdminResponseDto from(QuizQuestion q) {
        return new QuizQuestionAdminResponseDto(
                q.getQuestionId(),
                q.getCategory().getCategoryId(),
                q.getCategory().getName(),
                q.getRequiredLevel(),
                q.getQuizYear(),
                q.getQuestionText(),
                q.isCorrectAnswer(),
                q.getSortOrder(),
                q.isActive()
        );
    }
}
