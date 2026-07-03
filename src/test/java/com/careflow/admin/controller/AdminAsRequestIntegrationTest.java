package com.careflow.admin.controller;

import com.careflow.appliance.entity.Appliance;
import com.careflow.appliance.entity.ApplianceCategory;
import com.careflow.appliance.repository.ApplianceCategoryRepository;
import com.careflow.appliance.repository.ApplianceRepository;
import com.careflow.as_request.entity.AsRequest;
import com.careflow.as_request.repository.AsRequestRepository;
import com.careflow.auth.security.JwtProvider;
import com.careflow.common.enums.AsStatus;
import com.careflow.common.enums.RegisterMethod;
import com.careflow.common.enums.Role;
import com.careflow.region.entity.Regions;
import com.careflow.region.repository.RegionRepository;
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
@DisplayName("AdminAsRequest 통합 테스트 (H2)")
class AdminAsRequestIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private JwtProvider jwtProvider;

    // 통합 테스트 시 Redis 우회
    @MockitoBean private StringRedisTemplate stringRedisTemplate;

    @Autowired private UserRepository userRepository;
    @Autowired private RegionRepository regionRepository;
    @Autowired private ApplianceCategoryRepository categoryRepository;
    @Autowired private ApplianceRepository applianceRepository;
    @Autowired private SymptomRepository symptomRepository;
    @Autowired private AsRequestRepository asRequestRepository;

    private User admin;
    private String adminToken;

    @BeforeEach
    void setUp() {
        // 1. 기초 데이터 세팅
        Regions region = regionRepository.save(Regions.create("서울 강남구", null, 2, 0));

        admin = userRepository.save(User.builder().email("admin@test.com").passwordHash("hash").name("최고관리자").role(Role.ADMIN).build());
        User customer = userRepository.save(User.builder().email("cust@test.com").passwordHash("hash").name("홍길동").role(Role.CUSTOMER).build());

        ApplianceCategory cat = categoryRepository.save(ApplianceCategory.createRoot("냉장고", 1));
        Appliance appliance = applianceRepository.save(Appliance.create(customer, cat, "삼성", "비스포크", null, null, null, RegisterMethod.MANUAL));
        Symptom symptom = symptomRepository.save(Symptom.builder().category(cat).symptomCode("ERR1").symptomName("냉각 불량").build());

        // 2. A/S 요청 2건 생성 (1건은 PENDING, 1건은 COMPLETED)
        AsRequest req1 = AsRequest.builder()
                .customer(customer).appliance(appliance).symptom(symptom)
                .visitRegion(region).visitAddressDetail("101동 101호")
                .scheduledDate(LocalDate.now()).scheduledTime("10:00").build();
        asRequestRepository.save(req1); // 기본 상태 PENDING

        AsRequest req2 = AsRequest.builder()
                .customer(customer).appliance(appliance).symptom(symptom)
                .visitRegion(region).visitAddressDetail("101동 202호")
                .scheduledDate(LocalDate.now()).scheduledTime("14:00").build();
        // 도메인 규칙을 무시하고 강제로 상태만 변경하기 위해 Reflection 사용
        ReflectionTestUtils.setField(req2, "status", AsStatus.COMPLETED);
        asRequestRepository.save(req2);

        // 3. ADMIN 토큰 생성
        adminToken = jwtProvider.generateAccessToken(admin.getId(), admin.getEmail(), "ADMIN", null);
    }

    @Test
    @DisplayName("성공: 관리자가 실시간 A/S 현황 통계를 조회하면 DB의 상태별 집계가 반환된다.")
    void getRealTimeStats_Integration_Success() throws Exception {
        mockMvc.perform(get("/api/admin/as-requests/stats")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.PENDING").value(1))
                .andExpect(jsonPath("$.COMPLETED").value(1))
                .andExpect(jsonPath("$.IN_PROGRESS").value(0));
    }

    @Test
    @DisplayName("성공: 관리자가 페이징 리스트 조회 시 JOIN FETCH를 통해 데이터가 안정적으로 반환된다.")
    void getAsRequestsList_Integration_Success() throws Exception {
        mockMvc.perform(get("/api/admin/as-requests")
                        .header("Authorization", "Bearer " + adminToken)
                        .param("page", "0")
                        .param("size", "10"))
                .andExpect(status().isOk())
                // 1) 통계 블록 검증 (필터가 없으므로 전체 2건)
                .andExpect(jsonPath("$.stats.PENDING").value(1))
                .andExpect(jsonPath("$.stats.COMPLETED").value(1))
                // 2) 리스트 블록 검증
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.content[0].customerName").value("홍길동"))
                .andExpect(jsonPath("$.content[0].symptom").value("냉각 불량"));
    }

    @Test
    @DisplayName("성공: 상태 필터를 걸더라도, stats(통계)는 필터가 무시되어 전체 상태 카운트를 유지한다.")
    void getAsRequestsList_WithStatusFilter_Integration_Success() throws Exception {
        // PENDING 상태만 검색 요청
        mockMvc.perform(get("/api/admin/as-requests")
                        .header("Authorization", "Bearer " + adminToken)
                        .param("status", "PENDING"))
                .andExpect(status().isOk())
                // 1) 통계 블록은 status 필터를 무시해야 하므로 COMPLETED가 여전히 1로 나와야 함
                .andExpect(jsonPath("$.stats.PENDING").value(1))
                .andExpect(jsonPath("$.stats.COMPLETED").value(1))
                // 2) 리스트는 PENDING만 필터링되어 1건만 나와야 함
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].status").value("PENDING"));
    }
}