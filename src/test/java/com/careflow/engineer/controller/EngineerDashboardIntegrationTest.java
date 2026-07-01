package com.careflow.engineer.controller;

import com.careflow.agency.entity.Agencies;
import com.careflow.agency.repository.AgenciesRepository;
import com.careflow.appliance.entity.Appliance;
import com.careflow.appliance.entity.ApplianceCategory;
import com.careflow.appliance.repository.ApplianceCategoryRepository;
import com.careflow.appliance.repository.ApplianceRepository;
import com.careflow.as_request.entity.AsRequest;
import com.careflow.as_request.repository.AsRequestRepository;
import com.careflow.assignment.entity.AsAssignment;
import com.careflow.assignment.repository.AsAssignmentRepository;
import com.careflow.auth.security.JwtProvider;
import com.careflow.common.enums.AgencyStatus;
import com.careflow.common.enums.AssignType;
import com.careflow.common.enums.RegisterMethod;
import com.careflow.common.enums.Role;
import com.careflow.engineer.domain.entity.EngineerProfile;
import com.careflow.engineer.domain.enums.SkillLevel;
import com.careflow.engineer.repository.EngineerProfileRepository;
import com.careflow.payment.entity.Payment;
import com.careflow.payment.repository.PaymentRepository;
import com.careflow.region.entity.Regions;
import com.careflow.region.repository.RegionRepository;
import com.careflow.report.domain.entity.WorkReport;
import com.careflow.report.domain.enums.DiagnosisResult;
import com.careflow.report.repository.WorkReportRepository;
import com.careflow.settlement.entity.BankAccount;
import com.careflow.settlement.repository.BankAccountRepository;
import com.careflow.symptom.entity.Symptom;
import com.careflow.symptom.repository.SymptomRepository;
import com.careflow.user.entity.User;
import com.careflow.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("local")
@Sql(scripts = "/cleanup.sql", executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
@DisplayName("EngineerDashboard 통합 테스트 (H2)")
class EngineerDashboardIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private JwtProvider jwtProvider;

    // 🌟 핵심: 통합 테스트에서 Redis 연결 우회
    @MockitoBean private StringRedisTemplate stringRedisTemplate;

    @Autowired private UserRepository userRepository;
    @Autowired private AgenciesRepository agenciesRepository;
    @Autowired private RegionRepository regionRepository;
    @Autowired private ApplianceCategoryRepository categoryRepository;
    @Autowired private ApplianceRepository applianceRepository;
    @Autowired private SymptomRepository symptomRepository;
    @Autowired private AsRequestRepository asRequestRepository;
    @Autowired private AsAssignmentRepository asAssignmentRepository;
    @Autowired private EngineerProfileRepository engineerProfileRepository;
    @Autowired private BankAccountRepository bankAccountRepository;
    @Autowired private WorkReportRepository workReportRepository;
    @Autowired private PaymentRepository paymentRepository;

    private User engineer;
    private String engineerToken;

    @BeforeEach
    void setUp() {
        // 1. 기초 데이터 (지역, 카테고리, 대행사, 유저)
        Regions region = regionRepository.save(Regions.create("서울 강남구", null, 2, 0));
        Agencies agency = agenciesRepository.save(Agencies.builder().agencyName("강남센터").businessNumber("123").approvalStatus(AgencyStatus.APPROVED).agencyFeeRate(5.0).build());

        engineer = userRepository.save(User.builder().email("eng@test.com").passwordHash("hash").name("이엔지").role(Role.ENGINEER).agency(agency).build());
        User customer = userRepository.save(User.builder().email("cust@test.com").passwordHash("hash").name("김고객").role(Role.CUSTOMER).build());

        // 2. 기사 프로필 및 계좌 등록 (v21 명세)
        EngineerProfile profile = EngineerProfile.createInitial(engineer);
        profile.completeProfile(categoryRepository.save(ApplianceCategory.createRoot("세탁기", 1)), 2015, SkillLevel.ADVANCED, "반갑습니다");
        engineerProfileRepository.save(profile);
        bankAccountRepository.save(BankAccount.builder().engineer(engineer).bankName("국민은행").accountNumber("123-456-789").build());

        // 3. A/S 요청 -> 배정 -> 완료 -> 보고서 작성 흐름 세팅
        ApplianceCategory cat = categoryRepository.save(ApplianceCategory.createRoot("냉장고", 1));
        Appliance appliance = applianceRepository.save(Appliance.create(customer, cat, "삼성", "비스포크", null, null, null, RegisterMethod.MANUAL));
        Symptom symptom = symptomRepository.save(Symptom.builder().category(cat).symptomCode("ERR1").symptomName("소음").build());

        AsRequest asRequest = AsRequest.builder().customer(customer).appliance(appliance).symptom(symptom).visitRegion(region).visitAddressDetail("101동").scheduledDate(LocalDate.now()).scheduledTime("14:00").build();
        asRequest.processAssignment(agency);
        asRequest.acceptAssignment(); // 수락
        asRequest.depart(); asRequest.arrive(); asRequest.startWork();
        asRequest.completeWork(); // 작업 완료
        asRequestRepository.save(asRequest);

        AsAssignment assignment = AsAssignment.create(asRequest, engineer, agency, AssignType.AUTO);
        ReflectionTestUtils.setField(assignment, "status", "COMPLETED");
        asAssignmentRepository.save(assignment);

        // 작업 보고서 작성
        workReportRepository.save(WorkReport.builder()
                .asRequest(asRequest).engineer(engineer).diagnosisResult(DiagnosisResult.NORMAL).workDurationMin(60).finalAmount(50000).build());

        // 토큰 생성
        engineerToken = jwtProvider.generateAccessToken(engineer.getId(), engineer.getEmail(), "ENGINEER", agency.getId());
    }

    @Test
    @DisplayName("성공: 대시보드 조회 시 기본 정보와 스케줄이 정상적으로 반환된다.")
    void getDashboard_Integration_Success() throws Exception {
        mockMvc.perform(get("/api/engineer/dashboard")
                        .header("Authorization", "Bearer " + engineerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.engineerName").value("이엔지"))
                .andExpect(jsonPath("$.skillLevel").value("ADVANCED")) // Profile 반영 확인
                .andExpect(jsonPath("$.todayCompletedCount").value(1)); // COMPLETED 배차 반영 확인
    }

    @Test
    @DisplayName("성공: 정산 내역 조회 시 Fetch Join과 Grouping이 적용되어 통계와 계좌 정보가 반환된다.")
    void getSettlementSummary_Integration_Success() throws Exception {
        mockMvc.perform(get("/api/engineer/settlements/summary")
                        .header("Authorization", "Bearer " + engineerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalCompletedCount").value(1))
                .andExpect(jsonPath("$.totalGrossAmount").value(50000)) // WorkReport 금액 합산 확인
                .andExpect(jsonPath("$.settlementSummary.bankName").value("국민은행")) // v21 계좌 반영 확인
                .andExpect(jsonPath("$.brandDistributions[0].name").value("삼성")) // 브랜드 그룹핑 확인
                .andExpect(jsonPath("$.statusDistributions[0].name").value("정상 완료")); // 진단 결과 그룹핑 확인
    }
}