package com.careflow.engineer.bugfix;

import com.careflow.agency.entity.Agencies;
import com.careflow.agency.repository.AgenciesRepository;
import com.careflow.appliance.entity.Appliance;
import com.careflow.appliance.entity.ApplianceCategory;
import com.careflow.appliance.repository.ApplianceCategoryRepository;
import com.careflow.appliance.repository.ApplianceRepository;
import com.careflow.as_request.entity.AsRequest;
import com.careflow.as_request.repository.AsRequestRepository;
import com.careflow.as_status_log.entity.AsStatusLog;
import com.careflow.as_status_log.repository.AsStatusLogRepository;
import com.careflow.assignment.entity.AsAssignment;
import com.careflow.assignment.repository.AsAssignmentRepository;
import com.careflow.auth.security.JwtProvider;
import com.careflow.common.enums.AgencyStatus;
import com.careflow.common.enums.AssignType;
import com.careflow.common.enums.RegisterMethod;
import com.careflow.common.enums.Role;
import com.careflow.engineer.domain.entity.EngineerProfile;
import com.careflow.engineer.repository.EngineerProfileRepository;
import com.careflow.payment.entity.Payment;
import com.careflow.payment.repository.PaymentRepository;
import com.careflow.region.entity.Regions;
import com.careflow.region.repository.RegionRepository;
import com.careflow.report.domain.entity.WorkReport;
import com.careflow.report.domain.enums.DiagnosisResult;
import com.careflow.report.repository.WorkReportRepository;
import com.careflow.review.entity.Review;
import com.careflow.review.repository.ReviewRepository;
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
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.jdbc.Sql;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("local")
@TestPropertySource(properties = "spring.quartz.auto-startup=false")
@Sql(scripts = "/cleanup.sql", executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
@DisplayName("기사 통합 테스트 (최종 연동 규격 검증)")
class EngineerIntegrationTest {

    @MockitoBean
    private StringRedisTemplate stringRedisTemplate;

    @Autowired
    private org.springframework.test.web.servlet.MockMvc mockMvc;

    @Autowired
    private JwtProvider jwtProvider;

    @Autowired
    private UserRepository userRepository;
    @Autowired
    private RegionRepository regionRepository;
    @Autowired
    private AgenciesRepository agenciesRepository;
    @Autowired
    private ApplianceCategoryRepository categoryRepository;
    @Autowired
    private ApplianceRepository applianceRepository;
    @Autowired
    private SymptomRepository symptomRepository;
    @Autowired
    private AsRequestRepository asRequestRepository;
    @Autowired
    private AsAssignmentRepository asAssignmentRepository;
    @Autowired
    private AsStatusLogRepository asStatusLogRepository;
    @Autowired
    private WorkReportRepository workReportRepository;
    @Autowired
    private PaymentRepository paymentRepository;
    @Autowired
    private ReviewRepository reviewRepository;
    @Autowired
    private SettlementRepository settlementRepository;
    @Autowired
    private EngineerProfileRepository engineerProfileRepository;

    private User customer;
    private User engineer;
    private Agencies agency;
    private AsRequest asRequest;
    private String engineerToken;

    @BeforeEach
    void setUp() {
        Regions region = regionRepository.save(Regions.create("서울 강남구", null, 2, 0));

        agency = agenciesRepository.save(Agencies.builder()
                .agencyName("강남센터")
                .businessNumber("123")
                .agencyFeeRate(5.0)
                .approvalStatus(AgencyStatus.APPROVED)
                .build());

        engineer = userRepository.save(User.builder()
                .email("eng@test.com")
                .passwordHash("hash")
                .name("이수리")
                .role(Role.ENGINEER)
                .regionId(region)
                .agency(agency)
                .build());

        customer = userRepository.save(User.builder()
                .email("cust@test.com")
                .passwordHash("hash")
                .name("김고객")
                .role(Role.CUSTOMER)
                .regionId(region)
                .addressDetail("101동 1001호")
                .build());

        EngineerProfile engineerProfile = EngineerProfile.createInitial(engineer);
        engineerProfile.completeLms();
        engineerProfileRepository.save(engineerProfile);

        ApplianceCategory category = categoryRepository.save(ApplianceCategory.createRoot("TV", 1));

        Appliance appliance = applianceRepository.save(Appliance.create(
                customer,
                category,
                "LG",
                "올레드",
                "SN-001",
                LocalDate.now(),
                null,
                RegisterMethod.MANUAL
        ));

        Symptom symptom = symptomRepository.save(Symptom.builder()
                .category(category)
                .symptomCode("ERR")
                .symptomName("화면 깨짐")
                .build());

        asRequest = asRequestRepository.save(AsRequest.builder()
                .customer(customer)
                .appliance(appliance)
                .symptom(symptom)
                .visitRegion(region)
                .visitAddressDetail("101동")
                .scheduledDate(LocalDate.now())
                .scheduledTime("14:00")
                .build());

        AsAssignment assignment = AsAssignment.create(asRequest, engineer, agency, AssignType.AUTO);
        assignment.accept();
        asAssignmentRepository.save(assignment);

        asRequest.processAssignment(agency);
        asRequest.acceptAssignment();
        asRequest.depart();
        asRequest.arrive();
        asRequest.startWork();
        asRequestRepository.save(asRequest);

        asStatusLogRepository.save(AsStatusLog.builder()
                .asRequest(asRequest)
                .changedBy(engineer)
                .fromStatus("ENGINEER_ARRIVED")
                .toStatus("IN_PROGRESS")
                .memo("수리 시작")
                .build());

        workReportRepository.save(WorkReport.builder()
                .asRequest(asRequest)
                .engineer(engineer)
                .diagnosisResult(DiagnosisResult.REPAIRED)
                .workDurationMin(60)
                .finalAmount(50000)
                .build());

        Payment payment = paymentRepository.save(Payment.builder()
                .asRequest(asRequest)
                .customer(customer)
                .amount(50000)
                .build());

        payment.markSuccess();
        paymentRepository.flush();

        Settlement settlement = settlementRepository.save(Settlement.create(
                payment,
                asRequest,
                engineer,
                agency,
                50000,
                5000,
                new BigDecimal("10.00"),
                2500,
                new BigDecimal("5.00"),
                42500
        ));
        settlement.approve();
        settlementRepository.flush();

        Review review = Review.create(asRequest, customer, engineer, 5, "매우 친절하십니다.");
        reviewRepository.save(review);

        engineerToken = jwtProvider.generateAccessToken(
                engineer.getId(),
                engineer.getEmail(),
                "ENGINEER",
                agency.getId()
        );
    }

    @Test
    @DisplayName("성공: 기사용 고객 상세 API 호출 시 고객 정보와 수리 이력을 반환한다.")
    void getCustomerDetail_Integration_Success() throws Exception {
        mockMvc.perform(get("/api/engineer/customers/{customerId}", customer.getId())
                        .header("Authorization", "Bearer " + engineerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.customerId").value(customer.getId()))
                .andExpect(jsonPath("$.name").value("김고객"))
                .andExpect(jsonPath("$.region").value("서울 강남구"))
                .andExpect(jsonPath("$.asHistory[0].productName").value("LG 올레드"))
                .andExpect(jsonPath("$.asHistory[0].diagnosisResult").value("REPAIRED"));
    }

    @Test
    @DisplayName("성공: 분리된 정산 서비스 API 호출 시 최신순으로 정산 내역이 페이징되어 반환된다.")
    void getSettlements_Integration_Success() throws Exception {
        mockMvc.perform(get("/api/engineer/settlements")
                        .param("page", "0")
                        .param("size", "10")
                        .header("Authorization", "Bearer " + engineerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].settlementId").exists())
                .andExpect(jsonPath("$.content[0].requestId").value("AS-" + asRequest.getCreatedAt().format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd")) + "-" + String.format("%04d", asRequest.getId())))
                .andExpect(jsonPath("$.content[0].productName").value("LG 올레드"))
                .andExpect(jsonPath("$.content[0].grossAmount").value(50000))
                .andExpect(jsonPath("$.content[0].engineerNetAmount").value(42500))
                .andExpect(jsonPath("$.content[0].status").value("PENDING"))
                .andExpect(jsonPath("$.content[0].paidAt").value("미정"));
    }

    @Test
    @DisplayName("성공: 리뷰 통계 API 호출 시 평점 분포 데이터(Map 형태)와 평균 평점을 정확히 반환한다.")
    void getReviewStats_Integration_Success() throws Exception {
        mockMvc.perform(get("/api/engineer/reviews/stats")
                        .header("Authorization", "Bearer " + engineerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.avgRating").value(5.0))
                .andExpect(jsonPath("$.totalReviews").value(1))
                .andExpect(jsonPath("$.ratingDistribution['5']").value(1))
                .andExpect(jsonPath("$.ratingDistribution['4']").value(0))
                .andExpect(jsonPath("$.ratingDistribution['1']").value(0));
    }

    @Test
    @DisplayName("성공: 대시보드 API 호출 시 오늘 진행 중인 작업의 currentRequestId와 카드별 식별자를 정확히 포함한다.")
    void getDashboardData_Integration_Success() throws Exception {
        mockMvc.perform(get("/api/engineer/dashboard")
                        .header("Authorization", "Bearer " + engineerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.currentWorkStatus").value("IN_PROGRESS"))
                .andExpect(jsonPath("$.currentRequestId").value(asRequest.getId()))
                .andExpect(jsonPath("$.todaySchedules[0].requestId").value(asRequest.getId()));
    }

    @Test
    @DisplayName("성공: 실적/정산 요약 API 호출 시 전월 대비 비교 패널 데이터와 진행/취소 건수를 누락 없이 반환한다.")
    void getSettlementSummary_Integration_Success() throws Exception {
        mockMvc.perform(get("/api/engineer/settlements/summary")
                        .header("Authorization", "Bearer " + engineerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.inProgressCount").exists())
                .andExpect(jsonPath("$.cancelledCount").exists())
                .andExpect(jsonPath("$.settlementSummary.engineerNetAmount").value(42500))
                .andExpect(jsonPath("$.monthlyComparison.thisMonthNetAmount").exists())
                .andExpect(jsonPath("$.monthlyComparison.netAmountDiff").exists());
    }
}
