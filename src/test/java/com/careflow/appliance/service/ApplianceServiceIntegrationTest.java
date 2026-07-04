package com.careflow.appliance.service;

import com.careflow.appliance.dto.HealthCertificateResponse;
import com.careflow.appliance.entity.Appliance;
import com.careflow.appliance.entity.ApplianceCategory;
import com.careflow.appliance.entity.HealthCertificate;
import com.careflow.appliance.repository.ApplianceCategoryRepository;
import com.careflow.appliance.repository.ApplianceRepository;
import com.careflow.appliance.repository.HealthCertificateRepository;
import com.careflow.as_request.entity.AsRequest;
import com.careflow.as_request.repository.AsRequestRepository;
import com.careflow.common.enums.RegisterMethod;
import com.careflow.common.enums.Role;
import com.careflow.part.domain.entity.RepairPart;
import com.careflow.part.repository.RepairPartRepository;
import com.careflow.region.entity.Regions;
import com.careflow.region.repository.RegionRepository;
import com.careflow.report.domain.entity.WorkReport;
import com.careflow.report.domain.entity.WorkReportPart;
import com.careflow.report.domain.enums.DiagnosisResult;
import com.careflow.report.domain.enums.PartImportance;
import com.careflow.report.repository.WorkReportRepository;
import com.careflow.symptom.entity.Symptom;
import com.careflow.symptom.repository.SymptomRepository;
import com.careflow.user.entity.User;
import com.careflow.user.repository.UserRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.annotation.Transactional;

