package com.careflow.agency.controller;

import com.careflow.agency.dto.request.AgencyStatisticsDateRangeRequest;
import com.careflow.agency.dto.response.*;
import com.careflow.agency.service.AgencyStatisticsService;
import com.careflow.auth.security.CustomUserDetails;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 대행사 통계 API 컨트롤러
 * 모든 엔드포인트는 ROLE_AGENCY 로그인 상태 전제
 * agencyId는 JWT에서 추출한 CustomUserDetails.getAgencyId()를 사용 — 경로/파라미터로 받지 않음
 */
@RestController
@RequestMapping("/api/agency/statistics")
@RequiredArgsConstructor
@PreAuthorize("hasRole('AGENCY')")
public class AgencyStatisticsController {

    private final AgencyStatisticsService statisticsService;

    // ─────────────────────────────────────────────
    // GET /api/agency/statistics/summary
    // 핵심 지표 6가지 요약 + 전 기간 대비 증감률
    // ─────────────────────────────────────────────
    @GetMapping("/summary")
    public ResponseEntity<AgencyStatisticsSummaryResponse> getSummary(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @Valid @ModelAttribute AgencyStatisticsDateRangeRequest request) throws IllegalAccessException {

        checkAgencyRole(userDetails);
        AgencyStatisticsSummaryResponse response = statisticsService.getSummary(userDetails.getAgencyId(), request);
        return ResponseEntity.ok(response);
    }

    // ─────────────────────────────────────────────
    // GET /api/agency/statistics/daily-trend
    // 날짜별 접수·완료 추이 목록 (라인차트)
    // ─────────────────────────────────────────────
    @GetMapping("/daily-trend")
    public ResponseEntity<List<AgencyStatisticsDailyTrendResponse>> getDailyTrend(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @Valid @ModelAttribute AgencyStatisticsDateRangeRequest request) throws IllegalAccessException {

        checkAgencyRole(userDetails);
        return ResponseEntity.ok(statisticsService.getDailyTrend(userDetails.getAgencyId(), request));
    }

    // ─────────────────────────────────────────────
    // GET /api/agency/statistics/hourly
    // 3시간 단위 시간대별 접수 현황 (바차트, 항상 8개 슬롯)
    // ─────────────────────────────────────────────
    @GetMapping("/hourly")
    public ResponseEntity<List<AgencyStatisticsHourlyResponse>> getHourly(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @Valid @ModelAttribute AgencyStatisticsDateRangeRequest request) throws IllegalAccessException {

        checkAgencyRole(userDetails);
        return ResponseEntity.ok(statisticsService.getHourly(userDetails.getAgencyId(), request));
    }

    // ─────────────────────────────────────────────
    // GET /api/agency/statistics/category-dist
    // 가전 카테고리별 접수 분포 (도넛차트)
    // ─────────────────────────────────────────────
    @GetMapping("/category-dist")
    public ResponseEntity<List<AgencyStatisticsCategoryDistResponse>> getCategoryDist(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @Valid @ModelAttribute AgencyStatisticsDateRangeRequest request) throws IllegalAccessException {

        checkAgencyRole(userDetails);
        return ResponseEntity.ok(statisticsService.getCategoryDist(userDetails.getAgencyId(), request));
    }

    // ─────────────────────────────────────────────
    // GET /api/agency/statistics/status-count
    // A/S 상태별 건수 및 비율
    // ─────────────────────────────────────────────
    @GetMapping("/status-count")
    public ResponseEntity<List<AgencyStatisticsStatusCountResponse>> getStatusCount(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @Valid @ModelAttribute AgencyStatisticsDateRangeRequest request) throws IllegalAccessException {

        checkAgencyRole(userDetails);
        return ResponseEntity.ok(statisticsService.getStatusCount(userDetails.getAgencyId(), request));
    }

    // ─────────────────────────────────────────────
    // GET /api/agency/statistics/engineer-top5
    // 기사별 완료 건수 상위 5명
    // ─────────────────────────────────────────────
    @GetMapping("/engineer-top5")
    public ResponseEntity<List<AgencyStatisticsEngineerTop5Response>> getEngineerTop5(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @Valid @ModelAttribute AgencyStatisticsDateRangeRequest request) throws IllegalAccessException {

        checkAgencyRole(userDetails);
        return ResponseEntity.ok(statisticsService.getEngineerTop5(userDetails.getAgencyId(), request));
    }

    // ─────────────────────────────────────────────
    // GET /api/agency/statistics/monthly-summary
    // 이달의 요약 하이라이트 지표 (요청 시점 현재 달 자동 산정)
    // ─────────────────────────────────────────────
    @GetMapping("/monthly-summary")
    public ResponseEntity<AgencyStatisticsMonthlySummaryResponse> getMonthlySummary(
            @AuthenticationPrincipal CustomUserDetails userDetails) throws IllegalAccessException {

        checkAgencyRole(userDetails);
        return ResponseEntity.ok(statisticsService.getMonthlySummary(userDetails.getAgencyId()));
    }

