package com.careflow.assignment.controller;

import com.careflow.agency.entity.Agencies;
import com.careflow.agency.repository.AgenciesRepository;
import com.careflow.appliance.entity.Appliance;
import com.careflow.appliance.entity.ApplianceCategory;
import com.careflow.appliance.repository.ApplianceCategoryRepository;
import com.careflow.appliance.repository.ApplianceRepository;
import com.careflow.as_request.entity.AsRequest;
import com.careflow.as_request.repository.AsRequestRepository;
import com.careflow.assignment.dto.AssignmentRejectRequest;
import com.careflow.assignment.entity.AsAssignment;
import com.careflow.assignment.repository.AsAssignmentRepository;
import com.careflow.auth.security.JwtProvider;
import com.careflow.common.enums.AgencyStatus;
import com.careflow.common.enums.AssignType;
import com.careflow.common.enums.RegisterMethod;
import com.careflow.common.enums.Role;
import com.careflow.region.entity.Regions;
import com.careflow.region.repository.RegionRepository;
import com.careflow.symptom.entity.Symptom;
import com.careflow.symptom.repository.SymptomRepository;
import com.careflow.user.entity.User;
import com.careflow.user.repository.UserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("local")
@Sql(scripts = "/cleanup.sql", executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
@DisplayName("EngineerAssignment 통합 테스트 (H2)")
class EngineerAssignmentIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private JwtProvider jwtProvider;

    @Autowired private UserRepository userRepository;
    @Autowired private AgenciesRepository agenciesRepository;
    @Autowired private RegionRepository regionRepository;
    @Autowired private ApplianceCategoryRepository categoryRepository;
    @Autowired private ApplianceRepository applianceRepository;
    @Autowired private SymptomRepository symptomRepository;
    @Autowired private AsRequestRepository asRequestRepository;
    @Autowired private AsAssignmentRepository asAssignmentRepository;

    @MockitoBean private StringRedisTemplate stringRedisTemplate;

    private User engineer;
    private AsAssignment savedAssignment;
    private String engineerToken;

    @BeforeEach
    void setUp() {
        // 1. 기본 데이터 세팅
        Regions region = regionRepository.save(Regions.create("서울 강남구", null, 2, 0));
        Agencies agency = agenciesRepository.save(Agencies.builder().agencyName("강남센터").businessNumber("123").approvalStatus(AgencyStatus.APPROVED).agencyFeeRate(5.0).build());

        engineer = userRepository.save(User.builder().email("eng@test.com").passwordHash("hash").name("이엔지").role(Role.ENGINEER).agency(agency).build());
        User customer = userRepository.save(User.builder().email("cust@test.com").passwordHash("hash").name("김고객").role(Role.CUSTOMER).build());

        ApplianceCategory cat = categoryRepository.save(ApplianceCategory.createRoot("세탁기", 1));
        Appliance appliance = applianceRepository.save(Appliance.create(customer, cat, "LG", "트롬", null, null, null, RegisterMethod.MANUAL));
        Symptom symptom = symptomRepository.save(Symptom.builder().category(cat).symptomCode("ERR1").symptomName("소음").build());

        // 2. A/S 요청 및 배차 생성 (WAITING 상태)
        AsRequest asRequest = AsRequest.builder()
                .customer(customer).appliance(appliance).symptom(symptom).visitRegion(region)
                .visitAddressDetail("101동 101호").scheduledDate(LocalDate.now()).scheduledTime("14:00")
                .build();
        asRequest.processAssignment(agency);
        asRequestRepository.save(asRequest);

        savedAssignment = asAssignmentRepository.save(AsAssignment.create(asRequest, engineer, agency, AssignType.AUTO));

        // 3. 토큰 발급
        engineerToken = jwtProvider.generateAccessToken(engineer.getId(), engineer.getEmail(), "ENGINEER", agency.getId());
    }

    @Test
    @DisplayName("성공: 배정 수락 시 DB의 상태가 ACCEPTED로 안전하게 변경된다.")
    void acceptAssignment_Integration_Success() throws Exception {
        mockMvc.perform(put("/api/engineer/assignments/" + savedAssignment.getId() + "/accept")
                        .header("Authorization", "Bearer " + engineerToken))
                .andExpect(status().isOk());

        // DB 검증 (더티 체킹 정상 작동 확인)
        AsAssignment updatedAssignment = asAssignmentRepository.findById(savedAssignment.getId()).orElseThrow();
        assertThat(updatedAssignment.getStatus()).isEqualTo("ACCEPTED");

        AsRequest updatedRequest = asRequestRepository.findById(updatedAssignment.getAsRequest().getId()).orElseThrow();
        assertThat(updatedRequest.getStatus().name()).isEqualTo("ACCEPTED");
    }

    @Test
    @DisplayName("성공: 기사용 배정 목록 조회 시 필요한 DTO 형태로 정상 반환된다.")
    void getAssignments_Integration_Success() throws Exception {
        mockMvc.perform(get("/api/engineer/assignments?status=WAITING")
                        .header("Authorization", "Bearer " + engineerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].assignStatus").value("WAITING"))
                .andExpect(jsonPath("$.content[0].productName").value("LG 세탁기"))
                .andExpect(jsonPath("$.content[0].customerName").value("김고객"));
    }
}