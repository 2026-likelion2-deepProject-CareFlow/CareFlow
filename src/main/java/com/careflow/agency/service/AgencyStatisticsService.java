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

        // 현재 기간 집계
        long totalReceipts         = statsRepo.countReceipts(agencyId, from, to);
        long completedCount        = statsRepo.countCompleted(agencyId, from, to);
        Double avgMinutes          = statsRepo.findAvgProcessingTimeMinutes(agencyId, from, to);
        Double avgRating           = statsRepo.findAvgRating(agencyId, from, to);
        long totalSettlementAmount = statsRepo.sumSettlementAmount(agencyId, from, to);

        double completionRate         = totalReceipts == 0 ? 0.0 : round1((double) completedCount / totalReceipts * 100);
        double avgProcessingTimeHours = avgMinutes == null ? 0.0 : round1(avgMinutes / 60.0);
        double avgRatingVal           = avgRating == null  ? 0.0 : round1(avgRating);

        // 전 기간 집계 (증감률 계산)
        long prevReceipts         = statsRepo.countReceipts(agencyId, prevFrom, prevTo);
        long prevCompleted        = statsRepo.countCompleted(agencyId, prevFrom, prevTo);
        Double prevAvgMinutes     = statsRepo.findAvgProcessingTimeMinutes(agencyId, prevFrom, prevTo);
        Double prevAvgRating      = statsRepo.findAvgRating(agencyId, prevFrom, prevTo);
        long prevSettlement       = statsRepo.sumSettlementAmount(agencyId, prevFrom, prevTo);

        double prevCompletionRate = prevReceipts == 0 ? 0.0 : round1((double) prevCompleted / prevReceipts * 100);
        double prevAvgHours       = prevAvgMinutes == null ? 0.0 : round1(prevAvgMinutes / 60.0);
        double prevRatingVal      = prevAvgRating == null  ? 0.0 : round1(prevAvgRating);

        return new AgencyStatisticsSummaryResponse(
                totalReceipts,
                changeRate(totalReceipts, prevReceipts),
                completedCount,
                changeRate(completedCount, prevCompleted),
                completionRate,
                round1(completionRate - prevCompletionRate),
                avgProcessingTimeHours,
                round1(avgProcessingTimeHours - prevAvgHours),
                avgRatingVal,
                round1(avgRatingVal - prevRatingVal),
                totalSettlementAmount,
                changeRate(totalSettlementAmount, prevSettlement)
        );
    }

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
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("MM.dd");

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

        String[] labels = {"00-03시","03-06시","06-09시","09-12시","12-15시","15-18시","18-21시","21-24시"};
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

        // 배차 완료 이상 상태 합산
        long assigned = sum(countMap, "ASSIGNED","ACCEPTED","ENGINEER_DEPARTED","ENGINEER_ARRIVED","IN_PROGRESS","COMPLETED","PAID");
        long inProgress = countMap.getOrDefault("IN_PROGRESS", 0L);
        long completed  = sum(countMap, "COMPLETED","PAID");
        long cancelled  = countMap.getOrDefault("CANCELLED", 0L);

        List<AgencyStatisticsStatusCountResponse> result = new ArrayList<>();
        result.add(new AgencyStatisticsStatusCountResponse("접수",      total,      total == 0 ? 0.0 : 100.0));
        result.add(new AgencyStatisticsStatusCountResponse("배차 완료", assigned,   pct(assigned, total)));
        result.add(new AgencyStatisticsStatusCountResponse("작업 중",   inProgress, pct(inProgress, total)));
        result.add(new AgencyStatisticsStatusCountResponse("작업 완료", completed,  pct(completed, total)));
        result.add(new AgencyStatisticsStatusCountResponse("취소",      cancelled,  pct(cancelled, total)));
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
        String topDayOfWeek = formatTopDay(topDay);

        // 최다 접수 시간대
        Object[] topHour = statsRepo.findTopHourSlot(agencyId, from, to);
        String topHourSlot = formatTopHour(topHour);

        // 최고 평점 기사
        Object[] topEngineer = statsRepo.findTopRatedEngineer(agencyId, from, to);
        String topRatedEngineerName = formatTopEngineer(topEngineer);

        // 고객 만족도
        Object[] satisfaction = statsRepo.findSatisfactionStats(agencyId, from, to);
        double satisfactionRate = calcSatisfactionRate(satisfaction);

        return new AgencyStatisticsMonthlySummaryResponse(
                topDayOfWeek,
                topHourSlot,
                topRatedEngineerName,
                satisfactionRate
        );
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

    /** 여러 status key 합산 */
    private long sum(java.util.Map<String, Long> map, String... keys) {
        long total = 0;
        for (String key : keys) total += map.getOrDefault(key, 0L);
        return total;
    }

    private static final String[] DAY_NAMES = {"","일요일","월요일","화요일","수요일","목요일","금요일","토요일"};

    private String formatTopDay(Object[] row) {
        if (row == null) return "데이터 없음";
        int dow   = ((Number) row[0]).intValue();      // 1=일, ..., 7=토
        long cnt  = ((Number) row[1]).longValue();
        String name = (dow >= 1 && dow <= 7) ? DAY_NAMES[dow] : "알 수 없음";
        return name + " (" + cnt + "건)";
    }

    private String formatTopHour(Object[] row) {
        if (row == null) return "데이터 없음";
        int hr   = ((Number) row[0]).intValue();
        long cnt = ((Number) row[1]).longValue();
        return String.format("%02d-%02d시 (%d건)", hr, hr + 1, cnt);
    }

    private String formatTopEngineer(Object[] row) {
        if (row == null) return "데이터 없음";
        String name   = (String) row[0];
        double rating = round1(((Number) row[1]).doubleValue());
        return name + " 기사 (" + rating + ")";
    }

    private double calcSatisfactionRate(Object[] row) {
        if (row == null) return 0.0;
        long satisfied = ((Number) row[0]).longValue();
        long total     = ((Number) row[1]).longValue();
        return total == 0 ? 0.0 : round1((double) satisfied / total * 100);
    }
}
