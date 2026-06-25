package com.careflow.settlement.dto;

/**
 * SettlementRepository JPQL 집계 쿼리 결과를 받는 인터페이스 프로젝션
 * - 기사별 완료 건수 / 실수령액 합산
 */
public interface EngineerSettlementSummary {
    Long getEngineerId();
    String getEngineerName();
    Long getCompletedCount();
    Long getTotalEarning();
}
