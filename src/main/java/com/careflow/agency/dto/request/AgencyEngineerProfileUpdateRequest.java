package com.careflow.agency.dto.request;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.AccessLevel;

import java.util.List;

/**
 * 대행사 관리자의 소속 기사 프로필 수정 요청 DTO
 * 수정 가능 필드: 전문 가전 카테고리, 전문 브랜드, 활동 지역
 * 모든 필드 선택적 — null이면 해당 필드는 변경하지 않음
 */
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AgencyEngineerProfileUpdateRequest {

    // 전문 가전 카테고리 ID (소분류 depth=2만 허용)
    private Integer categoryId;

    // 전문 브랜드 목록 (전체 교체 방식)
    private List<String> expertBrands;

    // 활동 지역 ID 목록 (구 단위 depth=2만 허용, 전체 교체 방식)
    private List<Integer> serviceRegionIds;
}
