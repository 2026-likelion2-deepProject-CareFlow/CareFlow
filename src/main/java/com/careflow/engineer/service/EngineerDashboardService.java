package com.careflow.engineer.service;

import com.careflow.as_status_log.entity.AsStatusLog;
import com.careflow.as_status_log.repository.AsStatusLogRepository;
import com.careflow.assignment.entity.AsAssignment;
import com.careflow.assignment.repository.AsAssignmentRepository;
import com.careflow.bank_account.entity.BankAccount;
import com.careflow.bank_account.repository.BankAccountRepository;
import com.careflow.engineer.domain.entity.EngineerProfile;
import com.careflow.engineer.dto.EngineerDashboardResponse;
import com.careflow.settlement.dto.EngineerSettlementSummaryResponse;
import com.careflow.engineer.repository.EngineerProfileRepository;
import com.careflow.notification.repository.NotificationRepository;
import com.careflow.settlement.repository.SettlementRepository;
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

    // 1. 대시보드 API (오늘의 실적 및 상태)
    public EngineerDashboardResponse getDashboardData(Long engineerId) {
        EngineerProfile profile = engineerProfileRepository.findByUser_Id(engineerId)
                .orElseThrow(() -> new IllegalArgumentException("기사 프로필을 찾을 수 없습니다."));

        LocalDate today = LocalDate.now();
        List<AsAssignment> todayAssignments = asAssignmentRepository.findTodayAssignments(engineerId, today);

        int expectedCount = 0;
        int completedCount = 0;
        String currentStatus = "WAITING";

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

        // 🌟 방어 로직 2: 상태값 파악 시 리스트 인덱스 안전 처리
        for (AsAssignment a : todayAssignments) {
            if ("COMPLETED".equals(a.getStatus())) {
                completedCount++;
            } else if ("WAITING".equals(a.getStatus()) || "ACCEPTED".equals(a.getStatus())) {
                expectedCount++;
                if ("ACCEPTED".equals(a.getStatus())) {
                    List<AsStatusLog> logs = asStatusLogRepository.findByAsRequest_IdOrderByCreatedAtAsc(a.getAsRequest().getId());
                    if (logs != null && !logs.isEmpty()) {
                        currentStatus = logs.get(logs.size() - 1).getToStatus();
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
                .todaySchedules(schedules)
                .notices(notices)
                .build();
    }

    // 2. 실적/정산 요약 API
    public EngineerSettlementSummaryResponse getSettlementSummary(Long engineerId, LocalDate dateFrom, LocalDate dateTo) {
        EngineerProfile profile = engineerProfileRepository.findByUser_Id(engineerId).orElseThrow();

        LocalDate start = dateFrom != null ? dateFrom : LocalDate.now().withDayOfMonth(1);
        LocalDate end = dateTo != null ? dateTo : start.plusMonths(1).minusDays(1);

        List<AsAssignment> completedAssignments = asAssignmentRepository.findCompletedAssignmentsWithDetails(engineerId, start, end);

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
                    String brand = a.getAsRequest().getAppliance() != null ? a.getAsRequest().getAppliance().getBrand() : "";
                    String model = a.getAsRequest().getAppliance() != null ? a.getAsRequest().getAppliance().getModelName() : "";

                    return EngineerSettlementSummaryResponse.PerformanceItem.builder()
                            .requestId(reqId)
                            .workDate(workDate)
                            .customerName(customerName)
                            .productName((brand + " " + model).trim())
                            .brand(brand)
                            .grossAmount(a.getAsRequest().getWorkReport() != null ? a.getAsRequest().getWorkReport().getFinalAmount() : 0)
                            .diagnosisResult(a.getAsRequest().getWorkReport() != null && a.getAsRequest().getWorkReport().getDiagnosisResult() != null ? a.getAsRequest().getWorkReport().getDiagnosisResult().name() : "NORMAL")
                            .rating(a.getAsRequest().getReview() != null && a.getAsRequest().getReview().getRating() != null ? a.getAsRequest().getReview().getRating() : 0.0)
                            .build();
                }).toList();

        // 4. 정산 요약 및 계좌 (v21 명세 완벽 반영)
        Integer sumNet = settlementRepository.sumExpectedEarningByEngineerIdAndDate(engineerId, start.atStartOfDay(), end.plusDays(1).atStartOfDay());
        BankAccount bankAccount = bankAccountRepository.findByEngineerId(engineerId).orElse(null);

        // 🌟 방어 로직: 프로필 평점 Null 처리
        double avgRating = profile.getAvgRating() != null ? profile.getAvgRating().doubleValue() : 0.0;

        EngineerSettlementSummaryResponse.SettlementSummary settlementSummary = EngineerSettlementSummaryResponse.SettlementSummary.builder()
                .grossAmount(totalGross)
                .platformFee((int)(totalGross * 0.1)) // 플랫폼 수수료 10% 가정
                .agencyFee((int)(totalGross * 0.05))  // 대행사 수수료 5% 가정
                .engineerNetAmount(sumNet != null ? sumNet : 0)
                .paidAt(end.plusDays(5).format(DateTimeFormatter.ofPattern("yyyy.MM.dd")) + " 예정")
                .bankName(bankAccount != null && bankAccount.getBankName() != null ? bankAccount.getBankName() : "미등록")
                .accountNumber(bankAccount != null && bankAccount.getAccountNumber() != null ? bankAccount.getAccountNumber() : "계좌 미등록")
                .build();

        return EngineerSettlementSummaryResponse.builder()
                .totalCompletedCount(totalCompleted)
                .totalGrossAmount(totalGross)
                .avgRating(avgRating)
                .customerSatisfaction(customerSatisfaction)
                .rejectedCount(rejectedCount)
                .dailyTrends(dailyTrends)
                .brandDistributions(brandDists)
                .statusDistributions(statusDists)
                .performanceList(performanceList)
                .settlementSummary(settlementSummary)
                .build();
    }
}