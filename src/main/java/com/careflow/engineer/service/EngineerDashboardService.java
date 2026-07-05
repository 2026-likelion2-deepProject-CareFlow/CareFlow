package com.careflow.engineer.service;

import com.careflow.as_status_log.entity.AsStatusLog;
import com.careflow.as_status_log.repository.AsStatusLogRepository;
import com.careflow.assignment.entity.AsAssignment;
import com.careflow.assignment.repository.AsAssignmentRepository;
import com.careflow.bank_account.entity.BankAccount;
import com.careflow.bank_account.repository.BankAccountRepository;
import com.careflow.engineer.domain.entity.EngineerProfile;
import com.careflow.engineer.dto.EngineerDashboardResponse;
import com.careflow.common.enums.DiagnosisResult;
import com.careflow.settlement.dto.EngineerSettlementSummaryResponse;
import com.careflow.engineer.repository.EngineerProfileRepository;
import com.careflow.notification.repository.NotificationRepository;
import com.careflow.settlement.repository.SettlementRepository;
import com.careflow.review.repository.ReviewRepository;
import com.careflow.common.enums.AsStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class EngineerDashboardService {

    private final AsAssignmentRepository asAssignmentRepository;
    private final EngineerProfileRepository engineerProfileRepository;
    private final SettlementRepository settlementRepository;
    private final NotificationRepository notificationRepository;
    private final AsStatusLogRepository asStatusLogRepository;
    private final BankAccountRepository bankAccountRepository;
    private final ReviewRepository reviewRepository; // [추가] 기간별 평점 비교(findAvgRatingByEngineers) 재사용

    // 1. 대시보드 API (오늘의 실적 및 상태)
    public EngineerDashboardResponse getDashboardData(Long engineerId) {
        EngineerProfile profile = engineerProfileRepository.findByUser_Id(engineerId)
                .orElseThrow(() -> new IllegalArgumentException("기사 프로필을 찾을 수 없습니다."));

        LocalDate today = LocalDate.now();
        List<AsAssignment> todayAssignments = asAssignmentRepository.findTodayAssignments(engineerId, today);

        int expectedCount = 0;
        int completedCount = 0;
        String currentStatus = "WAITING";
        Long currentRequestId = null;       // [추가] 진행 중 건의 requestId
        boolean currentIsActive = false;    // [추가] 실제 진행 중(출발~수리중)인 건을 이미 확정했는지

        // 🌟 방어 로직 1: 리스트 매핑 시 연관 객체 Null Safe 처리
        List<EngineerDashboardResponse.TodayScheduleDto> schedules = todayAssignments.stream().map(a -> {

            // 1) 주소 안전 처리
            String regionName = (a.getAsRequest().getCustomer() != null && a.getAsRequest().getCustomer().getRegionId() != null)
                    ? a.getAsRequest().getCustomer().getRegionId().getName() + " " : "";
            String fullAddress = regionName + (a.getAsRequest().getVisitAddressDetail() != null ? a.getAsRequest().getVisitAddressDetail() : "");

            // 2) 가전 및 카테고리 안전 처리
            String brand = a.getAsRequest().getAppliance() != null ? a.getAsRequest().getAppliance().getBrand() : "브랜드 미상";
            String categoryName = (a.getAsRequest().getAppliance() != null && a.getAsRequest().getAppliance().getCategory() != null)
                    ? a.getAsRequest().getAppliance().getCategory().getName() : "";
            String modelNo = a.getAsRequest().getAppliance() != null ? a.getAsRequest().getAppliance().getModelName() : "모델 미상";

            // 3) 고객 정보 안전 처리
            String customerName = a.getAsRequest().getCustomer() != null ? a.getAsRequest().getCustomer().getName() : "고객 미상";
            String customerPhone = a.getAsRequest().getCustomer() != null ? a.getAsRequest().getCustomer().getPhone() : "연락처 미상";

            // 4) 증상 안전 처리
            String symptomName = a.getAsRequest().getSymptom() != null ? a.getAsRequest().getSymptom().getSymptomName() : "증상 미등록";
            String symptomDesc = a.getAsRequest().getSymptomDesc() != null ? " - " + a.getAsRequest().getSymptomDesc() : "";

            return EngineerDashboardResponse.TodayScheduleDto.builder()
                    .assignmentId(a.getId())
                    .requestId(a.getAsRequest().getId()) // [추가] 상태변경 API가 요구하는 requestId (assignmentId 아님)
                    .time(a.getAsRequest().getScheduledTime() != null ? a.getAsRequest().getScheduledTime() : "시간 미정")
                    .status(a.getStatus())
                    .productName((brand + " " + categoryName).trim())
                    .modelNo(modelNo)
                    .customerName(customerName)
                    .customerPhone(customerPhone)
                    .address(fullAddress.trim())
                    .symptom(symptomName + symptomDesc)
                    .build();
        }).toList();

        // 🌟 방어 로직 2 + [수정] 진행 중 건 선택 로직
        //  - 완료/예정 건수 집계는 기존과 동일.
        //  - currentWorkStatus/currentRequestId 는 "실제 진행 중(최신 status log 가 WAITING·COMPLETED 가 아닌)" 건을 우선 선택.
        //    ACCEPTED 배정이 여러 건이면 마지막으로 덮어쓰던 기존 버그를 막고, 진행 중 건을 발견하면 그걸로 확정한다.
        //    진행 중 건이 없으면 첫 ACCEPTED 건(대기 상태)을 가리키고, ACCEPTED 자체가 없으면 null(WAITING) 로 남는다.
        for (AsAssignment a : todayAssignments) {
            String assignStatus = a.getStatus();
            if ("COMPLETED".equals(assignStatus)) {
                completedCount++;
            } else if ("WAITING".equals(assignStatus) || "ACCEPTED".equals(assignStatus)) {
                expectedCount++;
                if ("ACCEPTED".equals(assignStatus) && !currentIsActive) {
                    List<AsStatusLog> logs = asStatusLogRepository.findByAsRequest_IdOrderByCreatedAtAsc(a.getAsRequest().getId());
                    String latest = (logs != null && !logs.isEmpty())
                            ? logs.get(logs.size() - 1).getToStatus()
                            : "WAITING";
                    boolean active = !"WAITING".equals(latest) && !"COMPLETED".equals(latest); // 출발/도착/작업중
                    if (currentRequestId == null || active) {
                        currentRequestId = a.getAsRequest().getId();
                        currentStatus = latest;
                        currentIsActive = active; // 진행 중 건을 잡았으면 이후 ACCEPTED 는 무시
                    }
                }
            }
        }

        LocalDateTime startOfMonth = today.withDayOfMonth(1).atStartOfDay();
        LocalDateTime endOfMonth = startOfMonth.plusMonths(1);
        Integer expectedEarning = settlementRepository.sumExpectedEarningByEngineerIdAndDate(engineerId, startOfMonth, endOfMonth);

        List<EngineerDashboardResponse.NoticeDto> notices = notificationRepository.findByUser_IdOrderByCreatedAtDesc(engineerId, PageRequest.of(0, 3))
                .stream().map(n -> EngineerDashboardResponse.NoticeDto.builder()
                        .id(n.getId())
                        .text(n.getTitle() != null ? n.getTitle() : "알림")
                        .date(n.getCreatedAt() != null ? n.getCreatedAt().format(DateTimeFormatter.ofPattern("yyyy.MM.dd")) : "")
                        .build()
                ).toList();

        // 🌟 방어 로직 3: 프로필 객체 속성들(enum, bigdecimal) Null Safe 처리
        String engineerName = profile.getUser() != null ? profile.getUser().getName() : "수리기사";
        String skillLevel = profile.getSkillLevel() != null ? profile.getSkillLevel().name() : "BEGINNER";
        BigDecimal avgRating = profile.getAvgRating() != null ? profile.getAvgRating() : BigDecimal.ZERO;

        return EngineerDashboardResponse.builder()
                .engineerName(engineerName)
                .skillLevel(skillLevel)
                .isLmsCompleted(profile.isLmsCompleted())
                .avgRating(avgRating)
                .totalReviews(profile.getTotalReviews())
                .profileImageUrl(profile.getProfileImageUrl())
                .todayExpectedCount(expectedCount)
                .todayCompletedCount(completedCount)
                .thisMonthExpectedEarning(expectedEarning != null ? expectedEarning : 0)
                .currentWorkStatus(currentStatus)
                .currentRequestId(currentRequestId) // [추가] 진행 중 건의 requestId (없으면 null)
                .todaySchedules(schedules)
                .notices(notices)
                .build();
    }

    // 2. 실적/정산 요약 API
    public EngineerSettlementSummaryResponse getSettlementSummary(Long engineerId, LocalDate dateFrom, LocalDate dateTo, String brand, String status) {
        EngineerProfile profile = engineerProfileRepository.findByUser_Id(engineerId).orElseThrow();

        LocalDate start = dateFrom != null ? dateFrom : LocalDate.now().withDayOfMonth(1);
        LocalDate end = dateTo != null ? dateTo : start.plusMonths(1).minusDays(1);

        // 🌟 String으로 들어온 상태값을 Enum으로 안전하게 변환
        DiagnosisResult statusEnum = null;
        if (status != null && !status.isBlank()) {
            try {
                statusEnum = DiagnosisResult.valueOf(status.toUpperCase());
            } catch (IllegalArgumentException e) {
                // 프론트에서 잘못된 값이 오면 무시 (필터 미적용)
                statusEnum = null;
            }
        }

        // 빈 문자열 방어
        String filterBrand = (brand != null && !brand.isBlank()) ? brand : null;

        // 🌟 수정된 Repository 호출 (파라미터 추가)
        List<AsAssignment> completedAssignments = asAssignmentRepository.findCompletedAssignmentsWithDetails(
                engineerId, start, end, filterBrand, statusEnum);

        long rejectedCount = asAssignmentRepository.findByEngineer_IdAndStatus(engineerId, "REJECTED").stream()
                .filter(a -> a.getAssignedAt() != null && !a.getAssignedAt().toLocalDate().isBefore(start) && !a.getAssignedAt().toLocalDate().isAfter(end))
                .count();

        long totalCompleted = completedAssignments.size();
        int totalGross = 0;
        int goodReviews = 0;
        int reviewCount = 0;

        for (AsAssignment a : completedAssignments) {
            if (a.getAsRequest() != null) {
                if (a.getAsRequest().getWorkReport() != null) {
                    totalGross += a.getAsRequest().getWorkReport().getFinalAmount();
                }
                if (a.getAsRequest().getReview() != null && a.getAsRequest().getReview().getRating() != null) {
                    reviewCount++;
                    if (a.getAsRequest().getReview().getRating() >= 4) goodReviews++;
                }
            }
        }
        double customerSatisfaction = reviewCount > 0 ? ((double) goodReviews / reviewCount) * 100 : 0.0;

        // 3-1. 일별 트렌드
        Map<String, Long> dailyMap = completedAssignments.stream()
                .filter(a -> a.getAsRequest() != null && a.getAsRequest().getScheduledDate() != null)
                .collect(Collectors.groupingBy(
                        a -> a.getAsRequest().getScheduledDate().format(DateTimeFormatter.ofPattern("MM.dd")),
                        Collectors.counting()
                ));
        List<EngineerSettlementSummaryResponse.DailyTrend> dailyTrends = dailyMap.entrySet().stream()
                .map(e -> EngineerSettlementSummaryResponse.DailyTrend.builder().date(e.getKey()).count(e.getValue()).build())
                .sorted((d1, d2) -> d1.getDate().compareTo(d2.getDate())).toList();

        // 3-2. 브랜드별 분포
        Map<String, Long> brandMap = completedAssignments.stream()
                .filter(a -> a.getAsRequest() != null && a.getAsRequest().getAppliance() != null)
                .collect(Collectors.groupingBy(a -> a.getAsRequest().getAppliance().getBrand() != null ? a.getAsRequest().getAppliance().getBrand() : "기타", Collectors.counting()));

        List<EngineerSettlementSummaryResponse.BrandDist> brandDists = brandMap.entrySet().stream()
                .map(e -> EngineerSettlementSummaryResponse.BrandDist.builder()
                        .name(e.getKey()).value(e.getValue())
                        .pct(totalCompleted > 0 ? String.format("%.1f%%", (e.getValue() / (double) totalCompleted) * 100) : "0.0%")
                        .color("#818cf8").build()).toList();

        // 3-3. 상태별 분포 매핑
        Map<String, Long> statusMap = completedAssignments.stream()
                .filter(a -> a.getAsRequest() != null && a.getAsRequest().getWorkReport() != null && a.getAsRequest().getWorkReport().getDiagnosisResult() != null)
                .collect(Collectors.groupingBy(a -> {
                    String raw = a.getAsRequest().getWorkReport().getDiagnosisResult().name();
                    if ("NORMAL".equals(raw) || "REPAIRED".equals(raw)) return "정상 완료";
                    if ("UNREPAIRABLE".equals(raw)) return "제방문";
                    return "부분 해결";
                }, Collectors.counting()));

        List<EngineerSettlementSummaryResponse.StatusDist> statusDists = statusMap.entrySet().stream()
                .map(e -> EngineerSettlementSummaryResponse.StatusDist.builder()
                        .name(e.getKey()).value(e.getValue())
                        .pct(totalCompleted > 0 ? String.format("%.1f%%", (e.getValue() / (double) totalCompleted) * 100) : "0.0%")
                        .color("#16a34a").build()).toList();

        // 3-4. 실적 리스트
        List<EngineerSettlementSummaryResponse.PerformanceItem> performanceList = completedAssignments.stream()
                .filter(a -> a.getAsRequest() != null)
                .map(a -> {
                    String reqDateStr = a.getAsRequest().getCreatedAt() != null ? a.getAsRequest().getCreatedAt().format(DateTimeFormatter.ofPattern("yyyyMMdd")) : "00000000";
                    String reqId = String.format("AS-%s-%04d", reqDateStr, a.getAsRequest().getId());

                    String workDate = a.getAsRequest().getScheduledDate() != null ? a.getAsRequest().getScheduledDate().format(DateTimeFormatter.ofPattern("MM.dd")) : "";
                    String customerName = a.getAsRequest().getCustomer() != null ? a.getAsRequest().getCustomer().getName() : "미상";
                    String applianceBrand = a.getAsRequest().getAppliance() != null ? a.getAsRequest().getAppliance().getBrand() : "";
                    String model = a.getAsRequest().getAppliance() != null ? a.getAsRequest().getAppliance().getModelName() : "";

                    return EngineerSettlementSummaryResponse.PerformanceItem.builder()
                            .requestId(reqId)
                            .workDate(workDate)
                            .customerName(customerName)
                            .productName((applianceBrand + " " + model).trim())
                            .brand(applianceBrand)
                            .grossAmount(a.getAsRequest().getWorkReport() != null ? a.getAsRequest().getWorkReport().getFinalAmount() : 0)
                            .diagnosisResult(a.getAsRequest().getWorkReport() != null && a.getAsRequest().getWorkReport().getDiagnosisResult() != null ? a.getAsRequest().getWorkReport().getDiagnosisResult().name() : "NORMAL")
                            .rating(a.getAsRequest().getReview() != null && a.getAsRequest().getReview().getRating() != null ? a.getAsRequest().getReview().getRating() : 0.0)
                            .build();
                }).toList();

        // 4. 정산 요약 및 계좌 (v21 명세 반영)
        LocalDateTime settlementFrom = start.atStartOfDay();
        LocalDateTime settlementTo = end.plusDays(1).atStartOfDay();

        // 🌟 수정 포인트 2: filterBrand, statusEnum 파라미터 함께 넘기기!
        SettlementRepository.MonthlySummaryProjection settlementAgg =
                settlementRepository.findEngineerMonthlySummary(engineerId, settlementFrom, settlementTo, filterBrand, statusEnum);

        int settleGross = (settlementAgg != null && settlementAgg.getTotalGrossAmount() != null) ? settlementAgg.getTotalGrossAmount().intValue() : 0;
        int settlePlatformFee = (settlementAgg != null && settlementAgg.getTotalPlatformFee() != null) ? settlementAgg.getTotalPlatformFee().intValue() : 0;
        int settleAgencyFee = (settlementAgg != null && settlementAgg.getTotalAgencyFee() != null) ? settlementAgg.getTotalAgencyFee().intValue() : 0;
        int settleNet = (settlementAgg != null && settlementAgg.getTotalEngineerPayout() != null) ? settlementAgg.getTotalEngineerPayout().intValue() : 0;

        BankAccount bankAccount = bankAccountRepository.findByEngineerId(engineerId).orElse(null);

        // 🌟 방어 로직: 프로필 평점 Null 처리
        double avgRating = profile.getAvgRating() != null ? profile.getAvgRating().doubleValue() : 0.0;

        EngineerSettlementSummaryResponse.SettlementSummary settlementSummary = EngineerSettlementSummaryResponse.SettlementSummary.builder()
                .grossAmount(settleGross)
                .platformFee(settlePlatformFee)
                .agencyFee(settleAgencyFee)
                .engineerNetAmount(settleNet)
                // ⚠ paidAt: 집계(여러 정산 행)에는 단일 지급일이 없어 표시용 예상치로 유지. 건별 실제 paid_at 은 정산 목록 API(GET /api/engineer/settlements) 참조.
                .paidAt(end.plusDays(5).format(DateTimeFormatter.ofPattern("yyyy.MM.dd")) + " 예정")
                .bankName(bankAccount != null && bankAccount.getBankName() != null ? bankAccount.getBankName() : "미등록")
                .accountNumber(bankAccount != null && bankAccount.getAccountNumber() != null ? bankAccount.getAccountNumber() : "계좌 미등록")
                .build();

        // ── [추가] 작업 건수 세분화 (완료/진행/취소) — 조회 기간(start~end, scheduledDate 기준)
        long inProgressCount = asAssignmentRepository.countByEngineerAndStatusInPeriod(engineerId, "ACCEPTED", start, end);
        long cancelledCount = asAssignmentRepository.countRequestsByEngineerAndRequestStatusInPeriod(engineerId, AsStatus.CANCELLED, start, end);

        // ── [추가] 이번 달 / 지난 달 비교 (조회 기간 필터와 무관하게 항상 '이번 달 vs 전월' — 대행사 리뷰 통계와 동일 패턴)
        java.time.YearMonth thisYm = java.time.YearMonth.now();
        java.time.YearMonth prevYm = thisYm.minusMonths(1);
        LocalDateTime thisMonthStart = thisYm.atDay(1).atStartOfDay();
        LocalDateTime nextMonthStart = thisYm.plusMonths(1).atDay(1).atStartOfDay();
        LocalDateTime prevMonthStart = prevYm.atDay(1).atStartOfDay();

        // 정산 금액(net) 비교 — settlements.engineer_net_amount 합 (createdAt 기준)
        Integer thisNetObj = settlementRepository.sumExpectedEarningByEngineerIdAndDate(engineerId, thisMonthStart, nextMonthStart);
        Integer prevNetObj = settlementRepository.sumExpectedEarningByEngineerIdAndDate(engineerId, prevMonthStart, thisMonthStart);
        int thisMonthNet = thisNetObj != null ? thisNetObj : 0;
        int prevMonthNet = prevNetObj != null ? prevNetObj : 0;

        // 평점 비교 — 해당 월 신규 리뷰 평균 (createdAt 기준). 프로필 누적 평균(avgRating)과는 다른 값.
        List<ReviewRepository.EngineerAvgRating> thisRatingRows = reviewRepository.findAvgRatingByEngineers(List.of(engineerId), thisMonthStart, nextMonthStart);
        List<ReviewRepository.EngineerAvgRating> prevRatingRows = reviewRepository.findAvgRatingByEngineers(List.of(engineerId), prevMonthStart, thisMonthStart);
        double thisMonthAvgRating = (!thisRatingRows.isEmpty() && thisRatingRows.get(0).getAvgRating() != null)
                ? Math.round(thisRatingRows.get(0).getAvgRating() * 100.0) / 100.0 : 0.0;
        double prevMonthAvgRating = (!prevRatingRows.isEmpty() && prevRatingRows.get(0).getAvgRating() != null)
                ? Math.round(prevRatingRows.get(0).getAvgRating() * 100.0) / 100.0 : 0.0;

        // 완료 건수 비교 — scheduledDate 기준 (양끝 포함)
        long thisMonthCompleted = asAssignmentRepository.countByEngineerAndStatusInPeriod(engineerId, "COMPLETED", thisYm.atDay(1), thisYm.atEndOfMonth());
        long prevMonthCompleted = asAssignmentRepository.countByEngineerAndStatusInPeriod(engineerId, "COMPLETED", prevYm.atDay(1), prevYm.atEndOfMonth());

        EngineerSettlementSummaryResponse.MonthlyComparison monthlyComparison =
                EngineerSettlementSummaryResponse.MonthlyComparison.builder()
                        .thisMonthNetAmount(thisMonthNet)
                        .prevMonthNetAmount(prevMonthNet)
                        .netAmountDiff(thisMonthNet - prevMonthNet)
                        .thisMonthAvgRating(thisMonthAvgRating)
                        .prevMonthAvgRating(prevMonthAvgRating)
                        .avgRatingDiff(Math.round((thisMonthAvgRating - prevMonthAvgRating) * 100.0) / 100.0)
                        .thisMonthCompletedCount(thisMonthCompleted)
                        .prevMonthCompletedCount(prevMonthCompleted)
                        .completedCountDiff(thisMonthCompleted - prevMonthCompleted)
                        .build();

        return EngineerSettlementSummaryResponse.builder()
                .totalCompletedCount(totalCompleted)
                .inProgressCount(inProgressCount)
                .cancelledCount(cancelledCount)
                .totalGrossAmount(totalGross)
                .avgRating(avgRating)
                .customerSatisfaction(customerSatisfaction)
                .rejectedCount(rejectedCount)
                .dailyTrends(dailyTrends)
                .brandDistributions(brandDists)
                .statusDistributions(statusDists)
                .performanceList(performanceList)
                .settlementSummary(settlementSummary)
                .monthlyComparison(monthlyComparison)
                .build();
    }
}