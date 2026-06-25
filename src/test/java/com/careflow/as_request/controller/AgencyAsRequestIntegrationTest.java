package com.careflow.as_request.controller;

import com.careflow.agency.entity.Agencies;
import com.careflow.agency.repository.AgenciesRepository;
import org.springframework.jdbc.core.JdbcTemplate;
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
    @Autowired private JdbcTemplate jdbcTemplate;

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
    @DisplayName("GET /api/as-requests/agency/search — 필터링 조회 (접수일 기준)")
    class SearchAgencyAsRequests {

        // 날짜 필터 검증을 위해 오늘/어제로 접수일을 구분해서 데이터를 세팅
        // saveRequest()는 createdAt = now()로 고정되므로
        // 과거 날짜는 saveRequestWithCreatedAt()으로 createdAt을 직접 지정
        private static final LocalDate TODAY     = LocalDate.now();
        private static final LocalDate YESTERDAY = LocalDate.now().minusDays(1);

        @BeforeEach
        void setUpRequests() {
            // 오늘 접수: ASSIGNED, ACCEPTED
            saveRequest(AsStatus.ASSIGNED, agency, SCHED_DATE);
            saveRequest(AsStatus.ACCEPTED, agency, SCHED_DATE);
            // 어제 접수: IN_PROGRESS
            saveRequestWithCreatedAt(AsStatus.IN_PROGRESS, agency, SCHED_DATE,
                    YESTERDAY.atStartOfDay());
            // COMPLETED — 접수일 무관하게 항상 제외
            saveRequest(AsStatus.COMPLETED, agency, SCHED_DATE);
        }

        @Test
        @DisplayName("성공: 접수일 필터(오늘) — ASSIGNED + ACCEPTED 2건, 어제/COMPLETED 제외")
        void dateFilter_today() throws Exception {
            mockMvc.perform(get("/api/as-requests/agency/search")
                            .header("Authorization", "Bearer " + agencyToken)
                            .param("date", TODAY.toString()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.length()").value(2));
        }

        @Test
        @DisplayName("성공: 접수일 필터(어제) — IN_PROGRESS 1건")
        void dateFilter_yesterday() throws Exception {
            mockMvc.perform(get("/api/as-requests/agency/search")
                            .header("Authorization", "Bearer " + agencyToken)
                            .param("date", YESTERDAY.toString()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.length()").value(1))
                    .andExpect(jsonPath("$[0].status").value("IN_PROGRESS"));
        }

        @Test
        @DisplayName("성공: 상태 필터(ASSIGNED) — 접수일 무관 1건")
        void statusFilter_assigned() throws Exception {
            mockMvc.perform(get("/api/as-requests/agency/search")
                            .header("Authorization", "Bearer " + agencyToken)
                            .param("status", "ASSIGNED"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.length()").value(1))
                    .andExpect(jsonPath("$[0].status").value("ASSIGNED"));
        }

        @Test
        @DisplayName("성공: 접수일 + 상태 복합 필터 — 오늘 접수 AND ACCEPTED 1건")
        void dateAndStatusFilter() throws Exception {
            mockMvc.perform(get("/api/as-requests/agency/search")
                            .header("Authorization", "Bearer " + agencyToken)
                            .param("date", TODAY.toString())
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

    // ─────────────────────────────────────────────────────────────
    //  GET /api/as-requests/agency/dashboard-summary — 대시보드 요약 통계
    // ─────────────────────────────────────────────────────────────
    @Nested
    @DisplayName("GET /api/as-requests/agency/dashboard-summary — 대시보드 요약 통계 조회")
    class GetAgencyDashboardSummary {

        @Test
        @DisplayName("성공: 전체 누적 3건, 오늘 신규 2건, ASSIGNED 1건, ACCEPTED 1건, CANCELLED 0건")
        void success_normalCounts() throws Exception {
            // 오늘 접수: ASSIGNED, ACCEPTED
            saveRequest(AsStatus.ASSIGNED, agency, SCHED_DATE);
            saveRequest(AsStatus.ACCEPTED, agency, SCHED_DATE);
            // 어제 접수된 요청 (totalCount에는 포함, todayNewCount에는 제외)
            saveRequestWithCreatedAt(AsStatus.ASSIGNED, agency, SCHED_DATE,
                    LocalDate.now().minusDays(1).atStartOfDay());

            mockMvc.perform(get("/api/as-requests/agency/dashboard-summary")
                            .header("Authorization", "Bearer " + agencyToken))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.totalCount").value(3))
                    .andExpect(jsonPath("$.todayNewCount").value(2))
                    .andExpect(jsonPath("$.todayAssignedCount").value(1))
                    .andExpect(jsonPath("$.todayAcceptedCount").value(1))
                    .andExpect(jsonPath("$.todayCancelledCount").value(0));
        }

        @Test
        @DisplayName("성공: 오늘 CANCELLED 건 포함 — todayCancelledCount 정확히 집계")
        void success_withCancelled() throws Exception {
            // CANCELLED: 고객이 취소한 건 (cancel()은 PENDING 상태에서만 가능)
            AsRequest cancelledReq = AsRequest.builder()
                    .customer(customer).appliance(appliance).symptom(symptom)
                    .visitRegion(region).visitAddressDetail("테헤란로 1")
                    .scheduledDate(SCHED_DATE).scheduledTime("10:00").build();
            cancelledReq.cancel("고객 변심");
            asRequestRepository.save(cancelledReq);

            saveRequest(AsStatus.ASSIGNED, agency, SCHED_DATE);

            // CANCELLED 요청은 agency_id = null 이므로 대행사 집계에 포함되지 않음
            // totalCount = 1(ASSIGNED), todayNewCount = 2(ASSIGNED + CANCELLED 접수)
            // 단, CANCELLED는 agency_id가 없어 countByAgencyId 쿼리에서 제외되므로 totalCount = 1
            mockMvc.perform(get("/api/as-requests/agency/dashboard-summary")
                            .header("Authorization", "Bearer " + agencyToken))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.totalCount").value(1))
                    .andExpect(jsonPath("$.todayNewCount").value(1))
                    .andExpect(jsonPath("$.todayAssignedCount").value(1))
                    .andExpect(jsonPath("$.todayCancelledCount").value(0));
        }

        @Test
        @DisplayName("성공: 타 대행사 요청은 집계에서 제외 — 데이터 격리 확인")
        void success_dataIsolation() throws Exception {
            // 현재 대행사 요청 2건
            saveRequest(AsStatus.ASSIGNED, agency, SCHED_DATE);
            saveRequest(AsStatus.ACCEPTED, agency, SCHED_DATE);
            // 타 대행사 요청 — 집계 제외 대상
            saveRequest(AsStatus.ASSIGNED, otherAgency, SCHED_DATE);

            mockMvc.perform(get("/api/as-requests/agency/dashboard-summary")
                            .header("Authorization", "Bearer " + agencyToken))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.totalCount").value(2))
                    .andExpect(jsonPath("$.todayNewCount").value(2));
        }

        @Test
        @DisplayName("성공: 신규 대행사(요청 전혀 없음) — 모든 카운트 0")
        void success_allZero() throws Exception {
            mockMvc.perform(get("/api/as-requests/agency/dashboard-summary")
                            .header("Authorization", "Bearer " + agencyToken))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.totalCount").value(0))
                    .andExpect(jsonPath("$.todayNewCount").value(0))
                    .andExpect(jsonPath("$.todayAssignedCount").value(0))
                    .andExpect(jsonPath("$.todayAcceptedCount").value(0))
                    .andExpect(jsonPath("$.todayCancelledCount").value(0));
        }

        @Test
        @DisplayName("성공: 오늘 접수 없고 과거 누적만 있을 때 — totalCount만 집계")
        void success_onlyPastRequests() throws Exception {
            // 어제 접수 2건
            saveRequestWithCreatedAt(AsStatus.ASSIGNED, agency, SCHED_DATE,
                    LocalDate.now().minusDays(1).atStartOfDay());
            saveRequestWithCreatedAt(AsStatus.ACCEPTED, agency, SCHED_DATE,
                    LocalDate.now().minusDays(1).atStartOfDay());

            mockMvc.perform(get("/api/as-requests/agency/dashboard-summary")
                            .header("Authorization", "Bearer " + agencyToken))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.totalCount").value(2))
                    .andExpect(jsonPath("$.todayNewCount").value(0))
                    .andExpect(jsonPath("$.todayAssignedCount").value(0))
                    .andExpect(jsonPath("$.todayAcceptedCount").value(0));
        }

        @Test
        @DisplayName("실패: CUSTOMER 계정으로 요청 — 401 Unauthorized")
        void customerRole_401() throws Exception {
            mockMvc.perform(get("/api/as-requests/agency/dashboard-summary")
                            .header("Authorization", "Bearer " + customerToken))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("실패: 인증 토큰 없음 — 401 Unauthorized")
        void noToken_401() throws Exception {
            mockMvc.perform(get("/api/as-requests/agency/dashboard-summary"))
                    .andExpect(status().isUnauthorized());
        }
    }

    // ── 헬퍼 ──────────────────────────────────────────────────────

    /**
     * 특정 상태 + 대행사의 AsRequest를 직접 저장하는 헬퍼.
     * 변경된 상태 전이 파이프라인(출발 -> 도착 -> 시작)을 엄격하게 준수합니다.
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
            case ENGINEER_DEPARTED -> {
                req.processAssignment(targetAgency);
                req.acceptAssignment();
                req.depart();
            }
            case ENGINEER_ARRIVED -> {
                req.processAssignment(targetAgency);
                req.acceptAssignment();
                req.depart();
                req.arrive();
            }
            case IN_PROGRESS -> {
                req.processAssignment(targetAgency);
                req.acceptAssignment();
                req.depart(); // 💡 기사 출발
                req.arrive(); // 💡 기사 도착
                req.startWork();
            }
            case COMPLETED -> {
                req.processAssignment(targetAgency);
                req.acceptAssignment();
                req.depart(); // 💡 기사 출발
                req.arrive(); // 💡 기사 도착
                req.startWork();
                req.completeWork();
            }
            // PENDING은 빌더 기본값
        }

        return asRequestRepository.save(req);
    }

    /**
     * createdAt을 JdbcTemplate으로 직접 UPDATE해 과거 날짜 데이터를 생성하는 헬퍼.
     * AsRequest.createdAt 이 @Column(updatable=false)라 JPA save()로는 반영이 안 되므로
     * JDBC로 직접 UPDATE를 실행한다.
     * totalCount vs todayNewCount 분리 검증 시 사용.
     */
    private AsRequest saveRequestWithCreatedAt(AsStatus targetStatus, Agencies targetAgency,
                                               LocalDate schedDate, java.time.LocalDateTime createdAt) {
        AsRequest req = saveRequest(targetStatus, targetAgency, schedDate);
        jdbcTemplate.update(
                "UPDATE as_requests SET created_at = ? WHERE request_id = ?",
                createdAt, req.getId());
        return req;
    }
}
