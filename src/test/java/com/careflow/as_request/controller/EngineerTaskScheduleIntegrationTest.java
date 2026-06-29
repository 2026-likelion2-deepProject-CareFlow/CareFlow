package com.careflow.as_request.controller;

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
@DisplayName("EngineerTaskSchedule 통합 테스트 (H2)")
class EngineerTaskScheduleIntegrationTest {

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
    private User engineer;
    private User customer;
    private Agencies agency;
    private Regions region;
    private Appliance appliance;
    private Symptom symptom;
    private String engineerToken;

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

        customer = userRepository.save(User.builder()
                .email("customer@schedule.com").passwordHash("hashed")
                .name("홍길동").phone("010-1111-2222").role(Role.CUSTOMER).build());

        engineer = userRepository.save(User.builder()
                .email("engineer@schedule.com").passwordHash("hashed")
                .name("테스트기사").phone("010-3333-4444").role(Role.ENGINEER).agency(agency).build());

        engineerToken = jwtProvider.generateAccessToken(
                engineer.getId(), engineer.getEmail(), "ENGINEER");

        appliance = applianceRepository.save(Appliance.create(
                customer, category, "삼성", "비스포크 냉장고",
                null, null, null, RegisterMethod.MANUAL));

        symptom = symptomRepository.save(Symptom.builder()
                .category(category).symptomCode("COOLING_FAIL").symptomName("냉방 불량").build());
    }

    // ─────────────────────────────────────────────
    //  GET /api/engineer/schedule?date=
    // ─────────────────────────────────────────────
    @Nested
    @DisplayName("GET /api/engineer/schedule — 기사 본인 작업 일정 조회")
    class GetEngineerTaskSchedule {

        @Test
        @DisplayName("성공: 당일 배정 작업 2건 반환 — 필드값 DB 값과 일치 검증")
        void success_twoTasks_fieldMatch() throws Exception {
            // given: 동일 날짜 배정 2건 (ACCEPTED)
            AsRequest req1 = saveRequest(TARGET_DATE, "삼성", "냉방 불량");
            AsRequest req2 = saveRequest(TARGET_DATE, "삼성", "냉방 불량");
            saveAssignment(req1, engineer, "ACCEPTED");
            saveAssignment(req2, engineer, "ACCEPTED");

            // when & then
            mockMvc.perform(get("/api/engineer/schedule")
                            .param("date", TARGET_DATE.toString())
                            .header("Authorization", "Bearer " + engineerToken))
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
            // given: ACCEPTED 2건, REJECTED 1건
            AsRequest req1 = saveRequest(TARGET_DATE, "삼성", "냉방 불량");
            AsRequest req2 = saveRequest(TARGET_DATE, "삼성", "냉방 불량");
            AsRequest req3 = saveRequest(TARGET_DATE, "삼성", "냉방 불량");
            saveAssignment(req1, engineer, "ACCEPTED");
            saveAssignment(req2, engineer, "ACCEPTED");
            saveAssignment(req3, engineer, "REJECTED");

            mockMvc.perform(get("/api/engineer/schedule")
                            .param("date", TARGET_DATE.toString())
                            .header("Authorization", "Bearer " + engineerToken))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.length()").value(2));
        }

        @Test
        @DisplayName("성공: 타 기사 배정은 결과에 포함되지 않음 — 데이터 격리")
        void success_otherEngineer_excluded() throws Exception {
            // given: 기사A 1건, 기사B 1건 (같은 날짜)
            User otherEngineer = userRepository.save(User.builder()
                    .email("other@schedule.com").passwordHash("hashed")
                    .name("타기사").phone("010-9999-8888").role(Role.ENGINEER).agency(agency).build());

            AsRequest myReq = saveRequest(TARGET_DATE, "삼성", "냉방 불량");
            AsRequest otherReq = saveRequest(TARGET_DATE, "삼성", "냉방 불량");
            saveAssignment(myReq, engineer, "ACCEPTED");
            saveAssignment(otherReq, otherEngineer, "ACCEPTED");

            // when: engineer 토큰으로 조회 → 본인 건만 반환
            mockMvc.perform(get("/api/engineer/schedule")
                            .param("date", TARGET_DATE.toString())
                            .header("Authorization", "Bearer " + engineerToken))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.length()").value(1))
                    .andExpect(jsonPath("$[0].requestId").value(myReq.getId()));
        }

        @Test
        @DisplayName("성공: 다른 날짜 배정은 포함되지 않음")
        void success_differentDate_excluded() throws Exception {
            // given: TARGET_DATE 1건, 다음 날 1건
            AsRequest req1 = saveRequest(TARGET_DATE, "삼성", "냉방 불량");
            AsRequest req2 = saveRequest(TARGET_DATE.plusDays(1), "삼성", "냉방 불량");
            saveAssignment(req1, engineer, "ACCEPTED");
            saveAssignment(req2, engineer, "ACCEPTED");

            mockMvc.perform(get("/api/engineer/schedule")
                            .param("date", TARGET_DATE.toString())
                            .header("Authorization", "Bearer " + engineerToken))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.length()").value(1));
        }

        @Test
        @DisplayName("성공: 배정 없는 날짜 — 빈 배열 반환")
        void success_noTask_emptyArray() throws Exception {
            mockMvc.perform(get("/api/engineer/schedule")
                            .param("date", TARGET_DATE.toString())
                            .header("Authorization", "Bearer " + engineerToken))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.length()").value(0));
        }

        @Test
        @DisplayName("실패: 인증 없음 — 401 Unauthorized")
        void fail_noToken_401() throws Exception {
            mockMvc.perform(get("/api/engineer/schedule")
                            .param("date", TARGET_DATE.toString()))
                    .andExpect(status().isUnauthorized());
        }
    }

    // ── 픽스처 헬퍼 ──────────────────────────────────────────────────

    private AsRequest saveRequest(LocalDate date, String brand, String symptomName) {
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

    private AsAssignment saveAssignment(AsRequest req, User engineer, String status) {
        AsAssignment assignment = AsAssignment.create(req, engineer, agency, AssignType.MANUAL);
        // status 필드는 String 이므로 리플렉션 없이 빌더 우회 — 초기 status("WAITING")을 원하는 값으로 대체
        if (!"WAITING".equals(status)) {
            AsAssignment withStatus = forceStatus(assignment, status);
            return asAssignmentRepository.save(withStatus);
        }
        return asAssignmentRepository.save(assignment);
    }

    /**
     * AsAssignment.status 는 도메인 메서드가 없으므로
     * 테스트 픽스처용으로 Builder 재활용 후 JPA save 로 덮어씀.
     * — create() → status="WAITING" 이 기본값이므로
     *   REJECTED / ACCEPTED 픽스처가 필요한 경우 직접 쿼리로 처리.
     */
    private AsAssignment forceStatus(AsAssignment assignment, String status) {
        // H2 에서는 saveAndFlush 후 네이티브 업데이트로 status 강제 변경
        AsAssignment saved = asAssignmentRepository.saveAndFlush(assignment);
        asAssignmentRepository.updateStatus(saved.getId(), status);
        return asAssignmentRepository.findById(saved.getId()).orElseThrow();
    }
}
