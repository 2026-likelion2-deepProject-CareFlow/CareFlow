package com.careflow.settlement.service;

import com.careflow.settlement.dto.EngineerPerformanceItem;
import com.careflow.settlement.dto.MonthlySummaryResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("SettlementCsvGenerator 단위 테스트")
class SettlementCsvGeneratorTest {

    private SettlementCsvGenerator generator;

    @BeforeEach
    void setUp() {
        generator = new SettlementCsvGenerator();
    }

    private MonthlySummaryResponse stubSummary(long count, long gross, long platform, long agency, long engineer) {
        return MonthlySummaryResponse.builder()
                .year(2026).month(6)
                .totalCount(count)
                .totalGrossAmount(gross)
                .totalPlatformFee(platform)
                .totalAgencyFee(agency)
                .totalEngineerPayout(engineer)
                .build();
    }

    @Test
    @DisplayName("UTF-8 BOM 삽입 확인: byte[] 첫 3바이트가 BOM")
    void bomIsInsertedAtStart() {
        byte[] csv = generator.generate(List.of(), stubSummary(0, 0, 0, 0, 0));

        assertThat(csv[0]).isEqualTo((byte) 0xEF);
        assertThat(csv[1]).isEqualTo((byte) 0xBB);
        assertThat(csv[2]).isEqualTo((byte) 0xBF);
    }

    @Test
    @DisplayName("헤더 행 포함 확인: 기사별 실적 헤더가 CSV에 존재")
    void containsEngineerSectionHeader() {
        byte[] csv = generator.generate(List.of(), stubSummary(0, 0, 0, 0, 0));
        String content = new String(csv, StandardCharsets.UTF_8);

        assertThat(content).contains("[기사별 실적]");
        assertThat(content).contains("기사ID,기사명,완료건수,평균평점,실수령액(원)");
    }

    @Test
    @DisplayName("데이터 행 값 검증: 기사 정보가 CSV에 올바르게 직렬화")
    void engineerDataRowIsCorrect() {
        EngineerPerformanceItem item = EngineerPerformanceItem.builder()
                .engineerId(101L)
                .engineerName("홍길동")
                .completedCount(12)
                .avgRating(4.75)
                .totalEarning(960000)
                .build();

        byte[] csv = generator.generate(List.of(item), stubSummary(1, 1000000, 100000, 90000, 810000));
        String content = new String(csv, StandardCharsets.UTF_8);

        assertThat(content).contains("101,홍길동,12,4.75,960000");
    }

    @Test
    @DisplayName("avgRating null 처리: 리뷰 없는 기사는 평점 칸이 빈 문자열")
    void avgRatingNullIsEmptyInCsv() {
        EngineerPerformanceItem item = EngineerPerformanceItem.builder()
                .engineerId(102L)
                .engineerName("김수리")
                .completedCount(3)
                .avgRating(null)
                .totalEarning(240000)
                .build();

        byte[] csv = generator.generate(List.of(item), stubSummary(1, 240000, 24000, 21600, 194400));
        String content = new String(csv, StandardCharsets.UTF_8);

        // avgRating 위치(4번째 필드)가 빈 값 — "102,김수리,3,,240000" 형태
        assertThat(content).contains("102,김수리,3,,240000");
    }

    @Test
    @DisplayName("빈 데이터: 기사 목록 없어도 헤더와 합산 섹션 포함")
    void emptyEngineers_stillContainsHeaders() {
        byte[] csv = generator.generate(List.of(), stubSummary(0, 0, 0, 0, 0));
        String content = new String(csv, StandardCharsets.UTF_8);

        assertThat(content).contains("[기사별 실적]");
        assertThat(content).contains("[월별 합산]");
        assertThat(content).contains("총건수,총매출(원),CareFlow수수료(원),대행사수수료(원),기사지급액합계(원)");
    }

    @Test
    @DisplayName("특수문자 이름 CSV 이스케이프: 쉼표 포함 이름은 큰따옴표로 감싸짐")
    void nameWithComma_isEscaped() {
        EngineerPerformanceItem item = EngineerPerformanceItem.builder()
                .engineerId(103L)
                .engineerName("홍,길동")  // 쉼표 포함
                .completedCount(5)
                .avgRating(4.0)
                .totalEarning(400000)
                .build();

        byte[] csv = generator.generate(List.of(item), stubSummary(1, 400000, 40000, 36000, 324000));
        String content = new String(csv, StandardCharsets.UTF_8);

        // 쉼표 포함 이름은 큰따옴표로 감싸야 함
        assertThat(content).contains("\"홍,길동\"");
    }

    @Test
    @DisplayName("특수문자 이름 CSV 이스케이프: 큰따옴표 포함 이름은 이중 큰따옴표 처리")
    void nameWithDoubleQuote_isEscaped() {
        EngineerPerformanceItem item = EngineerPerformanceItem.builder()
                .engineerId(104L)
                .engineerName("홍\"길동")  // 큰따옴표 포함
                .completedCount(2)
                .avgRating(3.5)
                .totalEarning(160000)
                .build();

        byte[] csv = generator.generate(List.of(item), stubSummary(1, 160000, 16000, 14400, 129600));
        String content = new String(csv, StandardCharsets.UTF_8);

        // 큰따옴표는 "" 로 이중 처리 후 전체를 큰따옴표로 감싸야 함
        assertThat(content).contains("\"홍\"\"길동\"");
    }

    @Test
    @DisplayName("합산 섹션 값 검증: 모든 합산 필드가 올바르게 직렬화")
    void summaryRowIsCorrect() {
        byte[] csv = generator.generate(
                List.of(),
                stubSummary(20L, 4000000L, 400000L, 360000L, 3240000L));
        String content = new String(csv, StandardCharsets.UTF_8);

        assertThat(content).contains("20,4000000,400000,360000,3240000");
    }
}
