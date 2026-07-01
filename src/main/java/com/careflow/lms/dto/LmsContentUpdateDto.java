package com.careflow.lms.dto;

import com.careflow.lms.entity.LmsContent;

public record LmsContentUpdateDto(
        String title,
        String body,
        String videoUrl,
        LmsContent.RequiredLevel requiredLevel,
        String version
) {}