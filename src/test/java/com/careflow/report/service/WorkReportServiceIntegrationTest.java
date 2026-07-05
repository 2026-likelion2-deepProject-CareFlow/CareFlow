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
import com.careflow.as_status_log.repository.AsStatusLogRepository;
import com.careflow.assignment.entity.AsAssignment;
import com.careflow.assignment.repository.AsAssignmentRepository;
import com.careflow.common.enums.AsStatus;
import com.careflow.common.enums.AssignType;
import com.careflow.common.enums.RegisterMethod;
import com.careflow.common.enums.Role;
import com.careflow.engineer.dto.CreateWorkReportRequest;
import com.careflow.notification.repository.NotificationRepository;
import com.careflow.part.domain.entity.RepairPart;
import com.careflow.part.repository.RepairPartRepository;
import com.careflow.region.entity.Regions;
import com.careflow.report.domain.entity.WorkReport;
import com.careflow.report.domain.entity.WorkReportPart;
import com.careflow.common.enums.DiagnosisResult;
import com.careflow.common.enums.PartImportance;
import com.careflow.report.dto.RepairHistoryResponse;
import com.careflow.report.dto.WorkReportDetailResponse;
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
    @Autowired private AsStatusLogRepository asStatusLogRepository;
    @Autowired private NotificationRepository notificationRepository;

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

        testAsRequest.assignAgency(agency);            // PENDING -> AGENCY_RECEIVED
        testAsRequest.processAssignment(agency);       // AGENCY_RECEIVED -> ASSIGNED
        testAsRequest.acceptAssignment();              // ASSIGNED -> ACCEPTED

        // 💡 추가된 부분: 기사가 출발하고 도착하는 과정 추가!
        testAsRequest.depart();                        // ACCEPTED -> ENGINEER_DEPARTED
        testAsRequest.arrive();                        // ENGINEER_DEPARTED -> ENGINEER_ARRIVED

        testAsRequest.startWork();                     // ENGINEER_ARRIVED -> IN_PROGRESS!
        asRequestRepository.save(testAsRequest);

        // AsAssignment 세팅
        AsAssignment assignment = AsAssignment.create(testAsRequest, testEngineer, agency, AssignType.MANUAL);
        ReflectionTestUtils.setField(assignment, "status", "ACCEPTED");
        asAssignmentRepository.save(assignment);
    }

    @ParameterizedTest
    @CsvSource({
            // 이번 접수가 첫 수리이므로: repairCount=1회(20점) + usagePeriod=구매일 미상(25점) + lastRepaired=방금 수리(0점)
            "CRITICAL, D, 45",  // 20+25+0(CRITICAL)+0 = 45점
            "MAJOR, D, 53",     // 20+25+8(MAJOR)+0 = 53점
            "NORMAL, C, 60",    // 20+25+15(NORMAL)+0 = 60점
            "MINOR, C, 65"      // 20+25+20(MINOR)+0 = 65점
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
    @DisplayName("성공: [부품 교체 없음] 4축 계산 모델 적용 및 DB에 완료 로그 저장")
    void submitWorkReport_NoPart_Integration() throws Exception {
        CreateWorkReportRequest request = createReportRequest("REPAIRED", null);

        long initialLogCount = asStatusLogRepository.count();

        // When
        workReportService.submitWorkReport(testEngineer.getId(), request);

        // Then
        // 이번 접수가 첫 수리이므로: repairCount=1회(20점) + usagePeriod=구매일 미상(25점)
        // + partImportance=부품 교체 없음(25점) + lastRepaired=방금 수리(0점) = 70점
        HealthCertificate cert = healthCertificateRepository.findAll().getFirst();
        assertThat(cert.getGrade()).isEqualTo("C");
        assertThat(cert.getScore()).isEqualTo(70);

        // 🌟 추가 검증: DB에 상태 로그 데이터가 INSERT 되었는지 확인
        long newLogCount = asStatusLogRepository.count();
        assertThat(newLogCount).isGreaterThan(initialLogCount);

        // 💡 AFTER_COMMIT 리스너는 @Transactional 테스트에서 트리거되지 않으므로 알림 카운트 검증은 제외합니다.
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

    @Test
    @DisplayName("성공: 가전 수리 이력 상세 조회 (최신순 정렬 및 조인 쿼리 검증)")
    void getApplianceRepairHistory_Integration() throws Exception {
        // Given: 기존 setUp()에서 만들어둔 testAsRequest와 testEngineer를 활용
        // 1번 보고서 (과거 데이터)
        WorkReport report1 = WorkReport.builder()
                .asRequest(testAsRequest)
                .engineer(testEngineer)
                .diagnosisResult(DiagnosisResult.REPAIRED)
                .workDurationMin(60)
                .finalAmount(50000)
                .memo("첫 번째 수리")
                .build();
        ReflectionTestUtils.setField(report1, "submittedAt", LocalDate.now().minusDays(10).atStartOfDay());
        workReportRepository.save(report1);

        // 2번 보고서 (최신 데이터)
        // (주의: AsRequest는 Report와 1:1 관계이므로 새 AsRequest를 만들어야 합니다)
        Symptom newSymptom = Symptom.builder()
                .category(testAsRequest.getAppliance().getCategory())
                .symptomCode("ERR-02")
                .symptomName("소음 발생")
                .build();
        em.persist(newSymptom);

        AsRequest request2 = AsRequest.builder()
                .customer(testAsRequest.getCustomer())
                .appliance(testAsRequest.getAppliance()) // 🎯 1번과 같은 가전제품!
                .symptom(newSymptom)
                .visitRegion(testAsRequest.getVisitRegion())
                .visitAddressDetail("테스트 아파트 101호")
                .scheduledDate(LocalDate.now())
                .scheduledTime("15:00")
                .build();
        asRequestRepository.save(request2);

        WorkReport report2 = WorkReport.builder()
                .asRequest(request2)
                .engineer(testEngineer)
                .diagnosisResult(DiagnosisResult.PART_REPLACED)
                .workDurationMin(120)
                .finalAmount(150000)
                .memo("두 번째 수리 (최신)")
                .build();
        ReflectionTestUtils.setField(report2, "submittedAt", LocalDate.now().atStartOfDay());
        workReportRepository.save(report2);

        // When: 고객 본인이 자기 가전의 수리 이력을 조회
        Long customerId = testAsRequest.getCustomer().getId();
        Long applianceId = testAsRequest.getAppliance().getId();
        List<RepairHistoryResponse> history = workReportService.getApplianceRepairHistory(customerId, "CUSTOMER", applianceId);

        // Then: 2건이 최신순(내림차순)으로 나와야 함
        assertThat(history).hasSize(2);

        // 0번째 인덱스가 가장 최근 수리인 '소음 발생'건 이어야 함
        assertThat(history.get(0).getSymptomName()).isEqualTo("소음 발생");
        assertThat(history.get(0).getFinalAmount()).isEqualTo(150000);

        // 1번째 인덱스가 과거 수리인 '고장'건 이어야 함
        assertThat(history.get(1).getSymptomName()).isEqualTo("고장");
        assertThat(history.get(1).getFinalAmount()).isEqualTo(50000);
    }

    @Test
    @DisplayName("실패: 다른 고객의 가전 수리 이력 조회 시도 (통합 환경 권한 방어)")
    void getApplianceRepairHistory_Fail_NotOwner_Integration() throws Exception {
        Long applianceId = testAsRequest.getAppliance().getId();
        Long otherCustomerId = 9999L; // 가전 소유자가 아닌 임의의 다른 고객 ID

        assertThatThrownBy(() -> workReportService.getApplianceRepairHistory(otherCustomerId, "CUSTOMER", applianceId))
                .isInstanceOf(IllegalAccessException.class)
                .hasMessageContaining("본인 소유의 가전제품 수리 이력만 조회할 수 있습니다.");
    }

    @Test
    @DisplayName("성공: 통합 환경에서 고객이 본인의 작업 보고서를 상세 조회한다")
    void getWorkReportDetail_Integration() throws Exception {
        // Given: setUp()에서 세팅된 testAsRequest와 testEngineer를 활용해 보고서 저장
        WorkReport report = WorkReport.builder()
                .asRequest(testAsRequest)
                .engineer(testEngineer)
                .diagnosisResult(DiagnosisResult.REPAIRED)
                .workDurationMin(90)
                .finalAmount(100000)
                .memo("꼼꼼하게 수리했습니다.")
                .build();

        // 부품 세팅
        Constructor<RepairPart> partConstructor = RepairPart.class.getDeclaredConstructor();
        partConstructor.setAccessible(true);
        RepairPart repairPart = partConstructor.newInstance();
        ReflectionTestUtils.setField(repairPart, "partCode", "COMP-DETAIL");
        ReflectionTestUtils.setField(repairPart, "partName", "상세조회용 부품");
        ReflectionTestUtils.setField(repairPart, "importance", PartImportance.NORMAL);
        ReflectionTestUtils.setField(repairPart, "baseUnitPrice", 50000);
        repairPartRepository.save(repairPart);

        WorkReportPart reportPart = WorkReportPart.builder()
                .repairPart(repairPart)
                .quantity(2) // 수량 2개
                .appliedUnitPrice(50000)
                .build();
        report.addPart(reportPart);

        WorkReport savedReport = workReportRepository.save(report);

        // 영속성 컨텍스트 비우기 (조회 시 DB에서 조인해서 가져오도록 강제)
        em.flush();
        em.clear();

        // When: 고객이 조회
        Long customerId = testAsRequest.getCustomer().getId();
        WorkReportDetailResponse response = workReportService.getWorkReportDetail(customerId, "CUSTOMER", savedReport.getReportId());

        // Then
        assertThat(response.getReportId()).isEqualTo(savedReport.getReportId());
        assertThat(response.getEngineerName()).isEqualTo("기사");
        assertThat(response.getDiagnosisResult()).isEqualTo("REPAIRED");
        assertThat(response.getFinalAmount()).isEqualTo(100000);
        assertThat(response.getMemo()).isEqualTo("꼼꼼하게 수리했습니다.");

        // 부품 리스트(FETCH JOIN) 검증
        assertThat(response.getParts()).hasSize(1);
        assertThat(response.getParts().get(0).getPartName()).isEqualTo("상세조회용 부품");
        assertThat(response.getParts().get(0).getQuantity()).isEqualTo(2);
    }

    @Test
    @DisplayName("성공: 통합 환경에서 고객이 작업 보고서를 승인한다")
    void approveWorkReport_Integration() throws Exception {
        // Given
        WorkReport report = WorkReport.builder()
                .asRequest(testAsRequest)
                .engineer(testEngineer)
                .diagnosisResult(DiagnosisResult.NORMAL)
                .workDurationMin(30)
                .finalAmount(30000)
                .build();
        WorkReport savedReport = workReportRepository.save(report);

        // When
        Long customerId = testAsRequest.getCustomer().getId();
        workReportService.approveWorkReport(customerId, savedReport.getReportId());

        em.flush();
        em.clear();

        // Then
        WorkReport updatedReport = workReportRepository.findById(savedReport.getReportId()).orElseThrow();
        assertThat(updatedReport.isCustomerApproved()).isTrue();
        assertThat(updatedReport.getApprovedAt()).isNotNull();
    }

    @Test
    @DisplayName("실패: 타인의 보고서를 승인하려고 하면 권한 예외 발생")
    void approveWorkReport_Fail_NotOwner_Integration() throws Exception {
        // Given
        WorkReport report = WorkReport.builder()
                .asRequest(testAsRequest)
                .engineer(testEngineer)
                .diagnosisResult(DiagnosisResult.NORMAL)
                .workDurationMin(30)
                .finalAmount(30000)
                .build();
        WorkReport savedReport = workReportRepository.save(report);

        // When & Then
        Long otherCustomerId = 9999L; // 가짜 ID
        assertThatThrownBy(() -> workReportService.approveWorkReport(otherCustomerId, savedReport.getReportId()))
                .isInstanceOf(IllegalAccessException.class)
                .hasMessageContaining("본인의 A/S 보고서만 승인할 수 있습니다.");
    }
}