package com.careflow.agency.controller;

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
import com.careflow.region.entity.Regions;
import com.careflow.region.repository.RegionRepository;
import com.careflow.symptom.entity.Symptom;
import com.careflow.symptom.repository.SymptomRepository;
import com.careflow.user.entity.User;
import com.careflow.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("local")
@Sql(scripts = "/as_request_cleanup.sql", executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
@DisplayName("AgencyEngineerTaskSchedule 통합 테스트 (H2)")
class AgencyEngineerTaskScheduleIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private JwtProvider jwtProvider;

    @Autowired private UserRepository userRepository;
    @Autowired private AgenciesRepository agenciesRepository;
    @Autowired private RegionRepository regionRepository;
    @Autowired private ApplianceCategoryRepository categoryRepository;
    @Autowired private ApplianceRepository applianceRepository;
    @Autowired private SymptomRepository symptomRepository;
    @Autowired private AsRequestRepository asRequestRepository;
    @Autowired private AsAssignmentRepository asAssignmentRepository;

    // ── 공통 픽스처 ──
    private User agencyManager;
    private User engineer;
    private User customer;
    private Agencies agency;
    private Regions region;
    private Appliance appliance;
    private Symptom symptom;
    private String agencyToken;

    private static final LocalDate TARGET_DATE = LocalDate.of(2026, 6, 1);

    @BeforeEach
    void setUp() {
        region = regionRepository.save(Regions.create("강남구", null, 2, 0));

        ApplianceCategory rootCat = categoryRepository.save(ApplianceCategory.createRoot("에어컨", 1));
        ApplianceCategory category = categoryRepository.save(ApplianceCategory.createChild("에어컨 소분류", rootCat, 1));

        agency = agenciesRepository.save(Agencies.builder()
                .agencyName("테스트대행사").businessNumber("BIZ-001")
                .agencyAddress("서울 강남구").agencyFeeRate(5.0)
                .approvalStatus(AgencyStatus.APPROVED).build());

        agencyManager = userRepository.save(User.builder()
                .email("manager@agencytest.com").passwordHash("hashed")
                .name("대행사관리자").phone("010-1111-2222").role(Role.AGENCY).agency(agency).build());

        agencyToken = jwtProvider.generateAccessToken(
                agencyManager.getId(), agencyManager.getEmail(), "AGENCY", agency.getId());

        customer = userRepository.save(User.builder()
                .email("customer@agencytest.com").passwordHash("hashed")
                .name("홍길동").phone("010-3333-4444").role(Role.CUSTOMER).build());

        engineer = userRepository.save(User.builder()
                .email("engineer@agencytest.com").passwordHash("hashed")
                .name("테스트기사").phone("010-5555-6666").role(Role.ENGINEER).agency(agency).build());

        appliance = applianceRepository.save(Appliance.create(
                customer, category, "삼성", "비스포크 냉장고",
                null, null, null, RegisterMethod.MANUAL));

        symptom = symptomRepository.save(Symptom.builder()
                .category(category).symptomCode("COOLING_FAIL").symptomName("냉방 불량").build());
    }

    // ─────────────────────────────────────────────
    //  GET /api/agency/engineers/{id}/schedule?date=
    // ─────────────────────────────────────────────
    @Nested
    @DisplayName("GET /api/agency/engineers/{engineerUserId}/schedule — 소속 기사 작업 일정 조회")
    class GetAgencyEngineerTaskSchedule {

        @Test
        @DisplayName("성공: 당일 배정 작업 2건 반환 — 필드값 DB 값과 일치 검증")
        void success_twoTasks_fieldMatch() throws Exception {
            AsRequest req1 = saveRequest(TARGET_DATE);
            AsRequest req2 = saveRequest(TARGET_DATE);
            saveAssignment(req1, "ACCEPTED");
            saveAssignment(req2, "ACCEPTED");

            mockMvc.perform(get("/api/agency/engineers/{engineerUserId}/schedule", engineer.getId())
                            .param("date", TARGET_DATE.toString())
                            .header("Authorization", "Bearer " + agencyToken))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.length()").value(2))
                    .andExpect(jsonPath("$[0].customerName").value("홍길동"))
                    .andExpect(jsonPath("$[0].applianceBrand").value("삼성"))
                    .andExpect(jsonPath("$[0].symptomName").value("냉방 불량"))
                    .andExpect(jsonPath("$[0].visitRegionName").value("강남구"))
                    .andExpect(jsonPath("$[0].assignmentStatus").value("ACCEPTED"));
        }

        @Test
        @DisplayName("성공: REJECTED 배정은 결과에서 제외")
        void success_rejected_excluded() throws Exception {
            AsRequest req1 = saveRequest(TARGET_DATE);
            AsRequest req2 = saveRequest(TARGET_DATE);
            AsRequest req3 = saveRequest(TARGET_DATE);
            saveAssignment(req1, "ACCEPTED");
            saveAssignment(req2, "ACCEPTED");
            saveAssignment(req3, "REJECTED");

            mockMvc.perform(get("/api/agency/engineers/{engineerUserId}/schedule", engineer.getId())
                            .param("date", TARGET_DATE.toString())
                            .header("Authorization", "Bearer " + agencyToken))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.length()").value(2));
        }

        @Test
        @DisplayName("성공: 배정 없는 날짜 — 빈 배열 반환")
        void success_noTask_emptyArray() throws Exception {
            mockMvc.perform(get("/api/agency/engineers/{engineerUserId}/schedule", engineer.getId())
                            .param("date", TARGET_DATE.toString())
                            .header("Authorization", "Bearer " + agencyToken))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.length()").value(0));
        }

        @Test
        @DisplayName("실패: 타 대행사 소속 기사 조회 — 401 Unauthorized (IllegalAccessException)")
        void fail_otherAgencyEngineer_401() throws Exception {
            // 다른 대행사와 그 소속 기사 생성
            Agencies otherAgency = agenciesRepository.save(Agencies.builder()
                    .agencyName("타대행사").businessNumber("BIZ-002")
                    .agencyAddress("서울 서초구").agencyFeeRate(3.0)
                    .approvalStatus(AgencyStatus.APPROVED).build());

            User otherEngineer = userRepository.save(User.builder()
                    .email("other@agencytest.com").passwordHash("hashed")
                    .name("타대행사기사").phone("010-9999-8888").role(Role.ENGINEER).agency(otherAgency).build());

            mockMvc.perform(get("/api/agency/engineers/{engineerUserId}/schedule", otherEngineer.getId())
                            .param("date", TARGET_DATE.toString())
                            .header("Authorization", "Bearer " + agencyToken))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("실패: 존재하지 않는 기사 ID — 404 Not Found")
        void fail_engineerNotFound_404() throws Exception {
            mockMvc.perform(get("/api/agency/engineers/99999/schedule")
                            .param("date", TARGET_DATE.toString())
                            .header("Authorization", "Bearer " + agencyToken))
                    .andExpect(status().isNotFound());
        }

        @Test
        @DisplayName("실패: 인증 없음 — 401 Unauthorized")
        void fail_noToken_401() throws Exception {
            mockMvc.perform(get("/api/agency/engineers/{engineerUserId}/schedule", engineer.getId())
                            .param("date", TARGET_DATE.toString()))
                    .andExpect(status().isUnauthorized());
        }
    }

    // ── 픽스처 헬퍼 ──────────────────────────────────────────────────

    private AsRequest saveRequest(LocalDate date) {
        AsRequest req = AsRequest.builder()
                .customer(customer)
                .appliance(appliance)
                .symptom(symptom)
                .visitRegion(region)
                .visitAddressDetail("테헤란로 123")
                .scheduledDate(date)
                .scheduledTime("10:00")
                .build();
        req.processAssignment(agency);
        return asRequestRepository.save(req);
    }

    private void saveAssignment(AsRequest req, String status) {
        AsAssignment assignment = AsAssignment.create(req, engineer, agency, AssignType.MANUAL);
        AsAssignment saved = asAssignmentRepository.saveAndFlush(assignment);
        if (!"WAITING".equals(status)) {
            asAssignmentRepository.updateStatus(saved.getId(), status);
        }
    }
}
