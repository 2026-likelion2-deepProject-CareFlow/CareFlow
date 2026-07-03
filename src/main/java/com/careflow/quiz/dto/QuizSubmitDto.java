package com.careflow.quiz.dto;

import java.util.List;

public record QuizSubmitDto(
        List<QuizAnswerItemDto> answers
) {}
