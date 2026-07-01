package com.careflow.lms.dto;

import com.careflow.lms.entity.LmsContent;


public record LmsContentResponseDto(
        Long contentId,
        Integer categoryId,
        String categoryName,
        String title,
        String body,
        String videoUrl,
        LmsContent.RequiredLevel requiredLevel,
        LmsContent.ContentType contentType,
        String version,
        boolean isActive
) {
    public static LmsContentResponseDto from(LmsContent content) {
        return new LmsContentResponseDto(
                content.getContentId(),
                content.getCategory().getCategoryId(),
                content.getCategory().getName(),
                content.getTitle(),
                content.getBody(),
                content.getVideoUrl(),
                content.getRequiredLevel(),
                content.getContentType(),
                content.getVersion(),
                content.isActive()
        );
    }
}
