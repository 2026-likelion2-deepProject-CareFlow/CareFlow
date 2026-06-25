package com.careflow.lms.dto;

import com.careflow.lms.entity.LmsContent;

import java.time.LocalDateTime;

public record LmsContentWithStatusDto(LmsContent content, boolean completed, LocalDateTime confirmedAt) {
}
