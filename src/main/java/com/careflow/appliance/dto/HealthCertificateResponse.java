package com.careflow.appliance.dto;

import lombok.Builder;
import lombok.Getter;
import java.time.LocalDateTime;

@Getter
@Builder
public class HealthCertificateResponse {
    private final Long certId;
    private final Long applianceId;
    private final String grade;
    private final Integer score;
    private final Boolean isCertified;
    private final LocalDateTime issuedAt;
    private final LocalDateTime updatedAt;

    // 💡 프론트엔드 레이더 차트용 4축 상세 점수 (DB 조회 후 로직으로 계산됨)
    private final Integer repairCountScore;      // 1축: 누적 수리 횟수 점수
    private final Integer usagePeriodScore;      // 2축: 사용 기간 점수
    private final Integer partImportanceScore;   // 3축: 핵심 부품 교체 점수
    private final Integer lastRepairedScore;     // 4축: 최근 수리 경과일 점수

    // 🌟 프론트용 텍스트 라벨 추가
    private final String repairCountCondition;
    private final String usagePeriodCondition;
    private final String partImportanceCondition;
    private final String lastRepairedCondition;
}