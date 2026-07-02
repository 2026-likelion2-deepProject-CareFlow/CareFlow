// 파일 경로: src/test/java/com/careflow/engineer/EngineerPhase4IntegrationTest.java
package com.careflow.engineer.bugfix;

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
import com.careflow.notification.entity.Notification;
import com.careflow.notification.repository.NotificationRepository;
import com.careflow.region.entity.Regions;
import com.careflow.region.repository.RegionRepository;
import com.careflow.review.entity.Review;
import com.careflow.review.repository.ReviewRepository;
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
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("local")
@Transactional // 데이터 롤백 보장
@Sql(scripts = "/cleanup.sql", executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
@DisplayName("기사 미구현 API 4단계 (동적 필터 및 페이징) 통합 테스트")
class EngineerPhase4IntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private JwtProvider jwtProvider;

    @MockitoBean private StringRedisTemplate stringRedisTemplate; // 🌟 필수 우회

    @Autowired private UserRepository userRepository;
    @Autowired private AgenciesRepository agenciesRepository;
    @Autowired private RegionRepository regionRepository;
    @Autowired private ApplianceCategoryRepository categoryRepository;
    @Autowired private ApplianceRepository applianceRepository;
    @Autowired private SymptomRepository symptomRepository;
    @Autowired private AsRequestRepository asRequestRepository;
    @Autowired private AsAssignmentRepository asAssignmentRepository;
    @Autowired private NotificationRepository notificationRepository;
    @Autowired private ReviewRepository reviewRepository;

    private User engineer;
    private String engineerToken;

    @BeforeEach
    void setUp() {
        Regions region = regionRepository.save(Regions.create("서울 서초구", null, 2, 0));
        Agencies agency = agenciesRepository.save(Agencies.builder().agencyName("서초센터").businessNumber("123").approvalStatus(AgencyStatus.APPROVED).agencyFeeRate(5.0).build());

        engineer = userRepository.save(User.builder().email("eng4@test.com").passwordHash("hash").name("사기사").role(Role.ENGINEER).agency(agency).build());
        User customer = userRepository.save(User.builder().email("cust4@test.com").passwordHash("hash").name("홍길동").role(Role.CUSTOMER).regionId(region).build());

        engineerToken = jwtProvider.generateAccessToken(engineer.getId(), engineer.getEmail(), "ENGINEER", agency.getId());

        // 1. 알림 데이터 생성 (AS_STATUS, LMS 2종류)
        notificationRepository.save(Notification.builder().user(engineer).type("AS_STATUS").title("배차 알림").body("내용").build());
        notificationRepository.save(Notification.builder().user(engineer).type("LMS").title("교육 알림").body("내용").build());

        // 2. 고객-기사 연결 (A/S 요청 및 배차)
        ApplianceCategory cat = categoryRepository.save(ApplianceCategory.createRoot("세탁기", 1));
        Appliance appliance = applianceRepository.save(Appliance.create(customer, cat, "LG", "트롬", null, null, null, RegisterMethod.MANUAL));
        Symptom symptom = symptomRepository.save(Symptom.builder().category(cat).symptomCode("ERR").symptomName("소음").build());

        AsRequest asRequest = AsRequest.builder().customer(customer).appliance(appliance).symptom(symptom).visitRegion(region).visitAddressDetail("101호").scheduledDate(LocalDate.now()).scheduledTime("14:00").build();
        asRequestRepository.save(asRequest);
        asAssignmentRepository.save(AsAssignment.create(asRequest, engineer, agency, AssignType.AUTO));

        // 3. 리뷰 데이터 생성 (5점짜리 1개)
        reviewRepository.save(Review.builder().asRequest(asRequest).customer(customer).engineer(engineer).rating(5).content("최고").build());
    }

    @Test
    @DisplayName("성공: 알림 조회 시 type 필터가 완벽하게 작동한다.")
    void getNotifications_Filter_Success() throws Exception {
        mockMvc.perform(get("/api/engineer/notifications?type=AS_STATUS")
                        .header("Authorization", "Bearer " + engineerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].type").value("AS_STATUS"));
    }

    @Test
    @DisplayName("성공: 리뷰 조회 시 rating 필터가 완벽하게 작동한다.")
    void getReviews_Filter_Success() throws Exception {
        mockMvc.perform(get("/api/engineer/reviews?rating=5")
                        .header("Authorization", "Bearer " + engineerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].rating").value(5));

        // 필터에 없는 점수를 검색하면 0건이 나와야 함
        mockMvc.perform(get("/api/engineer/reviews?rating=3")
                        .header("Authorization", "Bearer " + engineerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(0));
    }

    @Test
    @DisplayName("성공: 기사가 담당했던 고객 목록을 중복 없이 조회한다.")
    void getMyCustomers_Success() throws Exception {
        mockMvc.perform(get("/api/engineer/customers")
                        .header("Authorization", "Bearer " + engineerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].name").value("홍길동"))
                .andExpect(jsonPath("$.content[0].region").value("서울 서초구"));
    }
}