import java.lang.reflect.Constructor;
import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("local")
@Transactional
@Sql(scripts = "/cleanup.sql", executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
@DisplayName("ApplianceService 통합 테스트 (H2 DB 연동)")
class ApplianceServiceIntegrationTest {

    @Autowired private ApplianceService applianceService;
    @Autowired private ApplianceRepository applianceRepository;
    @Autowired private HealthCertificateRepository healthCertificateRepository;
    @Autowired private WorkReportRepository workReportRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private ApplianceCategoryRepository categoryRepository;
    @Autowired private RegionRepository regionRepository;
    @Autowired private SymptomRepository symptomRepository;
    @Autowired private AsRequestRepository asRequestRepository;
    @Autowired private RepairPartRepository repairPartRepository;
    @Autowired private EntityManager em;

    private User testCustomer;
    private Appliance testAppliance;

    @BeforeEach
    void setUp() throws Exception {
        // 1. 기초 데이터 세팅 (고객, 카테고리, 가전)
        testCustomer = userRepository.save(User.builder().email("cust@test.com").name("고객").role(Role.CUSTOMER).build());
        User testEngineer = userRepository.save(User.builder().email("eng@test.com").name("기사").role(Role.ENGINEER).build());

        ApplianceCategory rootCategory = ApplianceCategory.createRoot("대형가전", 1);
        categoryRepository.save(rootCategory);
        ApplianceCategory childCategory = ApplianceCategory.createChild("에어컨", rootCategory, 1);
        categoryRepository.save(childCategory);

        testAppliance = Appliance.create(
                testCustomer, childCategory, "삼성", "테스트모델",
                null, LocalDate.now().minusYears(2), null, RegisterMethod.MANUAL);
        applianceRepository.save(testAppliance);

        // 2. 건강 진단서 세팅 (누적 수리 2회)
        HealthCertificate cert = HealthCertificate.builder().appliance(testAppliance).build();
        ReflectionTestUtils.setField(cert, "repairCount", 2);
        ReflectionTestUtils.setField(cert, "grade", "B");
        ReflectionTestUtils.setField(cert, "score", 75);
        healthCertificateRepository.save(cert);

        // 3. 수리를 위한 공통 데이터 세팅
        Regions region = Regions.create("서울시 강남구", null, 2, 0);
        regionRepository.save(region);

        Symptom symptom = Symptom.builder()
                .category(childCategory).symptomCode("ERR-01").symptomName("고장").build();
        symptomRepository.save(symptom);

        // 부품 마스터 세팅 (NORMAL 중요도)
        Constructor<RepairPart> partConstructor = RepairPart.class.getDeclaredConstructor();
        partConstructor.setAccessible(true);
        RepairPart repairPart = partConstructor.newInstance();
        ReflectionTestUtils.setField(repairPart, "partCode", "COMP-TEST");
        ReflectionTestUtils.setField(repairPart, "partName", "테스트 부품");
        ReflectionTestUtils.setField(repairPart, "importance", PartImportance.NORMAL);
        ReflectionTestUtils.setField(repairPart, "baseUnitPrice", 150000);
        repairPartRepository.save(repairPart);

        // 4. 더 오래된 작업 보고서 (20개월 전) - 부품 교체 포함, 가장 최근 보고서는 아님
        //    → 3축(부품 중요도)이 "가장 최근 보고서 1건"만 보는 게 아니라 전체 이력을 훑는지 검증
        AsRequest olderRequest = AsRequest.builder()
                .customer(testCustomer).appliance(testAppliance).symptom(symptom)
                .visitRegion(region).visitAddressDetail("101호")
                .scheduledDate(LocalDate.now().minusMonths(20)).scheduledTime("14:00").build();
        asRequestRepository.save(olderRequest);

        WorkReport olderReport = WorkReport.builder()
                .asRequest(olderRequest).engineer(testEngineer)
                .diagnosisResult(DiagnosisResult.PART_REPLACED)
                .workDurationMin(120).finalAmount(150000).build();

        WorkReportPart reportPart = WorkReportPart.builder()
                .repairPart(repairPart).quantity(1).appliedUnitPrice(150000).build();
        olderReport.addPart(reportPart);

        // 일단 저장 (이때 JPA Auditing이 강제로 현재 시간으로 덮어씀)
        workReportRepository.save(olderReport);

        // 💡 핵심: JPA를 우회하여 Native Query로 DB에 직접 20개월 전 날짜를 강제 세팅
        em.createNativeQuery("UPDATE work_reports SET submitted_at = :pastDate WHERE report_id = :id")
                .setParameter("pastDate", LocalDateTime.now().minusMonths(20))
                .setParameter("id", olderReport.getReportId())
                .executeUpdate();

        // 5. 가장 최근 작업 보고서 (13개월 전) - 부품 교체 없음(단순 수리)
        //    → 4축(최근 수리 경과)은 가장 최근 보고서인 이 건의 제출일을 기준으로 계산되어야 함
        AsRequest latestRequest = AsRequest.builder()
                .customer(testCustomer).appliance(testAppliance).symptom(symptom)
                .visitRegion(region).visitAddressDetail("101호")
                .scheduledDate(LocalDate.now().minusMonths(13)).scheduledTime("14:00").build();
        asRequestRepository.save(latestRequest);

        WorkReport latestReport = WorkReport.builder()
                .asRequest(latestRequest).engineer(testEngineer)
                .diagnosisResult(DiagnosisResult.REPAIRED)
                .workDurationMin(60).finalAmount(50000).build();

        workReportRepository.save(latestReport);

        em.createNativeQuery("UPDATE work_reports SET submitted_at = :pastDate WHERE report_id = :id")
                .setParameter("pastDate", LocalDateTime.now().minusMonths(13))
                .setParameter("id", latestReport.getReportId())
                .executeUpdate();

        // 💡 1차 캐시를 비워서, 조회 쿼리를 날릴 때 조작해둔 과거 데이터를 완벽하게 DB에서 불러오게 만듦
        em.flush();
        em.clear();
    }

    @Test
    @DisplayName("성공: 통합 환경에서 건강 진단서 상세 조회 (4축 점수 정상 계산)")
    void getHealthCertificate_Integration() throws Exception {
        // When
        HealthCertificateResponse response = applianceService.getHealthCertificate(
                testCustomer.getId(), "CUSTOMER", testAppliance.getId());

        // Then
        assertThat(response.getGrade()).isEqualTo("B");
        assertThat(response.getScore()).isEqualTo(75);

        // 역추산된 점수들 검증
        assertThat(response.getRepairCountScore()).isEqualTo(15);     // 누적 2회 수리 (HealthScoreCalculator: 0회=25,1회=20,2회=15,3회=8,4회+=0)
        assertThat(response.getUsagePeriodScore()).isEqualTo(20);     // 2년 사용
        assertThat(response.getPartImportanceScore()).isEqualTo(15);  // 20개월 전(최신 아님) 보고서의 NORMAL 부품도 정상적으로 잡힘
        assertThat(response.getLastRepairedScore()).isEqualTo(15);    // 가장 최근 보고서(13개월 전) 기준으로 계산
    }

    @Test
    @DisplayName("실패: 타인의 가전제품 건강 진단서를 몰래 조회 시도 시 차단")
    void getHealthCertificate_Fail_NotOwner_Integration() {
        Long myId = 1L;
        Long othersApplianceId = testAppliance.getId();
        Long otherPersonId = 9999L; // 가전 소유자가 아닌 임의의 다른 고객 ID

        assertThatThrownBy(() -> applianceService.getHealthCertificate(otherPersonId, "CUSTOMER", othersApplianceId))
                .isInstanceOf(IllegalAccessException.class)
                .hasMessageContaining("본인 소유의 가전제품 진단서만 조회할 수 있습니다.");
    }
}