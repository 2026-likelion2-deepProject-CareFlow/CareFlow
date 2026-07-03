package com.careflow.agency.dto.request;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@AllArgsConstructor
public class AgencySettlementSearchRequest {

    // 정산 상태 필터 (PENDING/PAID/DISPUTED), null 이면 전체
    private String status;

    // 기사명 부분 일치 검색, null 이면 조건 없음
    private String keyword;

    // 정산 생성일 검색 시작 (yyyy-MM-dd), null 이면 조건 없음
    private String dateFrom;

    // 정산 생성일 검색 종료 (yyyy-MM-dd), null 이면 조건 없음
    private String dateTo;
}
