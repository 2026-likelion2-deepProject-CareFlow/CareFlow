package com.careflow.engineer.dto;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UpdateProfileRequest {
    private Integer careerStartedYear;
    private String introduction;
    private String profileImageUrl;
    private List<String> expertBrands;
    private List<Integer> serviceRegionIds;
}