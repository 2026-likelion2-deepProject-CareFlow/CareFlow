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

        int axis1 = HealthScoreCalculator.calculateRepairCountScore(cert.getRepairCount());
        int axis2 = HealthScoreCalculator.calculateUsagePeriodScore(appliance.getPurchaseDate(), cert.getUpdatedAt());

        int axis3 = 25;
        if (!reports.isEmpty() && !reports.get(0).getParts().isEmpty()) {
            PartImportance maxImportance = null;
            for (WorkReportPart part : reports.get(0).getParts()) {
                PartImportance currentImportance = part.getRepairPart().getImportance();
                if (maxImportance == null || currentImportance.getSeverity() < maxImportance.getSeverity()) {
                    maxImportance = currentImportance;
                }
            }
            axis3 = HealthScoreCalculator.calculatePartImportanceScore(maxImportance);
        }

        LocalDateTime prevRepairedAt = reports.size() > 1 ? reports.get(1).getSubmittedAt() : null;
        int axis4 = HealthScoreCalculator.calculateLastRepairedScore(prevRepairedAt, cert.getUpdatedAt());

        // 5. 응답 DTO 조립
        return HealthCertificateResponse.builder()
                .certId(cert.getCertId())
                .applianceId(appliance.getId())
                .grade(cert.getGrade())
                .score(cert.getScore())
                .isCertified(cert.isCertified())
                .issuedAt(cert.getIssuedAt())
                .updatedAt(cert.getUpdatedAt())
                .repairCountScore(axis1)         // 역추산된 1축
                .usagePeriodScore(axis2)         // 역추산된 2축
                .partImportanceScore(axis3)      // 역추산된 3축
                .lastRepairedScore(axis4)        // 역추산된 4축
                .build();
    }

}
