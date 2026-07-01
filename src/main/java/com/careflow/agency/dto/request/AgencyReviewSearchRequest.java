package com.careflow.agency.dto.request;

import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class AgencyReviewSearchRequest {

    // 평점 필터 (1~5), null 이면 전체
    private Integer rating;

    // 기사 user_id 필터, null 이면 전체
    private Long engineerId;

    // 노출 상태 필터, null 이면 전체
    private Boolean isVisible;

    // 작성일 검색 시작 (yyyy-MM-dd), null 이면 조건 없음
    private String dateFrom;

    // 작성일 검색 종료 (yyyy-MM-dd), null 이면 조건 없음
    private String dateTo;

    // 고객명 / 기사명 / 주문번호 부분 일치 검색, null 이면 조건 없음
    private String keyword;
}
