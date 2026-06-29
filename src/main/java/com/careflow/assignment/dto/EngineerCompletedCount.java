package com.careflow.assignment.dto;

/**
 * 대행사 소속 기사의 수리 완료(COMPLETED) 건수 집계 결과 프로젝션.
 * AsAssignmentRepository 의 JPQL 집계 쿼리 반환값 매핑용.
 */
public interface EngineerCompletedCount {
    Long getEngineerUserId();
    String getEngineerName();
    Long getCompletedCount();
}
