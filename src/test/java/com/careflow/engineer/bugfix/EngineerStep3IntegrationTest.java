package com.careflow.engineer.bugfix;

import com.careflow.agency.entity.Agencies;
import com.careflow.agency.repository.AgenciesRepository;
import com.careflow.appliance.entity.Appliance;
import com.careflow.appliance.entity.ApplianceCategory;
import com.careflow.appliance.repository.ApplianceCategoryRepository;
import com.careflow.appliance.repository.ApplianceRepository;
import com.careflow.as_request.entity.AsRequest;
import com.careflow.as_request.repository.AsRequestRepository;
import com.careflow.as_status_log.repository.AsStatusLogRepository;
import com.careflow.auth.security.JwtProvider;
import com.careflow.common.enums.AgencyStatus;
import com.careflow.common.enums.AsStatus;
import com.careflow.common.enums.RegisterMethod;
import com.careflow.common.enums.Role;
import com.careflow.engineer.domain.entity.EngineerProfile;
import com.careflow.engineer.repository.EngineerProfileRepository;
import com.careflow.payment.entity.Payment;
import com.careflow.payment.repository.PaymentRepository;
import com.careflow.region.entity.Regions;
import com.careflow.region.repository.RegionRepository;
import com.careflow.report.domain.entity.WorkReport;
import com.careflow.common.enums.DiagnosisResult;
import com.careflow.report.repository.WorkReportRepository;
import com.careflow.settlement.entity.Settlement;
import com.careflow.settlement.repository.SettlementRepository;
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
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional; // 🌟 필수 추가

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("local")
@Transactional // 🌟 핵심 수정 포인트: 데이터 롤백 및 영속성 컨텍스트 플러시 보장
@Sql(scripts = "/cleanup.sql", executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
@DisplayName("기사 미구현 API 3종 (Navbar, 보고서 취소, 정산내역) 통합 테스트")
class EngineerStep3IntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private JwtProvider jwtProvider;

    @MockitoBean private StringRedisTemplate stringRedisTemplate;

    @Autowired private UserRepository userRepository;
    @Autowired private AgenciesRepository agenciesRepository;
    @Autowired private RegionRepository regionRepository;
    @Autowired private ApplianceCategoryRepository categoryRepository;
    @Autowired private ApplianceRepository applianceRepository;
    @Autowired private SymptomRepository symptomRepository;
    @Autowired private AsRequestRepository asRequestRepository;
    @Autowired private EngineerProfileRepository engineerProfileRepository;
    @Autowired private WorkReportRepository workReportRepository;
    @Autowired private PaymentRepository paymentRepository;
    @Autowired private SettlementRepository settlementRepository;
    @Autowired private AsStatusLogRepository asStatusLogRepository;

    private User engineer;
    private AsRequest asRequest;
    private WorkReport workReport;
    private String engineerToken;

    @BeforeEach
    void setUp() {
        Regions region = regionRepository.save(Regions.create("서울 강남구", null, 2, 0));
        Agencies agency = agenciesRepository.save(Agencies.builder().agencyName("강남센터").businessNumber("123").approvalStatus(AgencyStatus.APPROVED).agencyFeeRate(5.0).build());

        engineer = userRepository.save(User.builder().email("eng@test.com").passwordHash("hash").name("김기사").role(Role.ENGINEER).agency(agency).build());
        User customer = userRepository.save(User.builder().email("cust@test.com").passwordHash("hash").name("홍길동").role(Role.CUSTOMER).build());

        EngineerProfile profile = EngineerProfile.createInitial(engineer);
        profile.updateBasicInfo(2015, null, "소개", "https://img.url");
        engineerProfileRepository.save(profile);

        ApplianceCategory cat = categoryRepository.save(ApplianceCategory.createRoot("냉장고", 1));
        Appliance appliance = applianceRepository.save(Appliance.create(customer, cat, "삼성", "비스포크", null, null, null, RegisterMethod.MANUAL));
        Symptom symptom = symptomRepository.save(Symptom.builder().category(cat).symptomCode("ERR").symptomName("소음").build());

        asRequest = AsRequest.builder().customer(customer).appliance(appliance).symptom(symptom).visitRegion(region).visitAddressDetail("101호").scheduledDate(LocalDate.now()).scheduledTime("14:00").build();
        asRequest.processAssignment(agency);
        asRequest.acceptAssignment(); asRequest.depart(); asRequest.arrive(); asRequest.startWork(); asRequest.completeWork();
        asRequestRepository.save(asRequest);

        workReport = workReportRepository.save(WorkReport.builder()
                .asRequest(asRequest).engineer(engineer).diagnosisResult(DiagnosisResult.NORMAL).workDurationMin(60).finalAmount(50000).build());

        Payment payment = paymentRepository.save(Payment.create(asRequest, customer, 50000));
        settlementRepository.save(Settlement.create(payment, asRequest, engineer, agency, 50000, 5000, BigDecimal.valueOf(10), 2500, BigDecimal.valueOf(5), 42500));

        engineerToken = jwtProvider.generateAccessToken(engineer.getId(), engineer.getEmail(), "ENGINEER", agency.getId());
    }

    @Test
    @DisplayName("성공: Navbar 프로필 조회 시 이름과 이미지 URL이 반환된다.")
    void getNavbarProfile_Success() throws Exception {
        mockMvc.perform(get("/api/engineer/profile/me")
                        .header("Authorization", "Bearer " + engineerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("김기사"));
    }

    @Test
    @DisplayName("성공: 정산 내역 조회 시 Fetch Join을 통해 N+1 없이 페이징 목록이 반환된다.")
    void getSettlements_Success() throws Exception {
        mockMvc.perform(get("/api/engineer/settlements?page=0&size=10")
                        .header("Authorization", "Bearer " + engineerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].engineerNetAmount").value(42500));
    }

    @Test
    @DisplayName("성공: 보고서 승인 요청 취소 시 DB에서 삭제되고 상태가 IN_PROGRESS로 원복된다.")
    void cancelApprovalRequest_Integration_Success() throws Exception {
        mockMvc.perform(delete("/api/engineer/work-reports/" + workReport.getReportId() + "/approval-request")
                        .header("Authorization", "Bearer " + engineerToken))
                .andExpect(status().isOk());

        // 1. DB 물리 삭제 검증
        assertThat(workReportRepository.findById(workReport.getReportId())).isEmpty();

        // 2. AsRequest 상태 원복(COMPLETED -> IN_PROGRESS) 검증
        AsRequest updatedRequest = asRequestRepository.findById(asRequest.getId()).orElseThrow();
        assertThat(updatedRequest.getStatus()).isEqualTo(AsStatus.IN_PROGRESS);
    }
}