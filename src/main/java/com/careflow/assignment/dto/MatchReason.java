package com.careflow.assignment.dto;

/**
 * 자동 배차/자동 재배차 시 매칭 성사 사유 문자열 상수.
 * AsRequestService(일반 배차)와 AssignmentReassignService(재배차) 양쪽에서 동일하게 사용.
 */
public final class MatchReason {

    private MatchReason() {}

    /** Fallback 0: 스케줄 + 브랜드 + 카테고리 + 서비스 지역 4가지 조건 모두 충족 */
    public static final String FALLBACK_0 =
            "스케줄, 브랜드, 카테고리, 서비스 지역 4가지 조건 모두 충족";

    /** Fallback 1: 브랜드 조건 완화 — 스케줄 + 카테고리 + 서비스 지역 조건 충족 */
    public static final String FALLBACK_1 =
            "스케줄, 카테고리, 서비스 지역 조건 충족 (브랜드 조건 완화 적용)";

    /** Fallback 2: 브랜드 + 서비스 지역 조건 완화 — 스케줄 + 카테고리 조건 충족 */
    public static final String FALLBACK_2 =
            "스케줄, 카테고리 조건 충족 (브랜드 및 서비스 지역 조건 완화 적용)";

    /** Fallback 3: 브랜드 + 서비스 지역 + 카테고리 조건 완화 — 스케줄 조건만 충족 */
    public static final String FALLBACK_3 =
            "스케줄 조건만 충족 (브랜드, 서비스 지역, 카테고리 조건 모두 완화 적용)";
}
