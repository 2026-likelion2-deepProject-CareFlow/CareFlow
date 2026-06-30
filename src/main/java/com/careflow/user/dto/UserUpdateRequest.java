package com.careflow.user.dto;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * 사용자 본인 정보 수정 요청 DTO
 * PATCH /api/users/me 요청에 사용
 * null 필드는 변경하지 않음 (PATCH 의미론)
 */
public record UserUpdateRequest(

        @Size(max = 50, message = "이름은 50자 이하여야 합니다.")
        String name,

        @Pattern(regexp = "^\\d{10,11}$", message = "전화번호는 숫자 10~11자리여야 합니다.")
        String phone,

        Integer regionId,

        @Size(max = 100, message = "상세 주소는 100자 이하여야 합니다.")
        String addressDetail
) {}
