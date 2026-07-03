package com.careflow.quiz.dto;

import java.util.List;

public record QuizResultDto(
        boolean isPassed,
        int attemptCount,
        boolean forceReLearning,
        List<QuizAnswerResultDto> answerDetails  // 합격 시에만 값 존재, 불합격 시 null
) {
    public static QuizResultDto passed(int attemptCount, List<QuizAnswerResultDto> answerDetails) {
        return new QuizResultDto(true, attemptCount, false, answerDetails);
    }
    public static QuizResultDto failed(int attemptCount) {
        // 불합격 시 answerDetails = null (점수·정오답 비공개)
        return new QuizResultDto(false, attemptCount, false, null);
    }
    public static QuizResultDto failedWithReLearning(int attemptCount) {
        return new QuizResultDto(false, attemptCount, true, null);
    }
}
