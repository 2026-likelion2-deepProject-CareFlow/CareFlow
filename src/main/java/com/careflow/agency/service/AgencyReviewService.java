package com.careflow.agency.service;

import com.careflow.agency.dto.request.AgencyReviewSearchRequest;
import com.careflow.agency.dto.response.AgencyReviewListResponse;
import com.careflow.auth.security.CustomUserDetails;
import com.careflow.review.repository.ReviewRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;

@Service
@RequiredArgsConstructor
public class AgencyReviewService {

    private final ReviewRepository reviewRepository;

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ISO_LOCAL_DATE;

    /**
     * 대행사 리뷰 목록 조회 (GET /api/agency/reviews)
     *
     * 처리 순서:
     * 1. AGENCY 역할 검증
     * 2. stats 집계 (필터 무관, 항상 전체 모수 기준)
     * 3. content 조회 (필터 + 페이징)
     * 4. 응답 조립
     */
    @Transactional(readOnly = true)
    public AgencyReviewListResponse getReviews(
            CustomUserDetails userDetails,
            AgencyReviewSearchRequest filter,
            Pageable pageable) throws IllegalAccessException {

        // 1. AGENCY 역할 검증 — ENGINEER도 agencyId 클레임을 보유하므로 명시적 검증 필요
        if (!"AGENCY".equals(userDetails.getRole())) {
            throw new IllegalAccessException("대행사 계정만 접근할 수 있습니다.");
        }

        Long agencyId = userDetails.getAgencyId();

        // 2. 날짜 필터 파싱 (잘못된 형식이면 즉시 IllegalArgumentException → 400, DB 조회 전 fail-fast)
        LocalDateTime dateFrom = parseDate(filter.getDateFrom(), false);
        LocalDateTime dateTo   = parseDate(filter.getDateTo(), true);

        // 3. stats 집계
        AgencyReviewListResponse.Stats stats = buildStats(agencyId);

        // 4. content 조회 (필터 적용, 페이징)
        Page<AgencyReviewListResponse.ReviewSummary> page = reviewRepository.findAgencyReviews(
                agencyId,
                filter.getRating(),
                filter.getEngineerId(),
                filter.getIsVisible(),
                dateFrom,
                dateTo,
                filter.getKeyword() != null && filter.getKeyword().isBlank() ? null : filter.getKeyword(),
                pageable
        );

        return AgencyReviewListResponse.of(stats, page);
    }

    /**
     * stats 집계 — 검색 필터 무관, 항상 전체 모수 기준
     * - 전체 avgRating, totalCount, fiveStarRate
     * - 이번달 / 전월 신규 리뷰 수 및 평균 평점 (전월 대비 차이 계산)
     */
    private AgencyReviewListResponse.Stats buildStats(Long agencyId) {
        // 전체 통계 — Spring Data JPA가 다중 집계 쿼리를 List<Object[]>로 반환
        List<Object[]> overallRows = reviewRepository.findAgencyReviewStats(agencyId);
        Object[] overall    = overallRows.isEmpty() ? new Object[]{0L, 0.0, 0L} : overallRows.get(0);
        long totalCount     = ((Number) overall[0]).longValue();
        double avgRating    = ((Number) overall[1]).doubleValue();
        long fiveStarCount  = overall[2] != null ? ((Number) overall[2]).longValue() : 0L;
        double fiveStarRate = totalCount > 0
                ? Math.round(fiveStarCount * 1000.0 / totalCount) / 10.0
                : 0.0;

        // 이번달 / 전월 범위 계산
        YearMonth thisMonth  = YearMonth.now();
        YearMonth prevMonth  = thisMonth.minusMonths(1);
        LocalDateTime thisMonthStart = thisMonth.atDay(1).atStartOfDay();
        LocalDateTime nextMonthStart = thisMonth.plusMonths(1).atDay(1).atStartOfDay();
        LocalDateTime prevMonthStart = prevMonth.atDay(1).atStartOfDay();

        List<Object[]> thisMonthRows = reviewRepository.findAgencyReviewStatsByPeriod(
                agencyId, thisMonthStart, nextMonthStart);
        List<Object[]> prevMonthRows = reviewRepository.findAgencyReviewStatsByPeriod(
                agencyId, prevMonthStart, thisMonthStart);
        Object[] thisMonthStat = thisMonthRows.isEmpty() ? new Object[]{0L, 0.0} : thisMonthRows.get(0);
        Object[] prevMonthStat = prevMonthRows.isEmpty() ? new Object[]{0L, 0.0} : prevMonthRows.get(0);

        long newThisMonth      = ((Number) thisMonthStat[0]).longValue();
        double thisMonthAvg    = ((Number) thisMonthStat[1]).doubleValue();
        long prevMonthNewCount = ((Number) prevMonthStat[0]).longValue();
        double prevMonthAvg    = ((Number) prevMonthStat[1]).doubleValue();

        double prevMonthAvgRatingDiff = Math.round((thisMonthAvg - prevMonthAvg) * 100.0) / 100.0;
        long   prevMonthTotalDiff     = newThisMonth - prevMonthNewCount;

        return new AgencyReviewListResponse.Stats(
                Math.round(avgRating * 100.0) / 100.0,
                totalCount,
                fiveStarRate,
                newThisMonth,
                prevMonthAvgRatingDiff,
                prevMonthTotalDiff,
                prevMonthTotalDiff  // prevMonthNewDiff: 프론트 스펙 필드, prevMonthTotalDiff와 동일값
        );
    }

    /**
     * 날짜 문자열을 LocalDateTime 으로 변환
     * - isEndDate=true 이면 해당 날짜 포함을 위해 다음날 00:00 으로 변환
     * - null 이면 null 반환 (조건 없음)
     */
    private LocalDateTime parseDate(String dateStr, boolean isEndDate) {
        if (dateStr == null || dateStr.isBlank()) return null;
        try {
            LocalDate date = LocalDate.parse(dateStr, DATE_FORMATTER);
            return isEndDate ? date.plusDays(1).atStartOfDay() : date.atStartOfDay();
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException("날짜 형식이 올바르지 않습니다. (yyyy-MM-dd): " + dateStr);
        }
    }
}
