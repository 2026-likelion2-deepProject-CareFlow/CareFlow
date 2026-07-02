package com.careflow.report.policy;

import com.careflow.report.domain.enums.PartImportance;
import com.careflow.report.domain.policy.HealthScoreCalculator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("HealthScoreCalculator 단위 테스트")
class HealthScoreCalculatorTest {

    @Test
    @DisplayName("성공: 수리 횟수에 따른 점수가 가이드라인에 맞게 정확히 산정된다.")
    void calculateRepairCountScore() {
        assertThat(HealthScoreCalculator.calculateRepairCountScore(0)).isEqualTo(25);
        assertThat(HealthScoreCalculator.calculateRepairCountScore(1)).isEqualTo(20);
        assertThat(HealthScoreCalculator.calculateRepairCountScore(2)).isEqualTo(15);
        assertThat(HealthScoreCalculator.calculateRepairCountScore(3)).isEqualTo(8);
        assertThat(HealthScoreCalculator.calculateRepairCountScore(4)).isEqualTo(0);
        assertThat(HealthScoreCalculator.calculateRepairCountScore(10)).isEqualTo(0);
    }

    @Test
    @DisplayName("성공: 부품 중요도에 따른 점수가 정확히 산정된다.")
    void calculatePartImportanceScore() {
        assertThat(HealthScoreCalculator.calculatePartImportanceScore(PartImportance.MINOR)).isEqualTo(20);
        assertThat(HealthScoreCalculator.calculatePartImportanceScore(PartImportance.NORMAL)).isEqualTo(15);
        assertThat(HealthScoreCalculator.calculatePartImportanceScore(PartImportance.MAJOR)).isEqualTo(8);
        assertThat(HealthScoreCalculator.calculatePartImportanceScore(PartImportance.CRITICAL)).isEqualTo(0);
        assertThat(HealthScoreCalculator.calculatePartImportanceScore(null)).isEqualTo(25); // 부품 교체 없을 때
    }
}