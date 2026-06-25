package com.careflow.as_request.controller;

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
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("local")
@Sql(scripts = "/agency_as_request_cleanup.sql", executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
@DisplayName("AgencyAsRequest 통합 테스트 (H2)")
class AgencyAsRequestIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private JwtProvider jwtProvider;

    @Autowired private UserRepository userRepository;
    @Autowired private AgenciesRepository agenciesRepository;
    @Autowired private RegionRepository regionRepository;
    @Autowired private ApplianceCategoryRepository categoryRepository;
    @Autowired private ApplianceRepository applianceRepository;
    @Autowired private SymptomRepository symptomRepository;
    @Autowired private AsRequestRepository asRequestRepository;

    // ── 공통 픽스처 ──
    private User agencyManager;
    private User customer;
    private Agencies agency;
    private Agencies otherAgency;
    private Regions region;
    private Appliance appliance;
    private Symptom symptom;
    private String agencyToken;
    private String customerToken;

    private static final LocalDate SCHED_DATE = LocalDate.of(2026, 7, 1);

    @BeforeEach
    void setUp() {
        // 1. 지역
        region = regionRepository.save(Regions.create("서울특별시 강남구", null, 1, 0));

        // 2. 가전 카테고리
        ApplianceCategory rootCat = categoryRepository.save(ApplianceCategory.createRoot("에어컨", 1));
        ApplianceCategory cat = categoryRepository.save(ApplianceCategory.createChild("에어컨 소분류", rootCat, 1));

        // 3. 대행사 (테스트 대상)
        agency = agenciesRepository.save(Agencies.builder()
                .agencyName("테스트대행사").businessNumber("BIZ-001")
                .agencyAddress("서울 강남구").agencyFeeRate(5.0)
                .approvalStatus(AgencyStatus.APPROVED).build());

        // 4. 다른 대행사 (격리 검증용)
        otherAgency = agenciesRepository.save(Agencies.builder()
                .agencyName("타대행사").businessNumber("BIZ-002")
                .agencyAddress("서울 서초구").agencyFeeRate(5.0)
                .approvalStatus(AgencyStatus.APPROVED).build());

        // 5. 대행사 관리자 계정 + JWT
        agencyManager = userRepository.save(User.builder()
                .email("manager@test.com").passwordHash("hashed")
                .name("대행사관리자").phone("010-9999-8888").role(Role.AGENCY).agency(agency).build());
        agencyToken = jwtProvider.generateAccessToken(
                agencyManager.getId(), agencyManager.getEmail(), "AGENCY");

        // 6. 고객 계정 + JWT (권한 검증용)
        customer = userRepository.save(User.builder()
                .email("customer@test.com").passwordHash("hashed")
                .name("테스트고객").phone("010-1111-2222").role(Role.CUSTOMER).build());
        customerToken = jwtProvider.generateAccessToken(
                customer.getId(), customer.getEmail(), "CUSTOMER");

        // 7. 고객 소유 가전
        appliance = applianceRepository.save(Appliance.create(
                customer, cat, "삼성", "에어컨 Q9000",
                "SN-001", null, null, RegisterMethod.MANUAL));

        // 8. 증상 마스터
        symptom = symptomRepository.save(Symptom.builder()
                .category(cat).symptomCode("COOLING_FAIL").symptomName("냉방 불량").build());
    }

    // ─────────────────────────────────────────────────────────────
    //  GET /api/as-requests/agency — 전체 목록 조회
    // ─────────────────────────────────────────────────────────────
    @Nested
    @DisplayName("GET /api/as-requests/agency — 전체 목록 조회")
    class GetAgencyAsRequests {

        @Test
        @DisplayName("성공: 대행사 소속 요청 2건(ASSIGNED, IN_PROGRESS) 조회 — COMPLETED 1건 제외 확인")
        void success_excludeCompleted() throws Exception {
            // ASSIGNED 요청
            saveRequest(AsStatus.ASSIGNED, agency, SCHED_DATE);
            // IN_PROGRESS 요청
            saveRequest(AsStatus.IN_PROGRESS, agency, SCHED_DATE);
            // COMPLETED 요청 — 목록에서 제외되어야 함
            saveRequest(AsStatus.COMPLETED, agency, SCHED_DATE);

            mockMvc.perform(get("/api/as-requests/agency")
                            .header("Authorization", "Bearer " + agencyToken))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.length()").value(2))
                    .andExpect(jsonPath("$[0].customerName").value("테스트고객"))
                    .andExpect(jsonPath("$[0].symptomName").value("냉방 불량"));

            // DB에는 3건 존재하지만 응답은 2건
            assertThat(asRequestRepository.findAll()).hasSize(3);
        }

        @Test
        @DisplayName("성공: 타 대행사 요청은 반환되지 않음 — 데이터 격리 확인")
        void success_dataIsolation() throws Exception {
            // 현재 대행사 요청 1건
            saveRequest(AsStatus.ASSIGNED, agency, SCHED_DATE);
            // 타 대행사 요청 1건 — 노출 안 됨
            saveRequest(AsStatus.ASSIGNED, otherAgency, SCHED_DATE);

            mockMvc.perform(get("/api/as-requests/agency")
                            .header("Authorization", "Bearer " + agencyToken))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.length()").value(1));
        }

        @Test
        @DisplayName("성공: 요청 없음 — 204 No Content")
        void empty_204() throws Exception {
            mockMvc.perform(get("/api/as-requests/agency")
                            .header("Authorization", "Bearer " + agencyToken))
                    .andExpect(status().isNoContent());
        }

        @Test
        @DisplayName("실패: CUSTOMER 계정으로 요청 — 401 Unauthorized")
        void customerRole_401() throws Exception {
            mockMvc.perform(get("/api/as-requests/agency")
                            .header("Authorization", "Bearer " + customerToken))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("실패: 인증 토큰 없음 — 401 Unauthorized")
        void noToken_401() throws Exception {
            mockMvc.perform(get("/api/as-requests/agency"))
                    .andExpect(status().isUnauthorized());
        }
    }

    // ─────────────────────────────────────────────────────────────
    //  GET /api/as-requests/agency/search — 필터링 조회
    // ─────────────────────────────────────────────────────────────
    @Nested
    @DisplayName("GET /api/as-requests/agency/search — 필터링 조회")
    class SearchAgencyAsRequests {

        @BeforeEach
        void setUpRequests() {
            // 날짜별 구분: 7/1(ASSIGNED), 7/2(IN_PROGRESS), 7/1(ACCEPTED)
            saveRequest(AsStatus.ASSIGNED,    agency, LocalDate.of(2026, 7, 1));
            saveRequest(AsStatus.IN_PROGRESS, agency, LocalDate.of(2026, 7, 2));
            saveRequest(AsStatus.ACCEPTED,    agency, LocalDate.of(2026, 7, 1));
            // COMPLETED — 항상 제외
            saveRequest(AsStatus.COMPLETED,   agency, LocalDate.of(2026, 7, 1));
        }

        @Test
        @DisplayName("성공: 날짜 필터(7/1) — ASSIGNED + ACCEPTED 2건, COMPLETED 제외")
        void dateFilter_returnsTwo() throws Exception {
            mockMvc.perform(get("/api/as-requests/agency/search")
                            .header("Authorization", "Bearer " + agencyToken)
                            .param("date", "2026-07-01"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.length()").value(2));
        }

        @Test
        @DisplayName("성공: 상태 필터(ASSIGNED) — 1건")
        void statusFilter_assigned() throws Exception {
            mockMvc.perform(get("/api/as-requests/agency/search")
                            .header("Authorization", "Bearer " + agencyToken)
                            .param("status", "ASSIGNED"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.length()").value(1))
                    .andExpect(jsonPath("$[0].status").value("ASSIGNED"));
        }

        @Test
        @DisplayName("성공: 날짜 + 상태 복합 필터 — 날짜 7/1 AND ACCEPTED")
        void dateAndStatusFilter() throws Exception {
            mockMvc.perform(get("/api/as-requests/agency/search")
                            .header("Authorization", "Bearer " + agencyToken)
                            .param("date", "2026-07-01")
                            .param("status", "ACCEPTED"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.length()").value(1))
                    .andExpect(jsonPath("$[0].status").value("ACCEPTED"));
        }

        @Test
        @DisplayName("특수: status=COMPLETED 필터 — 204 No Content (항상 제외)")
        void completedFilter_noContent() throws Exception {
            mockMvc.perform(get("/api/as-requests/agency/search")
                            .header("Authorization", "Bearer " + agencyToken)
                            .param("status", "COMPLETED"))
                    .andExpect(status().isNoContent());
        }

        @Test
        @DisplayName("실패: 유효하지 않은 status 값 — 400 Bad Request")
        void invalidStatus_400() throws Exception {
            mockMvc.perform(get("/api/as-requests/agency/search")
                            .header("Authorization", "Bearer " + agencyToken)
                            .param("status", "WRONG_STATUS"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.success").value(false));
        }

        @Test
        @DisplayName("성공: 필터 미입력 — 전체 반환 (COMPLETED 제외 3건)")
        void noFilter_allExceptCompleted() throws Exception {
            mockMvc.perform(get("/api/as-requests/agency/search")
                            .header("Authorization", "Bearer " + agencyToken))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.length()").value(3));
        }
    }

    // ─────────────────────────────────────────────────────────────
    //  GET /api/as-requests/agency/{requestId} — 단건 상세 조회
    // ─────────────────────────────────────────────────────────────
    @Nested
    @DisplayName("GET /api/as-requests/agency/{requestId} — 단건 상세 조회")
    class GetAgencyAsRequestDetail {

        @Test
        @DisplayName("성공: 본인 소속 대행사 요청 — 200 OK, 가전/증상/고객 정보 포함")
        void success_200() throws Exception {
            AsRequest req = saveRequest(AsStatus.ASSIGNED, agency, SCHED_DATE);

            mockMvc.perform(get("/api/as-requests/agency/" + req.getId())
                            .header("Authorization", "Bearer " + agencyToken))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.requestId").value(req.getId()))
                    .andExpect(jsonPath("$.status").value("ASSIGNED"))
                    .andExpect(jsonPath("$.customerName").value("테스트고객"))
                    .andExpect(jsonPath("$.brand").value("삼성"))
                    .andExpect(jsonPath("$.modelName").value("에어컨 Q9000"))
                    .andExpect(jsonPath("$.symptomCode").value("COOLING_FAIL"))
                    .andExpect(jsonPath("$.symptomName").value("냉방 불량"))
                    .andExpect(jsonPath("$.visitRegionName").value("서울특별시 강남구"))
                    .andExpect(jsonPath("$.scheduledDate").value(SCHED_DATE.toString()));
        }

        @Test
        @DisplayName("실패: 존재하지 않는 requestId — 404 Not Found")
        void notFound_404() throws Exception {
            mockMvc.perform(get("/api/as-requests/agency/999999")
                            .header("Authorization", "Bearer " + agencyToken))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.success").value(false));
        }

        @Test
        @DisplayName("실패: 타 대행사 요청 접근 — 401 Unauthorized")
        void otherAgencyRequest_401() throws Exception {
            // 타 대행사 소속 요청
            AsRequest otherReq = saveRequest(AsStatus.ASSIGNED, otherAgency, SCHED_DATE);

            mockMvc.perform(get("/api/as-requests/agency/" + otherReq.getId())
                            .header("Authorization", "Bearer " + agencyToken))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.success").value(false));
        }

        @Test
        @DisplayName("실패: 인증 토큰 없음 — 401 Unauthorized")
        void noToken_401() throws Exception {
            AsRequest req = saveRequest(AsStatus.ASSIGNED, agency, SCHED_DATE);

            mockMvc.perform(get("/api/as-requests/agency/" + req.getId()))
                    .andExpect(status().isUnauthorized());
        }
    }

    // ── 헬퍼 ──────────────────────────────────────────────────────

    /**
     * 특정 상태 + 대행사의 AsRequest를 직접 저장하는 헬퍼.
     * ASSIGNED 이상 상태는 processAssignment()로 전환.
     */
    private AsRequest saveRequest(AsStatus targetStatus, Agencies targetAgency, LocalDate date) {
        AsRequest req = AsRequest.builder()
                .customer(customer).appliance(appliance).symptom(symptom)
                .visitRegion(region).visitAddressDetail("테헤란로 123")
                .scheduledDate(date).scheduledTime("10:00").build();

        switch (targetStatus) {
            case ASSIGNED -> req.processAssignment(targetAgency);
            case ACCEPTED -> {
                req.processAssignment(targetAgency);
                req.acceptAssignment();
            }
            case IN_PROGRESS -> {
                req.processAssignment(targetAgency);
                req.acceptAssignment();
                req.startWork();
            }
            case COMPLETED -> {
                req.processAssignment(targetAgency);
                req.acceptAssignment();
                req.startWork();
                req.completeWork();
            }
            // PENDING은 빌더 기본값
        }

        return asRequestRepository.save(req);
    }
}
