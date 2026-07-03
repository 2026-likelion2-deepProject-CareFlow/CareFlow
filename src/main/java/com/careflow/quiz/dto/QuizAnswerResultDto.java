package com.careflow.quiz.dto;

public record QuizAnswerResultDto(
        Long questionId,
        int sortOrder,
        String questionText,
        boolean submittedAnswer,   // 기사가 제출한 답
        boolean correctAnswer,     // 정답
        boolean isCorrect          // 정오답 여부
) {}
