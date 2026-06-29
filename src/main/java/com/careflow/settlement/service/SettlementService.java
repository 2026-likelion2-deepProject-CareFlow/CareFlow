package com.careflow.settlement.service;

import com.careflow.review.repository.ReviewRepository;
import com.careflow.settlement.dto.EngineerPerformanceItem;
import com.careflow.settlement.dto.EngineerPerformanceResponse;
import com.careflow.settlement.dto.EngineerSettlementSummary;
import com.careflow.settlement.dto.MonthlySummaryResponse;
import com.careflow.settlement.repository.SettlementRepository;
import com.careflow.settlement.repository.SettlementRepository.MonthlySummaryProjection;
import com.careflow.user.entity.User;
import com.careflow.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
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

    /**
     * 기사별 실적 리포트 조회 (월 단위)
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
        return MonthlySummaryResponse.builder()
                .year(year)
                .month(month)
                .totalCount(projection.getTotalCount() != null ? projection.getTotalCount() : 0L)
                .totalGrossAmount(projection.getTotalGrossAmount() != null ? projection.getTotalGrossAmount() : 0L)
                .totalPlatformFee(projection.getTotalPlatformFee() != null ? projection.getTotalPlatformFee() : 0L)
                .totalAgencyFee(projection.getTotalAgencyFee() != null ? projection.getTotalAgencyFee() : 0L)
                .totalEngineerPayout(projection.getTotalEngineerPayout() != null ? projection.getTotalEngineerPayout() : 0L)
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

    /** Double 소수점 둘째 자리 반올림 */
    private double roundToTwo(Double value) {
        if (value == null) return 0.0;
        return Math.round(value * 100.0) / 100.0;
    }
}
