package com.careflow.lms.dto;

import com.careflow.appliance.entity.ApplianceCategory;
import com.careflow.lms.entity.LmsContent;

public record LmsContentCreateDto(ApplianceCategory category,
                                  String title,
                                  String body,
                                  LmsContent.RequiredLevel requiredLevel,
                                  String version) {
}
