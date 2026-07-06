package com.careflow.engineer.service;

import com.careflow.settlement.dto.EngineerSettlementSummaryResponse;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;

/**
 * 기사 실적/정산 "리포트 다운로드" CSV 생성 컴포넌트.
 * - 화면과 동일한 EngineerSettlementSummaryResponse(동일 기간/브랜드/상태 필터 반영)를 그대로
 *   직렬화하여 CSV 수치가 화면과 항상 일치하도록 한다.
 * - UTF-8 BOM 삽입으로 Excel 한글 깨짐 방지 (AgencyStatisticsReportCsvGenerator와 동일 패턴).
 */
@Component
public class EngineerSettlementReportCsvGenerator {

    private static final byte[] BOM = {(byte) 0xEF, (byte) 0xBB, (byte) 0xBF};

    public byte[] generate(EngineerSettlementSummaryResponse data) {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        baos.writeBytes(BOM);
        try (PrintWriter w = new PrintWriter(new OutputStreamWriter(baos, StandardCharsets.UTF_8))) {

            // [실적 요약]
            w.println("[실적 요약]");
            w.println("총 완료 건수,총 매출액(원),평균 평점,고객 만족도(%),취소/거절 건수");
            w.println(String.join(",",
                    String.valueOf(data.getTotalCompletedCount()),
                    String.valueOf(data.getTotalGrossAmount()),
                    String.valueOf(data.getAvgRating()),
                    String.valueOf(data.getCustomerSatisfaction()),
                    String.valueOf(data.getRejectedCount())
            ));
            w.println();

            // [실적 상세]
            w.println("[실적 상세]");
            w.println("접수번호,작업일,고객명,제품명,브랜드,작업금액(원),상태,평점");
            if (data.getPerformanceList() != null) {
                for (EngineerSettlementSummaryResponse.PerformanceItem p : data.getPerformanceList()) {
                    w.println(String.join(",",
                            esc(p.getRequestId()),
                            esc(p.getWorkDate()),
                            esc(p.getCustomerName()),
                            esc(p.getProductName()),
                            esc(p.getBrand()),
                            String.valueOf(p.getGrossAmount()),
                            esc(p.getDiagnosisResult()),
                            String.valueOf(p.getRating())
                    ));
                }
            }
            w.println();

            // [정산 요약]
            w.println("[정산 요약]");
            w.println("총 매출액(원),플랫폼 수수료(원),대행사 수수료(원),실 정산 예정 금액(원),정산 예정일");
            EngineerSettlementSummaryResponse.SettlementSummary s = data.getSettlementSummary();
            if (s != null) {
                w.println(String.join(",",
                        String.valueOf(s.getGrossAmount()),
                        String.valueOf(s.getPlatformFee()),
                        String.valueOf(s.getAgencyFee()),
                        String.valueOf(s.getEngineerNetAmount()),
                        esc(s.getPaidAt())
                ));
            }
            w.flush();
        }
        return baos.toByteArray();
    }

    private String esc(String value) {
        if (value == null) return "";
        if (value.contains(",") || value.contains("\"") || value.contains("\n")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }
}
