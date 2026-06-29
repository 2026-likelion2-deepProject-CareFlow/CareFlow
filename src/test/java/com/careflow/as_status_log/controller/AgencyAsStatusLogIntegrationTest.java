package com.careflow.as_status_log.controller;

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
import com.careflow.auth.security.JwtProvider;
import com.careflow.common.enums.AgencyStatus;
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
@Sql(scripts = "/as_status_log_cleanup.sql", executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
@DisplayName("AgencyAsStatusLog 통합 테스트 (H2)")
class AgencyAsStatusLogIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private JwtProvider jwtProvider;

    @Autowired private UserRepository userRepository;
    @Autowired private AgenciesRepository agenciesRepository;
    @Autowired private RegionRepository regionRepository;
    @Autowired private ApplianceCategoryRepository categoryRepository;
    @Autowired private ApplianceRepository applianceRepository;
    @Autowired private SymptomRepository symptomRepository;
    @Autowired private AsRequestRepository asRequestRepository;
    @Autowired private AsStatusLogRepository asStatusLogRepository;

    // ── 공통 픽스처 ──
    private User agencyManager;
    private User customer;
    private Agencies agency;
    private Agencies otherAgency;
    private Appliance appliance;
    private Symptom symptom;
    private Regions region;
    private String agencyToken;
    private String customerToken;

    private static final LocalDate SCHED_DATE = LocalDate.of(2026, 7, 1);

    @BeforeEach
    void setUp() {
        region = regionRepository.save(Regions.create("서울특별시 강남구", null, 1, 0));

        ApplianceCategory rootCat = categoryRepository.save(ApplianceCategory.createRoot("에어컨", 1));
        ApplianceCategory cat = categoryRepository.save(ApplianceCategory.createChild("에어컨 소분류", rootCat, 1));

        agency = agenciesRepository.save(Agencies.builder()
                .agencyName("테스트대행사").businessNumber("BIZ-LOG01")
                .agencyAddress("서울 강남구").agencyFeeRate(5.0)
                .approvalStatus(AgencyStatus.APPROVED).build());

        otherAgency = agenciesRepository.save(Agencies.builder()
                .agencyName("타대행사").businessNumber("BIZ-LOG02")
                .agencyAddress("서울 서초구").agencyFeeRate(5.0)
                .approvalStatus(AgencyStatus.APPROVED).build());

        agencyManager = userRepository.save(User.builder()
                .email("manager@logtest.com").passwordHash("hashed")
                .name("대행사관리자").phone("010-9999-8888").role(Role.AGENCY).agency(agency).build());
        agencyToken = jwtProvider.generateAccessToken(
                agencyManager.getId(), agencyManager.getEmail(), "AGENCY");

        customer = userRepository.save(User.builder()
                .email("customer@logtest.com").passwordHash("hashed")
                .name("테스트고객").phone("010-1111-2222").role(Role.CUSTOMER).build());
        customerToken = jwtProvider.generateAccessToken(
                customer.getId(), customer.getEmail(), "CUSTOMER");

        appliance = applianceRepository.save(Appliance.create(
                customer, cat, "삼성", "에어컨 Q9000",
                "SN-LOG001", null, null, RegisterMethod.MANUAL));

        symptom = symptomRepository.save(Symptom.builder()
                .category(cat).symptomCode("COOLING_FAIL").symptomName("냉방 불량").build());
    }

    // ─────────────────────────────────────────────
    //  GET /api/as-status-logs/agency — 이력 목록 조회
    // ─────────────────────────────────────────────
    @Nested
    @DisplayName("GET /api/as-status-logs/agency — 이력 목록 조회")
    class GetStatusLogs {

        @Test
        @DisplayName("성공: 소속 대행사 이력 3건 반환 (최신순)")
        void success_returnsLogsForAgency() throws Exception {
            AsRequest req = saveRequest(agency);
            saveLog(req, agencyManager, null, "WAITING", null);
            saveLog(req, agencyManager, "WAITING", "ENGINEER_DEPARTED", "출발");
            saveLog(req, agencyManager, "ENGINEER_DEPARTED", "ENGINEER_ARRIVED", "도착");

            mockMvc.perform(get("/api/as-status-logs/agency")
                            .header("Authorization", "Bearer " + agencyToken))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.totalCount").value(3))
                    .andExpect(jsonPath("$.logs.length()").value(3))
                    .andExpect(jsonPath("$.logs[0].requestId").value(req.getId()));
        }

        @Test
        @DisplayName("성공: 이력 없으면 totalCount=0, 빈 배열 반환")
        void success_noLogs_empty() throws Exception {
            mockMvc.perform(get("/api/as-status-logs/agency")
                            .header("Authorization", "Bearer " + agencyToken))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.totalCount").value(0))
                    .andExpect(jsonPath("$.logs.length()").value(0));
        }

        @Test
        @DisplayName("성공: 타 대행사 이력은 포함되지 않음 — 데이터 격리")
        void success_dataIsolation_otherAgencyExcluded() throws Exception {
            // 소속 대행사 요청 — 이력 1건
            AsRequest myReq = saveRequest(agency);
            saveLog(myReq, agencyManager, null, "WAITING", null);

            // 타 대행사 요청 — 이력 2건
            User otherManager = userRepository.save(User.builder()
                    .email("other@logtest.com").passwordHash("hashed")
                    .name("타대행사관리자").phone("010-7777-8888").role(Role.AGENCY).agency(otherAgency).build());
            AsRequest otherReq = saveRequest(otherAgency);
            saveLog(otherReq, otherManager, null, "WAITING", null);
            saveLog(otherReq, otherManager, "WAITING", "ENGINEER_DEPARTED", null);

            // 소속 대행사 관리자는 본인 대행사 이력 1건만 조회
            mockMvc.perform(get("/api/as-status-logs/agency")
                            .header("Authorization", "Bearer " + agencyToken))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.totalCount").value(1));
        }

        @Test
        @DisplayName("실패: AGENCY 이외 권한(CUSTOMER)으로 호출 — 401")
        void fail_notAgency_401() throws Exception {
            mockMvc.perform(get("/api/as-status-logs/agency")
                            .header("Authorization", "Bearer " + customerToken))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("실패: 인증 헤더 없이 호출 — 401")
        void fail_noToken_401() throws Exception {
            mockMvc.perform(get("/api/as-status-logs/agency"))
                    .andExpect(status().isUnauthorized());
        }
    }

    // ─────────────────────────────────────────────
    //  GET /api/as-status-logs/agency/status-summary — 상태별 집계
    // ─────────────────────────────────────────────
    @Nested
    @DisplayName("GET /api/as-status-logs/agency/status-summary — 상태별 집계")
    class GetStatusSummary {

        @Test
        @DisplayName("성공: 상태별 집계 정확성 검증")
        void success_correctCounts() throws Exception {
            AsRequest req = saveRequest(agency);
            // WAITING 2건, ENGINEER_DEPARTED 1건, COMPLETED 3건
            saveLog(req, agencyManager, null, "WAITING", null);
            saveLog(req, agencyManager, null, "WAITING", null);
            saveLog(req, agencyManager, "WAITING", "ENGINEER_DEPARTED", null);
            saveLog(req, agencyManager, "WAITING", "COMPLETED", null);
            saveLog(req, agencyManager, "WAITING", "COMPLETED", null);
            saveLog(req, agencyManager, "WAITING", "COMPLETED", null);

            mockMvc.perform(get("/api/agency/work-requests/stats")
                            .header("Authorization", "Bearer " + agencyToken))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.waitingCount").value(2))
                    .andExpect(jsonPath("$.engineerDepartedCount").value(1))
                    .andExpect(jsonPath("$.engineerArrivedCount").value(0))
                    .andExpect(jsonPath("$.inProgressCount").value(0))
                    .andExpect(jsonPath("$.completedCount").value(3));
        }

        @Test
        @DisplayName("성공: 이력 없으면 모든 count = 0")
        void success_noLogs_allZero() throws Exception {
            mockMvc.perform(get("/api/agency/work-requests/stats")
                            .header("Authorization", "Bearer " + agencyToken))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.waitingCount").value(0))
                    .andExpect(jsonPath("$.engineerDepartedCount").value(0))
                    .andExpect(jsonPath("$.engineerArrivedCount").value(0))
                    .andExpect(jsonPath("$.inProgressCount").value(0))
                    .andExpect(jsonPath("$.completedCount").value(0));
        }

        @Test
        @DisplayName("성공: 타 대행사 이력은 집계에서 제외")
        void success_dataIsolation_otherAgencyExcluded() throws Exception {
            // 소속 대행사 — WAITING 1건
            AsRequest myReq = saveRequest(agency);
            saveLog(myReq, agencyManager, null, "WAITING", null);

            // 타 대행사 — COMPLETED 5건 (집계에 포함되면 안 됨)
            User otherManager = userRepository.save(User.builder()
                    .email("other2@logtest.com").passwordHash("hashed")
                    .name("타대행사관리자2").phone("010-5555-6666").role(Role.AGENCY).agency(otherAgency).build());
            AsRequest otherReq = saveRequest(otherAgency);
            for (int i = 0; i < 5; i++) {
                saveLog(otherReq, otherManager, null, "COMPLETED", null);
            }

            mockMvc.perform(get("/api/agency/work-requests/stats")
                            .header("Authorization", "Bearer " + agencyToken))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.waitingCount").value(1))
                    .andExpect(jsonPath("$.completedCount").value(0));
        }

        @Test
        @DisplayName("실패: AGENCY 이외 권한(CUSTOMER)으로 호출 — 401")
        void fail_notAgency_401() throws Exception {
            mockMvc.perform(get("/api/agency/work-requests/stats")
                            .header("Authorization", "Bearer " + customerToken))
                    .andExpect(status().isUnauthorized());
        }
    }

    // ─────────────────────────────────────────────
    //  픽스처 헬퍼
    // ─────────────────────────────────────────────

    /** AsRequest 생성 (지정 대행사 소속으로 processAssignment 처리) */
    private AsRequest saveRequest(Agencies targetAgency) {
        AsRequest req = AsRequest.builder()
                .customer(customer)
                .appliance(appliance)
                .symptom(symptom)
                .visitRegion(region)
                .visitAddressDetail("강남구 테헤란로 123")
                .scheduledDate(SCHED_DATE)
                .scheduledTime("10:00")
                .build();
        req.processAssignment(targetAgency);
        return asRequestRepository.save(req);
    }

    /** AsStatusLog 생성 */
    private AsStatusLog saveLog(AsRequest req, User changedBy,
                                String fromStatus, String toStatus, String memo) {
        return asStatusLogRepository.save(AsStatusLog.builder()
                .asRequest(req)
                .changedBy(changedBy)
                .fromStatus(fromStatus)
                .toStatus(toStatus)
                .memo(memo)
                .build());
    }
}
