package com.careflow.settlement.service;

import com.careflow.review.repository.ReviewRepository;
import com.careflow.settlement.dto.EngineerPerformanceItem;
import com.careflow.settlement.dto.EngineerPerformanceResponse;
import com.careflow.settlement.dto.EngineerSettlementListResponse;
import com.careflow.settlement.dto.EngineerSettlementSummary;
import com.careflow.settlement.dto.MonthlySummaryResponse;
import com.careflow.settlement.entity.Settlement;
import com.careflow.settlement.repository.SettlementRepository;
import com.careflow.settlement.repository.SettlementRepository.MonthlySummaryProjection;
import com.careflow.user.entity.User;
import com.careflow.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class SettlementService {

    private final SettlementRepository settlementRepository;
    private final ReviewRepository reviewRepository;
    private final UserRepository userRepository;
    private final SettlementCsvGenerator csvGenerator;

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ISO_LOCAL_DATE;

    /**
     * 기사별 정산 내역 목록 조회 — GET /api/agency/settlements/engineers/performance
     *
     * 동적 필터(status / keyword / settlementId / dateFrom / dateTo)를 지원하며,
     * 결과를 페이징하여 반환한다.
     * year + month 가 기본 조회 범위(paidAt 기준)이며,
     * dateFrom / dateTo 로 범위를 추가로 좁힐 수 있다.
     *
     * @param requestUserId 요청 대행사 담당자 user_id (JWT에서 추출)
     * @param year          조회 연도
     * @param month         조회 월 (1~12)
     * @param status        상태 필터 (null = 전체)
     * @param keyword       기사명 부분 일치 (null = 미적용)
     * @param settlementId  정산 ID 정확히 일치 (null = 미적용)
     * @param dateFrom      날짜 범위 시작 문자열 yyyy-MM-dd (null = 해당 월 1일)
     * @param dateTo        날짜 범위 종료 문자열 yyyy-MM-dd (null = 해당 월 말일)
     * @param pageable      페이징 정보
     */
    @Transactional(readOnly = true)
    public EngineerSettlementListResponse getSettlementList(
            Long requestUserId, int year, int month,
            String status, String keyword, Long settlementId,
            String dateFrom, String dateTo,
            Pageable pageable) {

        validateMonth(month);
        Long agencyId = resolveAgencyId(requestUserId);

        // 기본 범위: 해당 월 전체 (paidAt 기준)
        LocalDateTime monthStart = LocalDate.of(year, month, 1).atStartOfDay();
        LocalDateTime monthEnd   = monthStart.plusMonths(1);

        // dateFrom / dateTo 가 제공된 경우 기본 범위 내에서 좁힘 (형식 오류 시 즉시 400)
        LocalDateTime from = dateFrom != null ? parseDate(dateFrom, false) : monthStart;
        LocalDateTime to   = dateTo   != null ? parseDate(dateTo, true)   : monthEnd;

        // 빈 문자열 keyword는 null 처리 (필터 미적용)
        String effectiveKeyword = (keyword != null && keyword.isBlank()) ? null : keyword;

        Page<Settlement> page = settlementRepository.findSettlementListByAgency(
                agencyId, from, to, status, effectiveKeyword, settlementId, pageable);

        return EngineerSettlementListResponse.of(year, month, page);
    }

    /**
     * 기사별 실적 리포트 조회 (월 단위) — CSV 다운로드 내부용
     *
     * @param requestUserId 요청한 대행사 담당자 user_id (JWT에서 추출)
     * @param year          조회 연도
     * @param month         조회 월 (1~12)
     */
    @Transactional(readOnly = true)
    public EngineerPerformanceResponse getEngineerPerformance(Long requestUserId, int year, int month) {
        // month 유효성 검사 — GlobalExceptionHandler 가 400으로 변환
        validateMonth(month);

        // JWT userId 로 소속 대행사 조회
        Long agencyId = resolveAgencyId(requestUserId);

        // 조회 기간 설정 (해당 월 1일 00:00:00 ~ 다음 달 1일 00:00:00)
        LocalDateTime from = LocalDate.of(year, month, 1).atStartOfDay();
        LocalDateTime to   = from.plusMonths(1);

        // settlements.paid_at 기준으로 기사별 완료 건수 / 수령액 집계
        List<EngineerSettlementSummary> summaries =
                settlementRepository.findEngineerPerformance(agencyId, from, to);

        if (summaries.isEmpty()) {
            return EngineerPerformanceResponse.builder()
                    .year(year).month(month).engineers(List.of()).build();
        }

        // 기사 ID 목록 추출 후 해당 월 평점 일괄 조회 (쿼리 N+1 방지)
        List<Long> engineerIds = summaries.stream()
                .map(EngineerSettlementSummary::getEngineerId)
                .toList();

        // engineer_id → avgRating 맵 구성 (리뷰 없는 기사는 맵에 미포함 → null 처리)
        Map<Long, Double> ratingMap = reviewRepository
                .findAvgRatingByEngineers(engineerIds, from, to)
                .stream()
                .collect(Collectors.toMap(
                        r -> r.getEngineerId(),
                        r -> roundToTwo(r.getAvgRating())
                ));

        // 집계 결과 + 평점 결합하여 응답 DTO 생성
        List<EngineerPerformanceItem> items = summaries.stream()
                .map(s -> EngineerPerformanceItem.builder()
                        .engineerId(s.getEngineerId())
                        .engineerName(s.getEngineerName())
                        .completedCount(s.getCompletedCount())
                        .avgRating(ratingMap.get(s.getEngineerId())) // 리뷰 없으면 null
                        .totalEarning(s.getTotalEarning())
                        .build())
                .toList();

        return EngineerPerformanceResponse.builder()
                .year(year).month(month).engineers(items).build();
    }

    /**
     * 대행사 월별 정산 합산 내역 조회
     *
     * @param requestUserId 요청한 대행사 담당자 user_id (JWT에서 추출)
     * @param year          조회 연도
     * @param month         조회 월 (1~12)
     */
    @Transactional(readOnly = true)
    public MonthlySummaryResponse getMonthlySummary(Long requestUserId, int year, int month) {
        validateMonth(month);

        Long agencyId = resolveAgencyId(requestUserId);

        LocalDateTime from = LocalDate.of(year, month, 1).atStartOfDay();
        LocalDateTime to   = from.plusMonths(1);

        // DB 레벨 합산 집계 — 애플리케이션 레벨 루프 집계 금지
        MonthlySummaryProjection projection =
                settlementRepository.findMonthlySummary(agencyId, from, to);

        // 데이터 없는 월도 0으로 채워 정상 반환 (404 아님)
        // 각 상태별 금액은 CASE WHEN 집계 결과를 그대로 매핑 (UI 통계카드 대응)
        return MonthlySummaryResponse.builder()
                .year(year)
                .month(month)
                .totalCount(orZero(projection.getTotalCount()))
                .totalGrossAmount(orZero(projection.getTotalGrossAmount()))
                .paidAmount(orZero(projection.getPaidAmount()))
                .pendingAmount(orZero(projection.getPendingAmount()))
                .disputedAmount(orZero(projection.getDisputedAmount()))
                .totalPlatformFee(orZero(projection.getTotalPlatformFee()))
                .totalAgencyFee(orZero(projection.getTotalAgencyFee()))
                .totalEngineerPayout(orZero(projection.getTotalEngineerPayout()))
                .build();
    }

    /**
     * 월별 정산 리포트 CSV byte[] 생성
     * - 기사별 실적 + 합산 내역을 하나의 파일로 결합
     *
     * @param requestUserId 요청한 대행사 담당자 user_id (JWT에서 추출)
     * @param year          조회 연도
     * @param month         조회 월 (1~12)
     */
    @Transactional(readOnly = true)
    public byte[] generateMonthlyCsv(Long requestUserId, int year, int month) {
        // 기존 서비스 메서드 재사용으로 중복 쿼리 방지
        EngineerPerformanceResponse performance = getEngineerPerformance(requestUserId, year, month);
        MonthlySummaryResponse summary = getMonthlySummary(requestUserId, year, month);

        return csvGenerator.generate(performance.getEngineers(), summary);
    }

    // ─── 내부 헬퍼 ───────────────────────────────────────────────────────────

    /**
     * JWT userId 로 소속 대행사 ID 조회
     * 대행사 정보가 없으면 NoSuchElementException (→ 404)
     */
    private Long resolveAgencyId(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new NoSuchElementException("유저 정보가 존재하지 않습니다."));
        if (user.getAgency() == null) {
            throw new NoSuchElementException("소속 대행사 정보가 없습니다.");
        }
        return user.getAgency().getId();
    }

    /** month 1~12 범위 검증 */
    private void validateMonth(int month) {
        if (month < 1 || month > 12) {
            throw new IllegalArgumentException("월은 1~12 사이여야 합니다.");
        }
    }

    /**
     * 날짜 문자열 → LocalDateTime 변환
     * isEndDate=true 이면 dateTo 포함을 위해 +1일 00:00 으로 변환
     */
    private LocalDateTime parseDate(String dateStr, boolean isEndDate) {
        try {
            LocalDate date = LocalDate.parse(dateStr, DATE_FMT);
            return isEndDate ? date.plusDays(1).atStartOfDay() : date.atStartOfDay();
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException("날짜 형식이 올바르지 않습니다. (yyyy-MM-dd): " + dateStr);
        }
    }

    /** null 안전 long 변환 — projection 집계값이 null 일 때 0으로 대체 */
    private long orZero(Long value) {
        return value != null ? value : 0L;
    }

    /** Double 소수점 둘째 자리 반올림 */
    private double roundToTwo(Double value) {
        if (value == null) return 0.0;
        return Math.round(value * 100.0) / 100.0;
    }
}
