package com.careflow.report.service;

import com.careflow.appliance.entity.HealthCertificate;
import com.careflow.appliance.entity.Appliance;
import com.careflow.appliance.entity.ApplianceCategory;
import com.careflow.appliance.repository.ApplianceCategoryRepository;
import com.careflow.appliance.repository.ApplianceRepository;
import com.careflow.appliance.repository.HealthCertificateRepository;
import com.careflow.as_request.entity.AsRequest;
import com.careflow.as_request.repository.AsRequestRepository;
import com.careflow.common.enums.AsStatus;
import com.careflow.common.enums.RegisterMethod;
import com.careflow.common.enums.Role;
import com.careflow.engineer.dto.CreateWorkReportRequest;
import com.careflow.part.domain.entity.RepairPart;
import com.careflow.part.repository.RepairPartRepository;
import com.careflow.region.entity.Regions;
import com.careflow.report.domain.entity.WorkReport;
import com.careflow.report.domain.enums.PartImportance;
import com.careflow.report.repository.WorkReportRepository;
import com.careflow.symptom.entity.Symptom;
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
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("local")
@Transactional
@Sql(scripts = "/cleanup.sql", executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
@DisplayName("WorkReportService 통합 테스트 (진짜 DB 연동)")
class WorkReportServiceIntegrationTest {

    @Autowired private WorkReportService workReportService;
    @Autowired private WorkReportRepository workReportRepository;
    @Autowired private HealthCertificateRepository healthCertificateRepository;
    @Autowired private RepairPartRepository repairPartRepository;
    @Autowired private AsRequestRepository asRequestRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private ApplianceRepository applianceRepository;
    @Autowired private ApplianceCategoryRepository categoryRepository;
    @Autowired private EntityManager em; // 🎯 FK 조작을 위한 엔티티 매니저

    private User testEngineer;
    private AsRequest testAsRequest;

    @BeforeEach
    void setUp() throws Exception {
        User testCustomer = userRepository.save(User.builder().email("cust@test.com").name("고객").role(Role.CUSTOMER).build());
        testEngineer = userRepository.save(User.builder().email("eng@test.com").name("기사").role(Role.ENGINEER).build());

        ApplianceCategory rootCategory = ApplianceCategory.createRoot("대형가전", 1);
        categoryRepository.save(rootCategory);
        ApplianceCategory childCategory = ApplianceCategory.createChild("에어컨", rootCategory, 1);
        categoryRepository.save(childCategory);

        // createdAt 등 필수 필드가 Builder 에서 설정되므로 reflection 대신 정적 팩토리 사용
        Appliance testAppliance = Appliance.create(
                testCustomer, childCategory, "삼성", "테스트모델",
                null, null, null, RegisterMethod.MANUAL);
        applianceRepository.save(testAppliance);

        // Symptom — category_id NOT NULL 이므로 Builder 로 category 설정 필수
        Symptom testSymptom = Symptom.builder()
                .category(childCategory)
                .symptomCode("ERR-01")
                .symptomName("고장")
                .build();
        em.persist(testSymptom);

        // Regions — create() 팩토리로 필수 필드 초기화
        Regions testRegion = Regions.create("서울시 강남구", null, 2, 0);
        em.persist(testRegion);

        // 팀원분의 AsRequest 빌더 규격에 완벽히 맞춤
        testAsRequest = AsRequest.builder()
                .customer(testCustomer)
                .appliance(testAppliance)
                .symptom(testSymptom)
                .visitRegion(testRegion)
                .visitAddressDetail("테스트 아파트 101호")
                .scheduledDate(LocalDate.now())
                .scheduledTime("14:00")
                .build();
        asRequestRepository.save(testAsRequest);
    }

    @Test
    @DisplayName("성공: [핵심 부품 교체 시] 진단서 등급 E, 점수 30점으로 DB 갱신")
    void submitWorkReport_CriticalPart_Integration() throws Exception {
        RepairPart criticalPart = createRepairPart();
        CreateWorkReportRequest.PartDto partDto = createPartDto(criticalPart.getRepairPartId());
        CreateWorkReportRequest request = createReportRequest("PART_REPLACED", List.of(partDto));

        Long reportId = workReportService.submitWorkReport(testEngineer.getId(), request);

        WorkReport savedReport = workReportRepository.findById(reportId).orElseThrow();
        assertThat(savedReport.getFinalAmount()).isEqualTo(200000);
        assertThat(savedReport.getParts()).hasSize(1);

        AsRequest updatedRequest = asRequestRepository.findById(testAsRequest.getId()).orElseThrow();
        assertThat(updatedRequest.getStatus()).isEqualTo(AsStatus.COMPLETED);

        // 🎯 IDE 경고 해결: .get(0) -> .getFirst()
        HealthCertificate cert = healthCertificateRepository.findAll().getFirst();
        assertThat(cert.getGrade()).isEqualTo("E");
        assertThat(cert.getScore()).isEqualTo(30);
    }

    @Test
    @DisplayName("성공: [부품 교체 없음] 진단서 등급 A, 점수 95점으로 DB 갱신")
    void submitWorkReport_NoPart_Integration() throws Exception {
        CreateWorkReportRequest request = createReportRequest("REPAIRED", null);

        workReportService.submitWorkReport(testEngineer.getId(), request);

        HealthCertificate cert = healthCertificateRepository.findAll().getFirst();
        assertThat(cert.getGrade()).isEqualTo("A");
        assertThat(cert.getScore()).isEqualTo(95);
    }

    // ---------- 픽스처 헬퍼 ----------
    private RepairPart createRepairPart() throws Exception {
        Constructor<RepairPart> constructor = RepairPart.class.getDeclaredConstructor();
        constructor.setAccessible(true);
        RepairPart part = constructor.newInstance();
        ReflectionTestUtils.setField(part, "partCode", "COMP-01");
        ReflectionTestUtils.setField(part, "partName", "에어컨 컴프레서");
        ReflectionTestUtils.setField(part, "importance", PartImportance.CRITICAL);
        ReflectionTestUtils.setField(part, "baseUnitPrice", 150000);
        return repairPartRepository.save(part);
    }

    private CreateWorkReportRequest createReportRequest(String diag, List<CreateWorkReportRequest.PartDto> parts) throws Exception {
        Constructor<CreateWorkReportRequest> constructor = CreateWorkReportRequest.class.getDeclaredConstructor();
        constructor.setAccessible(true);
        CreateWorkReportRequest req = constructor.newInstance();
        ReflectionTestUtils.setField(req, "requestId", testAsRequest.getId());
        ReflectionTestUtils.setField(req, "diagnosisResult", diag);
        ReflectionTestUtils.setField(req, "workDurationMin", 120);
        ReflectionTestUtils.setField(req, "finalAmount", 200000);
        ReflectionTestUtils.setField(req, "memo", "작업 완료");
        ReflectionTestUtils.setField(req, "parts", parts);
        return req;
    }

    private CreateWorkReportRequest.PartDto createPartDto(Long partId) throws Exception {
        Constructor<CreateWorkReportRequest.PartDto> constructor = CreateWorkReportRequest.PartDto.class.getDeclaredConstructor();
        constructor.setAccessible(true);
        CreateWorkReportRequest.PartDto dto = constructor.newInstance();
        ReflectionTestUtils.setField(dto, "repairPartId", partId);
        ReflectionTestUtils.setField(dto, "quantity", 1);
        ReflectionTestUtils.setField(dto, "appliedUnitPrice", 150000);
        return dto;
    }
}