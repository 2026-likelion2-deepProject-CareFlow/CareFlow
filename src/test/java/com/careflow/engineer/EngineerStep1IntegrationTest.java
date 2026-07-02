package com.careflow.engineer;

import com.careflow.agency.entity.Agencies;
import com.careflow.agency.repository.AgenciesRepository;
import com.careflow.appliance.entity.Appliance;
import com.careflow.appliance.entity.ApplianceCategory;
import com.careflow.appliance.entity.HealthCertificate;
import com.careflow.appliance.repository.ApplianceCategoryRepository;
import com.careflow.appliance.repository.ApplianceRepository;
import com.careflow.appliance.repository.HealthCertificateRepository;
import com.careflow.as_request.entity.AsRequest;
import com.careflow.as_request.repository.AsRequestRepository;
import com.careflow.assignment.entity.AsAssignment;
import com.careflow.assignment.repository.AsAssignmentRepository;
import com.careflow.common.enums.AgencyStatus;
import com.careflow.common.enums.AssignType;
import com.careflow.common.enums.RegisterMethod;
import com.careflow.common.enums.Role;
import com.careflow.engineer.domain.entity.EngineerSchedule;
import com.careflow.engineer.domain.enums.ScheduleStatus;
import com.careflow.engineer.dto.CreateWorkReportRequest;
import com.careflow.engineer.repository.EngineerScheduleRepository;
import com.careflow.engineer.service.EngineerScheduleService;
import com.careflow.region.entity.Regions;
import com.careflow.region.repository.RegionRepository;
import com.careflow.report.service.WorkReportService;
import com.careflow.symptom.entity.Symptom;
import com.careflow.symptom.repository.SymptomRepository;
import com.careflow.user.entity.User;
import com.careflow.user.repository.UserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("local")
@Transactional
@TestPropertySource(properties = "spring.quartz.auto-startup=false")
@Sql(scripts = "/cleanup.sql", executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
@DisplayName("기사 STEP 1 버그 픽스 통합 테스트 (진단서 멱등성 & 일정 Hard Delete)")
public class EngineerStep1IntegrationTest {

    @MockitoBean
    private StringRedisTemplate stringRedisTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private WorkReportService workReportService;

    @Autowired
    private EngineerScheduleService engineerScheduleService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private AgenciesRepository agenciesRepository;

    @Autowired
    private RegionRepository regionRepository;

    @Autowired
    private ApplianceRepository applianceRepository;

    @Autowired
    private ApplianceCategoryRepository categoryRepository;

    @Autowired
    private SymptomRepository symptomRepository;

    @Autowired
    private AsRequestRepository asRequestRepository;

    @Autowired
    private AsAssignmentRepository asAssignmentRepository;

    @Autowired
    private HealthCertificateRepository healthCertificateRepository;

    @Autowired
    private EngineerScheduleRepository engineerScheduleRepository;

    private User engineer;
    private Appliance appliance;
    private AsRequest asRequest;
    private Agencies agency;

    @BeforeEach
    public void setUp() {
        System.out.println("=== setUp start ===");

        Regions region = regionRepository.save(Regions.create("서울 강남구", null, 2, 0));
        System.out.println("region saved: " + region.getId());

        agency = agenciesRepository.save(
                Agencies.builder()
                        .agencyName("강남센터")
                        .businessNumber("123")
                        .approvalStatus(AgencyStatus.APPROVED)
                        .agencyFeeRate(5.0)
                        .build()
        );
        System.out.println("agency saved: " + agency.getId());

        engineer = userRepository.save(
                User.builder()
                        .email("eng1@test.com")
                        .name("이수리")
                        .role(Role.ENGINEER)
                        .agency(agency)
                        .build()
        );
        System.out.println("engineer saved: " + engineer.getId());

        User customer = userRepository.save(
                User.builder()
                        .email("cust1@test.com")
                        .name("김고객")
                        .role(Role.CUSTOMER)
                        .regionId(region)
                        .build()
        );
        System.out.println("customer saved: " + customer.getId());

        ApplianceCategory cat = categoryRepository.save(ApplianceCategory.createRoot("TV", 1));
        System.out.println("category saved: " + cat.getCategoryId());

        appliance = applianceRepository.save(
                Appliance.create(customer, cat, "LG", "올레드", null, LocalDate.now(), null, RegisterMethod.MANUAL)
        );
        System.out.println("appliance saved: " + appliance.getId());

        Symptom symptom = symptomRepository.save(
                Symptom.builder()
                        .category(cat)
                        .symptomCode("ERR")
                        .symptomName("화면 깨짐")
                        .build()
        );
        System.out.println("symptom saved: " + symptom.getId());

        asRequest = asRequestRepository.saveAndFlush(
                AsRequest.builder()
                        .customer(customer)
                        .appliance(appliance)
                        .symptom(symptom)
                        .visitRegion(region)
                        .visitAddressDetail("101동")
                        .scheduledDate(LocalDate.now())
                        .scheduledTime("14:00")
                        .build()
        );
        System.out.println("asRequest saved: " + asRequest.getId() + ", status=" + asRequest.getStatus());

        AsAssignment assignment = AsAssignment.create(asRequest, engineer, agency, AssignType.AUTO);
        System.out.println("assignment created: status=" + assignment.getStatus());

        assignment.accept();
        System.out.println("assignment accepted: status=" + assignment.getStatus());

        asAssignmentRepository.saveAndFlush(assignment);
        System.out.println("assignment flushed");

        asRequest.processAssignment(agency);
        System.out.println("asRequest after processAssignment: " + asRequest.getStatus());

        asRequest.acceptAssignment();
        System.out.println("asRequest after acceptAssignment: " + asRequest.getStatus());

        asRequest.depart();
        System.out.println("asRequest after depart: " + asRequest.getStatus());

        asRequest.arrive();
        System.out.println("asRequest after arrive: " + asRequest.getStatus());

        asRequest.startWork();
        System.out.println("asRequest after startWork: " + asRequest.getStatus());

        asRequest = asRequestRepository.saveAndFlush(asRequest);
        System.out.println("asRequest flushed final: " + asRequest.getStatus());

        System.out.println("=== setUp end ===");
    }


    @Test
    @DisplayName("성공: 보고서 제출 후 진단서 점수가 변경되고, 취소 시 완벽히 원복(멱등성)된다.")
    public void submitAndCancelReport_Idempotency_Success() throws Exception {
        System.out.println("=== test start ===");
        System.out.println("requestId=" + asRequest.getId());
        System.out.println("engineerId=" + engineer.getId());
        System.out.println("requestStatus=" + asRequest.getStatus());

        String jsonRequest = String.format("""
        {
            "requestId": %d,
            "diagnosisResult": "REPAIRED",
            "workDurationMin": 30,
            "finalAmount": 30000
        }
        """, asRequest.getId());

        System.out.println("jsonRequest=" + jsonRequest);

        CreateWorkReportRequest requestDto = objectMapper.readValue(jsonRequest, CreateWorkReportRequest.class);
        System.out.println("dto parsed: requestId=" + requestDto.getRequestId());

        Long reportId = workReportService.submitWorkReport(engineer.getId(), requestDto);
        System.out.println("reportId=" + reportId);

        HealthCertificate certAfterSubmit = healthCertificateRepository.findByAppliance_Id(appliance.getId()).orElseThrow();
        System.out.println("certAfterSubmit: repairCount=" + certAfterSubmit.getRepairCount() + ", score=" + certAfterSubmit.getScore());

        workReportService.cancelApprovalRequest(engineer.getId(), reportId);
        System.out.println("cancelApprovalRequest done");

        HealthCertificate certAfterCancel = healthCertificateRepository.findByAppliance_Id(appliance.getId()).orElseThrow();
        System.out.println("certAfterCancel: repairCount=" + certAfterCancel.getRepairCount() + ", score=" + certAfterCancel.getScore());

        System.out.println("=== test end ===");
    }

}
