package com.careflow.agency.service;

import com.careflow.agency.dto.request.AgencyStatisticsDateRangeRequest;
import com.careflow.agency.dto.response.*;
import com.careflow.agency.repository.AgencyStatisticsQueryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class AgencyStatisticsService {

    private final AgencyStatisticsQueryRepository statsRepo;
    private final AgencyStatisticsReportCsvGenerator csvGenerator;

    // ──────────────────────────────────────────────────────────────
    // 1. Summary
    // ──────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public AgencyStatisticsSummaryResponse getSummary(Long agencyId, AgencyStatisticsDateRangeRequest req) {
        validateDateRange(req.dateFrom(), req.dateTo());

        LocalDateTime from = req.dateFrom().atStartOfDay();
        LocalDateTime to   = req.dateTo().plusDays(1).atStartOfDay();

        // 전 기간: 동일 길이만큼 이전 구간
        long periodDays = req.dateFrom().until(req.dateTo().plusDays(1), java.time.temporal.ChronoUnit.DAYS);
        LocalDateTime prevFrom = from.minusDays(periodDays);
        LocalDateTime prevTo   = from;

        PeriodStats current = computePeriodStats(agencyId, from, to);
        PeriodStats prev    = computePeriodStats(agencyId, prevFrom, prevTo);

        return new AgencyStatisticsSummaryResponse(
                current.receiptCount(),                                     // totalReceiptCount
                current.completedCount(),                                   // completedCount
                current.completionRate(),                                   // completionRate
                current.avgProcessingHours(),                                // avgProcessingHours
                current.avgRating(),                                        // avgRating
                current.totalSettlementAmount(),                            // totalSettlementAmount
                changeRate(current.receiptCount(), prev.receiptCount()),     // prevMonthReceiptDiff
                changeRate(current.completedCount(), prev.completedCount()), // prevMonthCompletedDiff
                round1(current.avgRating() - prev.avgRating()),             // prevMonthRatingDiff (절대 차이)
                changeRate(current.totalSettlementAmount(), prev.totalSettlementAmount()) // prevMonthAmountDiff
        );
    }

    // ──────────────────────────────────────────────────────────────
    // 1-1. 최근 주요 지표 요약 (기간별 비교 테이블)
    // 이번 달(1일~오늘) + 직전 2개월(각 1일~말일) 총 3개 구간을 비교
    // ──────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<AgencyStatisticsPeriodSummaryResponse> getRecentPeriodsSummary(Long agencyId) {
        LocalDate today = LocalDate.now();
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyy.MM.dd");

        List<AgencyStatisticsPeriodSummaryResponse> result = new ArrayList<>();

        // 이번 달 1일 ~ 오늘
        LocalDate curStart = today.withDayOfMonth(1);
        result.add(toPeriodResponse(agencyId, curStart, today, fmt));

        // 직전 2개월 (각각 1일~말일 풀 개월)
        for (int i = 1; i <= 2; i++) {
            LocalDate monthStart = today.minusMonths(i).withDayOfMonth(1);
            LocalDate monthEnd   = monthStart.withDayOfMonth(monthStart.lengthOfMonth());
            result.add(toPeriodResponse(agencyId, monthStart, monthEnd, fmt));
        }

        return result;
    }

    private AgencyStatisticsPeriodSummaryResponse toPeriodResponse(
            Long agencyId, LocalDate start, LocalDate end, DateTimeFormatter fmt) {
        LocalDateTime from = start.atStartOfDay();
        LocalDateTime to   = end.plusDays(1).atStartOfDay();
        PeriodStats stats  = computePeriodStats(agencyId, from, to);
        String label = start.format(fmt) + " ~ " + end.format(fmt);

        return new AgencyStatisticsPeriodSummaryResponse(
                label,
                stats.receiptCount(),
                stats.completedCount(),
                stats.completionRate(),
                stats.avgProcessingHours(),
                stats.avgRating(),
                stats.totalSettlementAmount()
        );
    }

    /** 특정 기간의 핵심 지표 집계 — getSummary()의 현재/전 기간 계산과 getRecentPeriodsSummary()에서 공용으로 사용 */
    private PeriodStats computePeriodStats(Long agencyId, LocalDateTime from, LocalDateTime to) {
        long receiptCount          = statsRepo.countReceipts(agencyId, from, to);
        long completedCount        = statsRepo.countCompleted(agencyId, from, to);
        Double avgMinutes          = statsRepo.findAvgProcessingTimeMinutes(agencyId, from, to);
        Double avgRating           = statsRepo.findAvgRating(agencyId, from, to);
        long totalSettlementAmount = statsRepo.sumSettlementAmount(agencyId, from, to);

        double completionRate         = receiptCount == 0 ? 0.0 : round1((double) completedCount / receiptCount * 100);
        double avgProcessingTimeHours = avgMinutes == null ? 0.0 : round1(avgMinutes / 60.0);
        double avgRatingVal           = avgRating == null  ? 0.0 : round1(avgRating);

        return new PeriodStats(receiptCount, completedCount, completionRate, avgProcessingTimeHours,
                avgRatingVal, totalSettlementAmount);
    }

    private record PeriodStats(
            long receiptCount, long completedCount, double completionRate,
            double avgProcessingHours, double avgRating, long totalSettlementAmount) {}

    // ──────────────────────────────────────────────────────────────
    // 2. Daily Trend
    // ──────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<AgencyStatisticsDailyTrendResponse> getDailyTrend(Long agencyId, AgencyStatisticsDateRangeRequest req) {
        validateDateRange(req.dateFrom(), req.dateTo());

        LocalDateTime from = req.dateFrom().atStartOfDay();
        LocalDateTime to   = req.dateTo().plusDays(1).atStartOfDay();

        List<Object[]> rows = statsRepo.findDailyTrend(agencyId, from, to);
        List<AgencyStatisticsDailyTrendResponse> result = new ArrayList<>();
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd");

        for (Object[] row : rows) {
            // row[0]: java.sql.Date or String depending on driver
            String dateStr = row[0].toString(); // yyyy-MM-dd 형식
            LocalDate date = LocalDate.parse(dateStr.substring(0, 10));
            long receiptCount   = ((Number) row[1]).longValue();
            long completedCount = ((Number) row[2]).longValue();
            result.add(new AgencyStatisticsDailyTrendResponse(date.format(fmt), receiptCount, completedCount));
        }
        return result;
    }

    // ──────────────────────────────────────────────────────────────
    // 3. Hourly
    // ──────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<AgencyStatisticsHourlyResponse> getHourly(Long agencyId, AgencyStatisticsDateRangeRequest req) {
        validateDateRange(req.dateFrom(), req.dateTo());

        LocalDateTime from = req.dateFrom().atStartOfDay();
        LocalDateTime to   = req.dateTo().plusDays(1).atStartOfDay();

        List<Object[]> rows = statsRepo.findHourlyDist(agencyId, from, to);

        // 0~7 인덱스 → 8개 슬롯 배열로 채우기 (없는 슬롯은 0)
        long[] slotCounts = new long[8];
        for (Object[] row : rows) {
            int slotIdx = ((Number) row[0]).intValue();
            if (slotIdx >= 0 && slotIdx < 8) {
                slotCounts[slotIdx] = ((Number) row[1]).longValue();
            }
        }

        String[] labels = {"00-03","03-06","06-09","09-12","12-15","15-18","18-21","21-24"};
        List<AgencyStatisticsHourlyResponse> result = new ArrayList<>(8);
        for (int i = 0; i < 8; i++) {
            result.add(new AgencyStatisticsHourlyResponse(labels[i], slotCounts[i]));
        }
        return result;
    }

    // ──────────────────────────────────────────────────────────────
    // 4. Category Distribution
    // ──────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<AgencyStatisticsCategoryDistResponse> getCategoryDist(Long agencyId, AgencyStatisticsDateRangeRequest req) {
        validateDateRange(req.dateFrom(), req.dateTo());

        LocalDateTime from = req.dateFrom().atStartOfDay();
        LocalDateTime to   = req.dateTo().plusDays(1).atStartOfDay();

        List<Object[]> rows = statsRepo.findCategoryDist(agencyId, from, to);
        if (rows.isEmpty()) return List.of();

        long total = rows.stream().mapToLong(r -> ((Number) r[1]).longValue()).sum();

        List<AgencyStatisticsCategoryDistResponse> result = new ArrayList<>();
        for (Object[] row : rows) {
            String categoryName = (String) row[0];
            long count = ((Number) row[1]).longValue();
            double pct = total == 0 ? 0.0 : round1((double) count / total * 100);
            result.add(new AgencyStatisticsCategoryDistResponse(categoryName, count, pct));
        }
        return result;
    }

    // ──────────────────────────────────────────────────────────────
    // 5. Status Count
    // ──────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<AgencyStatisticsStatusCountResponse> getStatusCount(Long agencyId, AgencyStatisticsDateRangeRequest req) {
        validateDateRange(req.dateFrom(), req.dateTo());

        LocalDateTime from = req.dateFrom().atStartOfDay();
        LocalDateTime to   = req.dateTo().plusDays(1).atStartOfDay();

        List<Object[]> rows = statsRepo.findStatusCount(agencyId, from, to);

        // enum별 건수 집계 맵
        java.util.Map<String, Long> countMap = new java.util.HashMap<>();
        for (Object[] row : rows) {
            countMap.put((String) row[0], ((Number) row[1]).longValue());
        }

        long total = countMap.values().stream().mapToLong(Long::longValue).sum();

        // AsStatus enum 코드값 기준으로 건수/비율 집계
        List<AgencyStatisticsStatusCountResponse> result = new ArrayList<>();
        for (com.careflow.common.enums.AsStatus status : com.careflow.common.enums.AsStatus.values()) {
            long count = countMap.getOrDefault(status.name(), 0L);
            result.add(new AgencyStatisticsStatusCountResponse(status.name(), count, pct(count, total)));
        }
        return result;
    }

    // ──────────────────────────────────────────────────────────────
    // 6. Engineer Top5
    // ──────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<AgencyStatisticsEngineerTop5Response> getEngineerTop5(Long agencyId, AgencyStatisticsDateRangeRequest req) {
        validateDateRange(req.dateFrom(), req.dateTo());

        LocalDateTime from = req.dateFrom().atStartOfDay();
        LocalDateTime to   = req.dateTo().plusDays(1).atStartOfDay();

        List<Object[]> rows = statsRepo.findEngineerTop5(agencyId, from, to);
        List<AgencyStatisticsEngineerTop5Response> result = new ArrayList<>();

        for (int i = 0; i < rows.size(); i++) {
            Object[] row = rows.get(i);
            String engineerName  = (String) row[0];
            long completedCount  = ((Number) row[1]).longValue();
            result.add(new AgencyStatisticsEngineerTop5Response(i + 1, engineerName, completedCount));
        }
        return result;
    }

    // ──────────────────────────────────────────────────────────────
    // 7. Monthly Summary
    // ──────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public AgencyStatisticsMonthlySummaryResponse getMonthlySummary(Long agencyId) {
        // 현재 달 1일 ~ 다음 달 1일
        LocalDate today      = LocalDate.now();
        LocalDateTime from   = today.withDayOfMonth(1).atStartOfDay();
        LocalDateTime to     = from.plusMonths(1);

        // 최다 접수 요일
        Object[] topDay = statsRepo.findTopDayOfWeek(agencyId, from, to);
        String topReceiptDayOfWeek = topDayName(topDay);
        long topReceiptDayCount    = topDay == null ? 0L : ((Number) topDay[1]).longValue();

        // 최다 접수 시간대
        Object[] topHour = statsRepo.findTopHourSlot(agencyId, from, to);
        String topReceiptHour      = topHourLabel(topHour);
        long topReceiptHourCount   = topHour == null ? 0L : ((Number) topHour[1]).longValue();

        // 최고 평점 기사
        Object[] topEngineer = statsRepo.findTopRatedEngineer(agencyId, from, to);
        String topRatingEngineerName = topEngineer == null ? "데이터 없음" : (String) topEngineer[0];
        double topRatingEngineerScore = topEngineer == null ? 0.0 : round1(((Number) topEngineer[1]).doubleValue());

        return new AgencyStatisticsMonthlySummaryResponse(
                topReceiptDayOfWeek,
                topReceiptDayCount,
                topReceiptHour,
                topReceiptHourCount,
                topRatingEngineerName,
                topRatingEngineerScore
        );
    }

    // ──────────────────────────────────────────────────────────────
    // 8. 리포트 다운로드 4종 (CSV)
    // ──────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public byte[] generateWorkStatusReportCsv(Long agencyId, AgencyStatisticsDateRangeRequest req) {
        validateDateRange(req.dateFrom(), req.dateTo());
        LocalDateTime from = req.dateFrom().atStartOfDay();
        LocalDateTime to   = req.dateTo().plusDays(1).atStartOfDay();

        List<Object[]> dailyRows = statsRepo.findDailyTrend(agencyId, from, to);
        return csvGenerator.generateWorkStatus(dailyRows);
    }

    @Transactional(readOnly = true)
    public byte[] generateSettlementReportCsv(Long agencyId, AgencyStatisticsDateRangeRequest req) {
        validateDateRange(req.dateFrom(), req.dateTo());
        LocalDateTime from = req.dateFrom().atStartOfDay();
        LocalDateTime to   = req.dateTo().plusDays(1).atStartOfDay();

        List<Object[]> dailyRows = statsRepo.findDailySettlementBreakdown(agencyId, from, to);
        return csvGenerator.generateSettlement(dailyRows);
    }

    @Transactional(readOnly = true)
    public byte[] generateEngineerPerformanceReportCsv(Long agencyId, AgencyStatisticsDateRangeRequest req) {
        validateDateRange(req.dateFrom(), req.dateTo());
        LocalDateTime from = req.dateFrom().atStartOfDay();
        LocalDateTime to   = req.dateTo().plusDays(1).atStartOfDay();

        List<Object[]> completedRows = statsRepo.findAllEngineerCompleted(agencyId, from, to);
        List<Object[]> ratingRows    = statsRepo.findEngineerRatings(agencyId, from, to);

        java.util.Map<Long, Double> ratingByEngineerId = new java.util.HashMap<>();
        for (Object[] row : ratingRows) {
            ratingByEngineerId.put(((Number) row[0]).longValue(), ((Number) row[1]).doubleValue());
        }
        return csvGenerator.generateEngineerPerformance(completedRows, ratingByEngineerId);
    }

    @Transactional(readOnly = true)
    public byte[] generateCustomerStatusReportCsv(Long agencyId, AgencyStatisticsDateRangeRequest req) {
        validateDateRange(req.dateFrom(), req.dateTo());
        LocalDateTime from = req.dateFrom().atStartOfDay();
        LocalDateTime to   = req.dateTo().plusDays(1).atStartOfDay();

        List<Object[]> activityRows = statsRepo.findCustomerActivity(agencyId, from, to);
        List<Object[]> ratingRows   = statsRepo.findCustomerRatings(agencyId, from, to);

        java.util.Map<Long, Double> ratingByCustomerId = new java.util.HashMap<>();
        for (Object[] row : ratingRows) {
            ratingByCustomerId.put(((Number) row[0]).longValue(), ((Number) row[1]).doubleValue());
        }
        return csvGenerator.generateCustomerStatus(activityRows, ratingByCustomerId);
    }

    // ──────────────────────────────────────────────────────────────
    // 9. 탭별 상세 통계 (기사 통계 / 정산 통계 / 고객 통계 탭)
    // 리포트 다운로드(CSV)와 동일한 리포지토리 쿼리를 JSON으로도 노출
    // ──────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<AgencyStatisticsEngineerStatResponse> getEngineerStats(Long agencyId, AgencyStatisticsDateRangeRequest req) {
        validateDateRange(req.dateFrom(), req.dateTo());
        LocalDateTime from = req.dateFrom().atStartOfDay();
        LocalDateTime to   = req.dateTo().plusDays(1).atStartOfDay();

        List<Object[]> completedRows = statsRepo.findAllEngineerCompleted(agencyId, from, to);
        List<Object[]> ratingRows    = statsRepo.findEngineerRatings(agencyId, from, to);

        java.util.Map<Long, Double> ratingByEngineerId = new java.util.HashMap<>();
        for (Object[] row : ratingRows) {
            ratingByEngineerId.put(((Number) row[0]).longValue(), ((Number) row[1]).doubleValue());
        }

        List<AgencyStatisticsEngineerStatResponse> result = new ArrayList<>();
        for (int i = 0; i < completedRows.size(); i++) {
            Object[] row = completedRows.get(i);
            long engineerId = ((Number) row[0]).longValue();
            result.add(new AgencyStatisticsEngineerStatResponse(
                    i + 1, engineerId, (String) row[1], ((Number) row[2]).longValue(),
                    ratingByEngineerId.get(engineerId)));
        }
        return result;
    }

    @Transactional(readOnly = true)
    public List<AgencyStatisticsSettlementTrendResponse> getSettlementTrend(Long agencyId, AgencyStatisticsDateRangeRequest req) {
        validateDateRange(req.dateFrom(), req.dateTo());
        LocalDateTime from = req.dateFrom().atStartOfDay();
        LocalDateTime to   = req.dateTo().plusDays(1).atStartOfDay();

        List<Object[]> rows = statsRepo.findDailySettlementBreakdown(agencyId, from, to);
        List<AgencyStatisticsSettlementTrendResponse> result = new ArrayList<>();
        for (Object[] row : rows) {
            result.add(new AgencyStatisticsSettlementTrendResponse(
                    row[0].toString().substring(0, 10),
                    ((Number) row[1]).longValue(),
                    ((Number) row[2]).longValue(),
                    ((Number) row[3]).longValue(),
                    ((Number) row[4]).longValue(),
                    ((Number) row[5]).longValue()));
        }
        return result;
    }

    @Transactional(readOnly = true)
    public List<AgencyStatisticsCustomerStatResponse> getCustomerStats(Long agencyId, AgencyStatisticsDateRangeRequest req) {
        validateDateRange(req.dateFrom(), req.dateTo());
        LocalDateTime from = req.dateFrom().atStartOfDay();
        LocalDateTime to   = req.dateTo().plusDays(1).atStartOfDay();

        List<Object[]> activityRows = statsRepo.findCustomerActivity(agencyId, from, to);
        List<Object[]> ratingRows   = statsRepo.findCustomerRatings(agencyId, from, to);

        java.util.Map<Long, Double> ratingByCustomerId = new java.util.HashMap<>();
        for (Object[] row : ratingRows) {
            ratingByCustomerId.put(((Number) row[0]).longValue(), ((Number) row[1]).doubleValue());
        }

        List<AgencyStatisticsCustomerStatResponse> result = new ArrayList<>();
        for (Object[] row : activityRows) {
            long customerId = ((Number) row[0]).longValue();
            result.add(new AgencyStatisticsCustomerStatResponse(
                    customerId, (String) row[1], ((Number) row[2]).longValue(),
                    ratingByCustomerId.get(customerId)));
        }
        return result;
    }

    // ──────────────────────────────────────────────────────────────
    // 내부 유틸 메서드
    // ──────────────────────────────────────────────────────────────

    private void validateDateRange(LocalDate from, LocalDate to) {
        if (from.isAfter(to)) {
            throw new IllegalArgumentException("시작일은 종료일보다 이전이어야 합니다.");
        }
    }

    /** 소수점 1자리 반올림 */
    private double round1(double val) {
        return Math.round(val * 10.0) / 10.0;
    }

    /** 전 기간 대비 증감률 (%) — 전 기간 0이면 0.0 */
    private double changeRate(long current, long prev) {
        if (prev == 0) return 0.0;
        return round1((double)(current - prev) / prev * 100);
    }

    /** 비율 계산 (소수점 1자리) */
    private double pct(long part, long total) {
        return total == 0 ? 0.0 : round1((double) part / total * 100);
    }

    private static final String[] DAY_NAMES = {"","일요일","월요일","화요일","수요일","목요일","금요일","토요일"};

    private String topDayName(Object[] row) {
        if (row == null) return "데이터 없음";
        int dow = ((Number) row[0]).intValue();      // 1=일, ..., 7=토
        return (dow >= 1 && dow <= 7) ? DAY_NAMES[dow] : "알 수 없음";
    }

    private String topHourLabel(Object[] row) {
        if (row == null) return "데이터 없음";
        int hr = ((Number) row[0]).intValue();
        return String.format("%02d-%02d시", hr, hr + 1);
    }
}
