package com.careflow.lms.dto;

import com.careflow.appliance.entity.ApplianceCategory;
import com.careflow.lms.entity.LmsContent;

public record LmsContentCreateDto(
        Integer categoryId,
        String title,
        String body,
        String videoUrl,
        LmsContent.RequiredLevel requiredLevel,
        LmsContent.ContentType contentType,
        String version
) {}
