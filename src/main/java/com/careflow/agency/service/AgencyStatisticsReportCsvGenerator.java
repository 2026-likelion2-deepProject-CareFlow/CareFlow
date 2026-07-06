package com.careflow.agency.service;

import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * 대행사 통계 페이지 "리포트 다운로드" 4종 CSV 생성 컴포넌트
 * - UTF-8 BOM 삽입으로 Excel 한글 깨짐 방지 (SettlementCsvGenerator와 동일 패턴)
 */
@Component
public class AgencyStatisticsReportCsvGenerator {

    private static final byte[] BOM = {(byte) 0xEF, (byte) 0xBB, (byte) 0xBF};

    /** 작업 현황 리포트: 일자별 접수/완료 건수 */
    public byte[] generateWorkStatus(List<Object[]> dailyRows) {
        return write(writer -> {
            writer.println("[일자별 작업 현황]");
            writer.println("날짜,접수건수,완료건수");
            for (Object[] row : dailyRows) {
                writer.println(String.join(",",
                        row[0].toString().substring(0, 10),
                        String.valueOf(((Number) row[1]).longValue()),
                        String.valueOf(((Number) row[2]).longValue())
                ));
            }
        });
    }

    /** 정산 리포트: 일자별 정산 건수/금액 */
    public byte[] generateSettlement(List<Object[]> dailyRows) {
        return write(writer -> {
            writer.println("[일자별 정산 내역]");
            writer.println("날짜,정산건수,총매출액(원),플랫폼수수료(원),대행사수수료(원),기사지급액(원)");
            for (Object[] row : dailyRows) {
                writer.println(String.join(",",
                        row[0].toString().substring(0, 10),
                        String.valueOf(((Number) row[1]).longValue()),
                        String.valueOf(((Number) row[2]).longValue()),
                        String.valueOf(((Number) row[3]).longValue()),
                        String.valueOf(((Number) row[4]).longValue()),
                        String.valueOf(((Number) row[5]).longValue())
                ));
            }
        });
    }

    /** 기사 성과 리포트: 기사별 완료건수 + 평균 평점 */
    public byte[] generateEngineerPerformance(List<Object[]> completedRows, java.util.Map<Long, Double> ratingByEngineerId) {
        return write(writer -> {
            writer.println("[기사별 성과]");
            writer.println("기사ID,기사명,완료건수,평균평점");
            for (Object[] row : completedRows) {
                long engineerId = ((Number) row[0]).longValue();
                Double rating = ratingByEngineerId.get(engineerId);
                writer.println(String.join(",",
                        String.valueOf(engineerId),
                        escapeCsvField((String) row[1]),
                        String.valueOf(((Number) row[2]).longValue()),
                        rating != null ? String.format("%.2f", rating) : ""
                ));
            }
        });
    }

    /** 고객 현황 리포트: 고객별 접수건수 + 평균 만족도(작성 리뷰 평점) */
    public byte[] generateCustomerStatus(List<Object[]> activityRows, java.util.Map<Long, Double> ratingByCustomerId) {
        return write(writer -> {
            writer.println("[고객별 접수 현황]");
            writer.println("고객ID,고객명,접수건수,평균만족도(작성리뷰기준)");
            for (Object[] row : activityRows) {
                long customerId = ((Number) row[0]).longValue();
                Double rating = ratingByCustomerId.get(customerId);
                writer.println(String.join(",",
                        String.valueOf(customerId),
                        escapeCsvField((String) row[1]),
                        String.valueOf(((Number) row[2]).longValue()),
                        rating != null ? String.format("%.2f", rating) : ""
                ));
            }
        });
    }

    private byte[] write(java.util.function.Consumer<PrintWriter> body) {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        baos.writeBytes(BOM);
        try (PrintWriter writer = new PrintWriter(new OutputStreamWriter(baos, StandardCharsets.UTF_8))) {
            body.accept(writer);
            writer.flush();
        }
        return baos.toByteArray();
    }

    private String escapeCsvField(String value) {
        if (value == null) return "";
        if (value.contains(",") || value.contains("\"") || value.contains("\n")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }
}