    // ─────────────────────────────────────────────
    // GET /api/agency/statistics/recent-periods
    // "최근 주요 지표 요약" 테이블 — 이번 달 + 직전 2개월 비교
    // ─────────────────────────────────────────────
    @GetMapping("/recent-periods")
    public ResponseEntity<List<AgencyStatisticsPeriodSummaryResponse>> getRecentPeriods(
            @AuthenticationPrincipal CustomUserDetails userDetails) throws IllegalAccessException {

        checkAgencyRole(userDetails);
        return ResponseEntity.ok(statisticsService.getRecentPeriodsSummary(userDetails.getAgencyId()));
    }

    // ─────────────────────────────────────────────
    // GET /api/agency/statistics/engineers
    // "기사 통계" 탭 — 기간 내 전체 기사별 완료건수·평균평점
    // ─────────────────────────────────────────────
    @GetMapping("/engineers")
    public ResponseEntity<List<AgencyStatisticsEngineerStatResponse>> getEngineerStats(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @Valid @ModelAttribute AgencyStatisticsDateRangeRequest request) throws IllegalAccessException {

        checkAgencyRole(userDetails);
        return ResponseEntity.ok(statisticsService.getEngineerStats(userDetails.getAgencyId(), request));
    }

    // ─────────────────────────────────────────────
    // GET /api/agency/statistics/settlement-trend
    // "정산 통계" 탭 — 기간 내 일자별 정산 추이
    // ─────────────────────────────────────────────
    @GetMapping("/settlement-trend")
    public ResponseEntity<List<AgencyStatisticsSettlementTrendResponse>> getSettlementTrend(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @Valid @ModelAttribute AgencyStatisticsDateRangeRequest request) throws IllegalAccessException {

        checkAgencyRole(userDetails);
        return ResponseEntity.ok(statisticsService.getSettlementTrend(userDetails.getAgencyId(), request));
    }

    // ─────────────────────────────────────────────
    // GET /api/agency/statistics/customers
    // "고객 통계" 탭 — 기간 내 고객별 접수건수·평균만족도
    // ─────────────────────────────────────────────
    @GetMapping("/customers")
    public ResponseEntity<List<AgencyStatisticsCustomerStatResponse>> getCustomerStats(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @Valid @ModelAttribute AgencyStatisticsDateRangeRequest request) throws IllegalAccessException {

        checkAgencyRole(userDetails);
        return ResponseEntity.ok(statisticsService.getCustomerStats(userDetails.getAgencyId(), request));
    }

    // ─────────────────────────────────────────────
    // 리포트 다운로드 4종 (CSV, UTF-8 BOM 포함)
    // ─────────────────────────────────────────────

    @GetMapping("/reports/work-status/download")
    public ResponseEntity<byte[]> downloadWorkStatusReport(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @Valid @ModelAttribute AgencyStatisticsDateRangeRequest request) throws IllegalAccessException {

        checkAgencyRole(userDetails);
        byte[] csv = statisticsService.generateWorkStatusReportCsv(userDetails.getAgencyId(), request);
        return csvResponse(csv, "work_status_report_" + request.dateFrom() + "_" + request.dateTo() + ".csv");
    }

    @GetMapping("/reports/settlement/download")
    public ResponseEntity<byte[]> downloadSettlementReport(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @Valid @ModelAttribute AgencyStatisticsDateRangeRequest request) throws IllegalAccessException {

        checkAgencyRole(userDetails);
        byte[] csv = statisticsService.generateSettlementReportCsv(userDetails.getAgencyId(), request);
        return csvResponse(csv, "settlement_report_" + request.dateFrom() + "_" + request.dateTo() + ".csv");
    }

    @GetMapping("/reports/engineer-performance/download")
    public ResponseEntity<byte[]> downloadEngineerPerformanceReport(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @Valid @ModelAttribute AgencyStatisticsDateRangeRequest request) throws IllegalAccessException {

        checkAgencyRole(userDetails);
        byte[] csv = statisticsService.generateEngineerPerformanceReportCsv(userDetails.getAgencyId(), request);
        return csvResponse(csv, "engineer_performance_report_" + request.dateFrom() + "_" + request.dateTo() + ".csv");
    }

    @GetMapping("/reports/customer/download")
    public ResponseEntity<byte[]> downloadCustomerReport(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @Valid @ModelAttribute AgencyStatisticsDateRangeRequest request) throws IllegalAccessException {

        checkAgencyRole(userDetails);
        byte[] csv = statisticsService.generateCustomerStatusReportCsv(userDetails.getAgencyId(), request);
        return csvResponse(csv, "customer_status_report_" + request.dateFrom() + "_" + request.dateTo() + ".csv");
    }

    private ResponseEntity<byte[]> csvResponse(byte[] csv, String filename) {
        return ResponseEntity.ok()
                .header(org.springframework.http.HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"" + filename + "\"")
                .contentType(org.springframework.http.MediaType.parseMediaType("text/csv; charset=UTF-8"))
                .body(csv);
    }

    // ─────────────────────────────────────────────
    // 공통: AGENCY 역할 검증
    // ─────────────────────────────────────────────
    private void checkAgencyRole(CustomUserDetails userDetails) throws IllegalAccessException {
        if (!"AGENCY".equals(userDetails.getRole())) {
            throw new IllegalAccessException("대행사 계정만 접근 가능합니다.");
        }
    }
}
