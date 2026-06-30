package com.careflow.lms.dto;

import com.careflow.lms.entity.LmsConfirmation;

public record LmsConfirmationResponseDto(
        Long confirmationId,
        Long userId,
        LmsContentResponseDto content,
        Integer completionYear,
        String confirmedVersion,
        java.time.LocalDateTime confirmedAt
) {
    public static LmsConfirmationResponseDto from(LmsConfirmation confirmation) {
        return new LmsConfirmationResponseDto(
                confirmation.getConfirmationId(),
                confirmation.getUser().getId(),
                LmsContentResponseDto.from(confirmation.getContent()),
                confirmation.getCompletionYear(),
                confirmation.getConfirmedVersion(),
                confirmation.getConfirmedAt()
        );
    }
}