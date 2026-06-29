package com.careflow.settlement.controller;

import com.careflow.auth.security.CustomUserDetails;
import com.careflow.settlement.dto.EngineerPerformanceResponse;
import com.careflow.settlement.dto.MonthlySummaryResponse;
import com.careflow.settlement.service.SettlementService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/agency/settlements")
@RequiredArgsConstructor
@PreAuthorize("hasRole('AGENCY')")
public class SettlementController {

    private final SettlementService settlementService;

    /**
     * [기사별 실적 리포트] GET /api/agency/settlements/engineers/performance
     *
     * - AGENCY 역할만 접근 가능 (본인 소속 대행사 기준 자동 필터)
     * - settlements.paid_at 기준 월 집계: 완료 건수 / 평균 평점 / 기사 실수령액
     *
     * @param year  조회 연도
     * @param month 조회 월 (1~12)
     */
    @GetMapping("/engineers/performance")
    public ResponseEntity<EngineerPerformanceResponse> getEngineerPerformance(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @RequestParam int year,
            @RequestParam int month) {

        EngineerPerformanceResponse response =
                settlementService.getEngineerPerformance(userDetails.getUserId(), year, month);

        return ResponseEntity.ok(response);
    }

    /**
     * [대행사 정산 합산 내역] GET /api/agency/settlements/summary
     *
     * - AGENCY 역할만 접근 가능
     * - settlements.paid_at 기준 월 합산: 총매출 / CareFlow수수료 / 대행사수수료 / 기사지급액
     * - 데이터 없는 월도 0 값으로 정상 반환 (404 아님)
     *
     * @param year  조회 연도
     * @param month 조회 월 (1~12)
     */
    @GetMapping("/summary")
    public ResponseEntity<MonthlySummaryResponse> getMonthlySummary(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @RequestParam int year,
            @RequestParam int month) {

        MonthlySummaryResponse response =
                settlementService.getMonthlySummary(userDetails.getUserId(), year, month);

        return ResponseEntity.ok(response);
    }

    /**
     * [월별 정산 리포트 다운로드] GET /api/agency/settlements/monthly-report/download
     *
     * - AGENCY 역할만 접근 가능
     * - 기사별 실적 + 합산 내역을 CSV 파일로 반환
     * - UTF-8 BOM 포함 (Excel 한글 깨짐 방지)
     * - 파일명 형식: settlement_report_{year}_{month:2자리}.csv
     *
     * @param year  조회 연도
     * @param month 조회 월 (1~12)
     */
    @GetMapping("/monthly-report/download")
    public ResponseEntity<byte[]> downloadMonthlyReport(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @RequestParam int year,
            @RequestParam int month) {

        byte[] csvBytes = settlementService.generateMonthlyCsv(userDetails.getUserId(), year, month);

        // 파일명 month는 항상 2자리 패딩 (예: 6월 → 06)
        String filename = String.format("settlement_report_%d_%02d.csv", year, month);

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"" + filename + "\"")
                .contentType(MediaType.parseMediaType("text/csv; charset=UTF-8"))
                .body(csvBytes);
    }
}
