package com.careflow.engineer.bugfix;

import com.careflow.agency.entity.Agencies;
import com.careflow.agency.repository.AgenciesRepository;
import com.careflow.appliance.entity.Appliance;
import com.careflow.appliance.entity.ApplianceCategory;
import com.careflow.appliance.repository.ApplianceCategoryRepository;
import com.careflow.appliance.repository.ApplianceRepository;
import com.careflow.as_request.entity.AsRequest;
import com.careflow.as_request.repository.AsRequestRepository;
import com.careflow.auth.security.JwtProvider;
import com.careflow.common.enums.AgencyStatus;
import com.careflow.common.enums.RegisterMethod;
import com.careflow.common.enums.Role;
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
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("local")
@Transactional
@TestPropertySource(properties = "spring.quartz.auto-startup=false")
@Sql(scripts = "/cleanup.sql", executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
@DisplayName("기사 STEP 2 & 3 (API 및 리팩토링) 통합 테스트")
class EngineerStep2And3IntegrationTest {

    @MockitoBean
    private StringRedisTemplate stringRedisTemplate;

    @Autowired
    private MockMvc mockMvc;

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
    private WorkReportRepository workReportRepository;

    @Autowired
    private PaymentRepository paymentRepository;

    @Autowired
    private SettlementRepository settlementRepository;

    private User engineer;
    private User customer;
    private Agencies agency;
    private String engineerToken;

    @BeforeEach
    void setUp() {
        Regions region = regionRepository.save(Regions.create("서울 강남구", null, 2, 0));

        agency = agenciesRepository.save(
                Agencies.builder()
                        .agencyName("강남센터")
                        .businessNumber("123")
                        .agencyFeeRate(5.0)
                        .approvalStatus(AgencyStatus.APPROVED)
                        .build()
        );

        engineer = userRepository.save(
                User.builder()
                        .email("eng@test.com")
                        .passwordHash("encoded-password")
                        .name("이수리")
                        .phone("010-1111-2222")
                        .role(Role.ENGINEER)
                        .regionId(region)
                        .agency(agency)
                        .build()
        );

        customer = userRepository.save(
                User.builder()
                        .email("cust@test.com")
                        .passwordHash("encoded-password")
                        .name("김고객")
                        .phone("010-3333-4444")
                        .role(Role.CUSTOMER)
                        .regionId(region)
                        .addressDetail("101동 1001호")
                        .build()
        );

        ApplianceCategory category = categoryRepository.save(
                ApplianceCategory.createRoot("TV", 1)
        );

        Appliance appliance = applianceRepository.save(
                Appliance.create(
                        customer,
                        category,
                        "LG",
                        "올레드",
                        "SN-001",
                        LocalDate.now(),
                        null,
                        RegisterMethod.MANUAL
                )
        );

        Symptom symptom = symptomRepository.save(
                Symptom.builder()
                        .category(category)
                        .symptomCode("ERR")
                        .symptomName("화면 깨짐")
                        .build()
        );

        AsRequest asRequest = asRequestRepository.save(
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



        WorkReport report = workReportRepository.save(
                WorkReport.builder()
                        .asRequest(asRequest)
                        .engineer(engineer)
                        .diagnosisResult(DiagnosisResult.REPAIRED)
                        .workDurationMin(60)
                        .finalAmount(50000)
                        .build()
        );

        Payment payment = paymentRepository.save(
                Payment.builder()
                        .asRequest(asRequest)
                        .customer(customer)
                        .amount(50000)
                        .build()
        );
        payment.markSuccess();
        paymentRepository.flush();


        Settlement settlement = settlementRepository.save(
                Settlement.create(
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
                )
        );
        settlement.markPaid();
        settlementRepository.flush();

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
                .andExpect(jsonPath("$.content[0].grossAmount").value(50000))
                .andExpect(jsonPath("$.content[0].engineerNetAmount").value(42500))
                .andExpect(jsonPath("$.content[0].status").value("PAID"));
    }
}
