package com.careflow.agency.dto.request;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.AccessLevel;

import java.util.List;

/**
 * 대행사 관리자의 소속 기사 프로필 수정 요청 DTO
 * 수정 가능 필드: 전문 가전 카테고리, 전문 브랜드, 활동 지역, 연락처, 이메일, 경력 시작 연도, 소개
 * 모든 필드 선택적 — null이면 해당 필드는 변경하지 않음
 * 기술 등급(skillLevel)은 프론트에서 값을 받지 않음 — 경력 시작 연도 기준으로 서버가 자동 산정(기사 본인 수정 플로우와 동일 규칙)
 */
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AgencyEngineerProfileUpdateRequest {

    // 전문 가전 카테고리 ID (소분류 depth=2만 허용)
    private Integer categoryId;

    // 전문 브랜드 목록 (전체 교체 방식, 빈 배열 전달 시 전체 삭제)
    private List<String> expertBrands;

    // 활동 지역 ID 목록 (구 단위 depth=2만 허용, 전체 교체 방식, 빈 배열 전달 시 전체 삭제)
    private List<Integer> serviceRegionIds;

    // 연락처
    private String phone;

    // 이메일 (로그인 식별자 — 중복 시 변경 거부)
    private String email;

    // 경력 시작 연도 — 변경 시 기술 등급도 서버에서 함께 재산정됨
    private Integer careerStartedYear;

    // 자기소개
    private String introduction;
}
