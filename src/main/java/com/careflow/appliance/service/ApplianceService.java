package com.careflow.appliance.service;

import com.careflow.appliance.dto.ApplianceCreateRequest;
import com.careflow.appliance.dto.ApplianceResponse;
import com.careflow.appliance.dto.HealthCertificateResponse;
import com.careflow.appliance.entity.Appliance;
import com.careflow.appliance.entity.HealthCertificate;
import com.careflow.appliance.repository.ApplianceCategoryRepository;
import com.careflow.appliance.repository.ApplianceRepository;
import com.careflow.appliance.entity.ApplianceCategory;
import com.careflow.appliance.repository.HealthCertificateRepository;
import com.careflow.report.domain.entity.WorkReport;
import com.careflow.report.domain.entity.WorkReportPart;
import com.careflow.report.domain.enums.PartImportance;
import com.careflow.report.domain.policy.HealthScoreCalculator;
import com.careflow.report.repository.WorkReportRepository;
import com.careflow.user.entity.User;
import com.careflow.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ApplianceService {

    private final ApplianceRepository applianceRepository;
    private final UserRepository userRepository;
    private final ApplianceCategoryRepository categoryRepository;
    private final HealthCertificateRepository healthCertificateRepository;
    private final WorkReportRepository workReportRepository;
    /**
     * 가전제품 등록
     * 1) 사용자 존재 여부 확인
     * 2) 카테고리 존재 여부 확인 (소분류 depth=2만 허용)
     * 3) Appliance 엔티티 생성 후 저장
     */
    @Transactional
    public ApplianceResponse registerAppliance(Long userId, ApplianceCreateRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new NoSuchElementException("존재하지 않는 사용자입니다."));

        // 카테고리 검증: 소분류(depth=2)만 가전 카테고리로 허용
        ApplianceCategory category = categoryRepository.findById(request.getCategoryId())
                .orElseThrow(() -> new NoSuchElementException("존재하지 않는 가전 카테고리입니다."));
        if (category.getDepth() != 2) {
            throw new IllegalArgumentException("가전 카테고리는 소분류(depth=2)만 선택 가능합니다.");
        }

        Appliance appliance = Appliance.create(
                user,
                category,
                request.getBrand(),
                request.getModelName(),
                request.getSerialNumber(),
                request.getPurchaseDate(),
                request.getWarrantyEndDate(),
                request.getRegisterMethod()
        );

        return ApplianceResponse.from(applianceRepository.save(appliance));
    }

    /**
     * 내 가전제품 목록 조회 (논리 삭제된 항목 제외, 최신순)
     */
    public List<ApplianceResponse> getMyAppliances(Long userId) {
        return applianceRepository.findByUser_IdAndDeletedAtIsNullOrderByCreatedAtDesc(userId)
                .stream()
                .map(ApplianceResponse::from)
                .collect(Collectors.toList());
    }

    /**
     * 가전제품 상세 조회
     * 본인 소유 가전인지 확인 후 반환
     */
    public ApplianceResponse getApplianceDetail(Long userId, Long applianceId) throws IllegalAccessException {
        Appliance appliance = applianceRepository.findByIdAndDeletedAtIsNull(applianceId)
                .orElseThrow(() -> new NoSuchElementException("존재하지 않는 가전제품입니다."));

        if (!appliance.getUser().getId().equals(userId)) {
            throw new IllegalAccessException("본인 소유의 가전제품만 조회할 수 있습니다.");
        }

        return ApplianceResponse.from(appliance);
    }

    /**
     * 가전제품 논리 삭제
     * 본인 소유 가전인지 확인 후 deletedAt 세팅
     */
    @Transactional
    public void deleteAppliance(Long userId, Long applianceId) throws IllegalAccessException {
        Appliance appliance = applianceRepository.findByIdAndDeletedAtIsNull(applianceId)
                .orElseThrow(() -> new NoSuchElementException("존재하지 않는 가전제품입니다."));

        if (!appliance.getUser().getId().equals(userId)) {
            throw new IllegalAccessException("본인 소유의 가전제품만 삭제할 수 있습니다.");
        }

        appliance.delete();
    }

    @Transactional(readOnly = true)
    public HealthCertificateResponse getHealthCertificate(Long userId, String role, Long applianceId) throws IllegalAccessException {

        // 1. 가전제품 조회 및 권한 검증
        Appliance appliance = applianceRepository.findByIdAndDeletedAtIsNull(applianceId)
                .orElseThrow(() -> new NoSuchElementException("존재하지 않는 가전제품입니다."));

        if ("CUSTOMER".equals(role) && !appliance.getUser().getId().equals(userId)) {
            throw new IllegalAccessException("본인 소유의 가전제품 진단서만 조회할 수 있습니다.");
        }

        // 2. 건강 진단서 원본 데이터 조회
        HealthCertificate cert = healthCertificateRepository.findByAppliance_Id(applianceId)
                .orElseThrow(() -> new NoSuchElementException("아직 발급된 건강 진단서가 없습니다. (수리 이력 없음)"));

        // 3. 해당 가전의 과거 작업 보고서 목록을 최신순(DESC)으로 모두 가져옴
        // (N+1 방지를 위해 Repository에서 FETCH JOIN 적용 권장)
        List<WorkReport> reports = workReportRepository.findByApplianceIdOrderBySubmittedAtDesc(applianceId);

        // 🌟 수정: 사용기간/최근수리 경과는 "인증서 갱신 시점"이 아니라 "현재 시점" 기준으로 계산해야
        // 시간이 지날수록 점수가 실제로 갱신됨 (cert.getUpdatedAt()은 마지막 수리 직후 시각이라 항상 경과=0이 되는 버그였음)
        LocalDateTime now = LocalDateTime.now();

        int axis1 = HealthScoreCalculator.calculateRepairCountScore(cert.getRepairCount());
        int axis2 = HealthScoreCalculator.calculateUsagePeriodScore(appliance.getPurchaseDate(), now);

        // 🌟 수정: 최신 보고서 1건이 아니라 전체 수리 이력을 훑어 가장 심각한(worst) 부품 중요도를 찾음
        // (syncHealthCertificate()의 저장 로직과 동일하게 맞춤)
        PartImportance maxImportance = null;
        for (WorkReport report : reports) {
            for (WorkReportPart part : report.getParts()) {
                PartImportance currentImportance = part.getRepairPart().getImportance();
                if (maxImportance == null || currentImportance.getSeverity() < maxImportance.getSeverity()) {
                    maxImportance = currentImportance;
                }
            }
        }
        int axis3 = HealthScoreCalculator.calculatePartImportanceScore(maxImportance);

        // 🌟 수정: 두 번째로 최근인 보고서가 아니라 가장 최근 보고서의 제출일을 기준으로 계산
        LocalDateTime prevRepairedAt = reports.isEmpty() ? null : reports.get(0).getSubmittedAt();
        int axis4 = HealthScoreCalculator.calculateLastRepairedScore(prevRepairedAt, now);

        // 🌟 Condition 텍스트 추출
        String cond1 = getRepairCountCondition(cert.getRepairCount());
        String cond2 = getUsagePeriodCondition(appliance.getPurchaseDate(), now);
        String cond3 = getPartImportanceCondition(maxImportance);
        String cond4 = getLastRepairedCondition(prevRepairedAt, now);

        return HealthCertificateResponse.builder()
                .certId(cert.getCertId())
                .applianceId(appliance.getId())
                .grade(cert.getGrade())
                .score(cert.getScore())
                .isCertified(cert.isCertified())
                .issuedAt(cert.getIssuedAt())
                .updatedAt(cert.getUpdatedAt())
                .repairCountScore(axis1)
                .usagePeriodScore(axis2)
                .partImportanceScore(axis3)
                .lastRepairedScore(axis4)
                // 🌟 추가된 필드 매핑
                .repairCountCondition(cond1)
                .usagePeriodCondition(cond2)
                .partImportanceCondition(cond3)
                .lastRepairedCondition(cond4)
                .build();
    }

    // --- 건강진단서 프론트 라벨 변환 헬퍼 ---
    private String getRepairCountCondition(int count) {
        if (count == 0) return "수리 이력 없음";
        if (count >= 4) return "4회 이상";
        return count + "회";
    }

    private String getUsagePeriodCondition(LocalDate purchaseDate, LocalDateTime asOf) {
        if (purchaseDate == null) return "알 수 없음";
        long years = ChronoUnit.YEARS.between(purchaseDate, asOf.toLocalDate());
        if (years < 1) return "1년 미만";
        if (years < 3) return "1년 이상 ~ 3년 미만";
        if (years < 5) return "3년 이상 ~ 5년 미만";
        if (years < 8) return "5년 이상 ~ 8년 미만";
        return "8년 이상";
    }

    private String getPartImportanceCondition(PartImportance importance) {
        if (importance == null) return "부품 교체 없음";
        return importance.name() + " 부품 교체";
    }

    private String getLastRepairedCondition(LocalDateTime lastRepaired, LocalDateTime asOf) {
        if (lastRepaired == null) return "수리 이력 없음";
        long months = ChronoUnit.MONTHS.between(lastRepaired, asOf);
        if (months >= 24) return "2년 이전";
        if (months >= 12) return "1년 이상 ~ 2년 미만";
        if (months >= 6) return "6개월 이상 ~ 1년 미만";
        return "6개월 이내";
    }
}
