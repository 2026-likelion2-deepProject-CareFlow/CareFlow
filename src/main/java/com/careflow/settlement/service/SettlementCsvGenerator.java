package com.careflow.settlement.service;

import com.careflow.settlement.dto.EngineerPerformanceItem;
import com.careflow.settlement.dto.MonthlySummaryResponse;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * 정산 리포트 CSV 생성 컴포넌트
 * - SettlementService에서 분리하여 독립적으로 단위 테스트 가능하게 구성
 * - UTF-8 BOM 삽입으로 Excel 한글 깨짐 방지
 */
@Component
public class SettlementCsvGenerator {

    // UTF-8 BOM 바이트 시퀀스
    private static final byte[] BOM = {(byte) 0xEF, (byte) 0xBB, (byte) 0xBF};

    /**
     * 기사별 실적 + 월별 합산 데이터를 CSV byte[] 로 변환
     *
     * @param engineers 기사별 실적 항목 목록
     * @param summary   월별 합산 내역
     * @return UTF-8 BOM 포함 CSV byte[]
     */
    public byte[] generate(List<EngineerPerformanceItem> engineers, MonthlySummaryResponse summary) {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();

        // BOM 먼저 삽입 (Excel 한글 인식을 위해 필수)
        baos.writeBytes(BOM);

        try (PrintWriter writer = new PrintWriter(
                new OutputStreamWriter(baos, StandardCharsets.UTF_8))) {

            // 섹션 1: 기사별 실적
            writer.println("[기사별 실적]");
            writer.println("기사ID,기사명,완료건수,평균평점,실수령액(원)");

            for (EngineerPerformanceItem item : engineers) {
                writer.println(String.join(",",
                        String.valueOf(item.getEngineerId()),
                        escapeCsvField(item.getEngineerName()),
                        String.valueOf(item.getCompletedCount()),
                        item.getAvgRating() != null
                                ? String.format("%.2f", item.getAvgRating())
                                : "",
                        String.valueOf(item.getTotalEarning())
                ));
            }

            writer.println();

            // 섹션 2: 월별 합산
            writer.println("[월별 합산]");
            writer.println("총건수,총매출(원),CareFlow수수료(원),대행사수수료(원),기사지급액합계(원)");
            writer.println(String.join(",",
                    String.valueOf(summary.getTotalCount()),
                    String.valueOf(summary.getTotalGrossAmount()),
                    String.valueOf(summary.getTotalPlatformFee()),
                    String.valueOf(summary.getTotalAgencyFee()),
                    String.valueOf(summary.getTotalEngineerPayout())
            ));

            writer.flush();
        }

        return baos.toByteArray();
    }

    /**
     * CSV RFC 4180 이스케이프 처리
     * - 쉼표, 큰따옴표, 줄바꿈 포함 시 큰따옴표로 감싸고 내부 큰따옴표는 "" 이중 처리
     */
    private String escapeCsvField(String value) {
        if (value == null) {
            return "";
        }
        if (value.contains(",") || value.contains("\"") || value.contains("\n")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }
}
