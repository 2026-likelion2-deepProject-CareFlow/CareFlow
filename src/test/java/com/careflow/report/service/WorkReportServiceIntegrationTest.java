package com.careflow.report.service;

import com.careflow.agency.entity.Agencies;
import com.careflow.agency.repository.AgenciesRepository;
import com.careflow.appliance.entity.HealthCertificate;
import com.careflow.appliance.entity.Appliance;
import com.careflow.appliance.entity.ApplianceCategory;
import com.careflow.appliance.repository.ApplianceCategoryRepository;
import com.careflow.appliance.repository.ApplianceRepository;
import com.careflow.appliance.repository.HealthCertificateRepository;
import com.careflow.as_request.entity.AsRequest;
import com.careflow.as_request.repository.AsRequestRepository;
import com.careflow.assignment.entity.AsAssignment;
import com.careflow.assignment.repository.AsAssignmentRepository;
import com.careflow.common.enums.AsStatus;
import com.careflow.common.enums.AssignType;
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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.lang.reflect.Constructor;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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
    @Autowired private AgenciesRepository agenciesRepository;
    @Autowired private AsAssignmentRepository asAssignmentRepository;
    @Autowired private EntityManager em;

    private User testEngineer;
    private AsRequest testAsRequest;

    @BeforeEach
    void setUp() throws Exception {
        Agencies agency = agenciesRepository.save(Agencies.builder()
                .agencyName("배차대행사").businessNumber("123-45-00000").agencyFeeRate(5.0).build());

        User testCustomer = userRepository.save(User.builder().email("cust@test.com").name("고객").role(Role.CUSTOMER).build());
        testEngineer = userRepository.save(User.builder().email("eng@test.com").name("기사").role(Role.ENGINEER).agency(agency).build());

        ApplianceCategory rootCategory = ApplianceCategory.createRoot("대형가전", 1);
        categoryRepository.save(rootCategory);
        ApplianceCategory childCategory = ApplianceCategory.createChild("에어컨", rootCategory, 1);
        categoryRepository.save(childCategory);

        Appliance testAppliance = Appliance.create(
                testCustomer, childCategory, "삼성", "테스트모델",
                null, null, null, RegisterMethod.MANUAL);
        applianceRepository.save(testAppliance);

        Symptom testSymptom = Symptom.builder()
                .category(childCategory).symptomCode("ERR-01").symptomName("고장").build();
        em.persist(testSymptom);

        Regions testRegion = Regions.create("서울시 강남구", null, 2, 0);
        em.persist(testRegion);

        // 1. A/S 접수 (PENDING)
        testAsRequest = AsRequest.builder()
                .customer(testCustomer)
                .appliance(testAppliance)
                .symptom(testSymptom)
                .visitRegion(testRegion)
                .visitAddressDetail("테스트 아파트 101호")
                .scheduledDate(LocalDate.now())
                .scheduledTime("14:00")
                .build();

        // 🎯 2. 도메인 메서드를 통해 실제 상태 전이 파이프라인 작동! (Reflection 꼼수 제거!)
        testAsRequest.assignAgency(agency);            // PENDING -> AGENCY_RECEIVED
        testAsRequest.processAssignment(agency);       // AGENCY_RECEIVED -> ASSIGNED
        testAsRequest.acceptAssignment();              // ASSIGNED -> ACCEPTED
        testAsRequest.startWork();                     // ACCEPTED -> IN_PROGRESS! (우리가 원하는 상태 도달)

        asRequestRepository.save(testAsRequest);

        // AsAssignment 세팅
        AsAssignment assignment = AsAssignment.create(testAsRequest, testEngineer, agency, AssignType.MANUAL);
        ReflectionTestUtils.setField(assignment, "status", "ACCEPTED");
        asAssignmentRepository.save(assignment);
    }

    @ParameterizedTest
    @CsvSource({
            "CRITICAL, B, 75",  // 기본 75점 + CRITICAL(0점) = 75점 (B등급 선방!)
            "MAJOR, B, 83",     // 기본 75점 + MAJOR(8점) = 83점
            "NORMAL, A, 90",    // 기본 75점 + NORMAL(15점) = 90점
            "MINOR, A, 95"      // 기본 75점 + MINOR(20점) = 95점
    })
    @DisplayName("성공: [부품 교체] 4축 계산 모델 적용 - 부품 중요도별 등급/점수 산정 검증")
    void submitWorkReport_Parts_Parameterized_Integration(PartImportance importance, String expectedGrade, int expectedScore) throws Exception {
        RepairPart dynamicPart = createRepairPart(importance);
        CreateWorkReportRequest.PartDto partDto = createPartDto(dynamicPart.getRepairPartId());
        CreateWorkReportRequest request = createReportRequest("PART_REPLACED", List.of(partDto));

        workReportService.submitWorkReport(testEngineer.getId(), request);

        HealthCertificate cert = healthCertificateRepository.findAll().getFirst();
        assertThat(cert.getGrade()).isEqualTo(expectedGrade);
        assertThat(cert.getScore()).isEqualTo(expectedScore);
    }

    @Test
    @DisplayName("성공: [부품 교체 없음] 4축 계산 모델 적용되어 100점(A등급)으로 DB 갱신")
    void submitWorkReport_NoPart_Integration() throws Exception {
        CreateWorkReportRequest request = createReportRequest("REPAIRED", null);

        workReportService.submitWorkReport(testEngineer.getId(), request);

        HealthCertificate cert = healthCertificateRepository.findAll().getFirst();
        // 🎯 검증: 첫 수리(25) + 기간 모름(25) + 과거 수리 없음(25) + 부품 교체 없음(25) = 100점 (A등급)
        assertThat(cert.getGrade()).isEqualTo("A");
        assertThat(cert.getScore()).isEqualTo(100);
    }

    @Test
    @DisplayName("실패: IN_PROGRESS 상태가 아닌 A/S 건 완료 시도 시 방어")
    void submitWorkReport_Fail_WrongStatus() throws Exception {
        // Given: PENDING 상태로 돌려놓기
        ReflectionTestUtils.setField(testAsRequest, "status", AsStatus.PENDING);
        asRequestRepository.save(testAsRequest);

        CreateWorkReportRequest request = createReportRequest("REPAIRED", null);

        // When & Then
        assertThatThrownBy(() -> workReportService.submitWorkReport(testEngineer.getId(), request))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("수리 진행 중(IN_PROGRESS)인 상태에서만 작업 완료 처리가 가능합니다");
    }

    // ---------- 픽스처 헬퍼 ----------
    private RepairPart createRepairPart(PartImportance importance) throws Exception {
        Constructor<RepairPart> constructor = RepairPart.class.getDeclaredConstructor();
        constructor.setAccessible(true);
        RepairPart part = constructor.newInstance();
        ReflectionTestUtils.setField(part, "partCode", "COMP-TEST");
        ReflectionTestUtils.setField(part, "partName", "테스트 부품");
        ReflectionTestUtils.setField(part, "importance", importance);
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