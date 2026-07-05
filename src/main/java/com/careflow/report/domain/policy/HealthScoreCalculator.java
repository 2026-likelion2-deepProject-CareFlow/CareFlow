package com.careflow.report.domain.policy;

import com.careflow.common.enums.PartImportance;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

/**
 * 건강 진단서 4축 점수 계산 정책 (공통 사용)
 * - 엔티티(저장)와 서비스(조회/역추산) 양쪽에서 이 클래스만 바라보도록 단일화(DRY 원칙 준수)
 */
public class HealthScoreCalculator {

    // 🌟 수정: 0회=25, 1회=20, 2회=15, 3회=8, 4회이상=0 으로 정확히 매핑
    public static int calculateRepairCountScore(int count) {
        if (count == 0) return 25;
        if (count == 1) return 20;
        if (count == 2) return 15;
        if (count == 3) return 8;
        return 0;
    }

    public static int calculateUsagePeriodScore(LocalDate purchaseDate, LocalDateTime asOf) {
        if (purchaseDate == null) return 25;
        long years = ChronoUnit.YEARS.between(purchaseDate, asOf.toLocalDate());
        if (years < 1) return 25;
        if (years < 3) return 20;
        if (years < 5) return 15;
        if (years < 8) return 8;
        return 0;
    }

    public static int calculatePartImportanceScore(PartImportance importance) {
        if (importance == null) return 25;
        return switch (importance) {
            case MINOR -> 20;
            case NORMAL -> 15;
            case MAJOR -> 8;
            case CRITICAL -> 0;
        };
    }

    public static int calculateLastRepairedScore(LocalDateTime lastRepaired, LocalDateTime asOf) {
        if (lastRepaired == null) return 25;
        long months = ChronoUnit.MONTHS.between(lastRepaired, asOf);
        if (months >= 24) return 20;
        if (months >= 12) return 15;
        if (months >= 6) return 8;
        return 0;
    }
}