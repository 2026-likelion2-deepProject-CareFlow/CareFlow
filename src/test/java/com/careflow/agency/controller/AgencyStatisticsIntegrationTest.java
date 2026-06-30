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
import com.careflow.common.enums.AssignType;
import com.careflow.auth.security.CustomUserDetails;
import com.careflow.common.enums.AsStatus;
import com.careflow.common.enums.Role;
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
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("local")
@Sql(scripts = "/cleanup.sql", executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
@DisplayName("AgencyStatisticsController 통합 테스트 (H2)")
class AgencyStatisticsIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private AgenciesRepository agenciesRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private RegionRepository regionRepository;
    @Autowired private ApplianceCategoryRepository categoryRepository;
    @Autowired private ApplianceRepository applianceRepository;
    @Autowired private SymptomRepository symptomRepository;
    @Autowired private AsRequestRepository asRequestRepository;
    @Autowired private AsAssignmentRepository asAssignmentRepository;
    @Autowired private ReviewRepository reviewRepository;
    @Autowired private JdbcTemplate jdbcTemplate;

    // 테스트 픽스처 (각 테스트 공통)
    private Agencies agency;
    private User agencyUser;
    private User customerUser;
    private User engineerUser;
    private ApplianceCategory rootCategory;
    private ApplianceCategory subCategory;
    private Symptom symptom;
    private Regions region;

    @BeforeEach
    void setUp() {
        // cleanup.sql 실행 후 공통 픽스처 재삽입
        region = regionRepository.save(Regions.create("서울특별시 강남구", null, 2, 0));

        // 대행사 생성 (representative_user_id null로 먼저 저장 후 연결은 생략 — 통계에 FK 불필요)
        agency = agenciesRepository.save(Agencies.create("테스트대행사", "BIZ-001", "서울", 5.0));

        // 대행사 관리자 계정
        agencyUser = userRepository.save(User.builder()
                .email("agency@test.com").passwordHash("pw").name("관리자")
                .role(Role.AGENCY).agency(agency).build());

        // 고객 계정
        customerUser = userRepository.save(User.builder()
                .email("customer@test.com").passwordHash("pw").name("고객")
                .role(Role.CUSTOMER).build());

        // 기사 계정
        engineerUser = userRepository.save(User.builder()
                .email("engineer@test.com").passwordHash("pw").name("기사")
                .role(Role.ENGINEER).agency(agency).build());

        // 가전 카테고리 (대분류 > 소분류)
        rootCategory = categoryRepository.save(ApplianceCategory.createRoot("에어컨", 0));
        subCategory  = categoryRepository.save(ApplianceCategory.createChild("에어컨 소분류", rootCategory, 0));

        // 증상
        symptom = symptomRepository.save(Symptom.builder()
                .category(subCategory)
                .symptomCode("COOLING_FAIL")
                .symptomName("냉방 불량")
                .build());
    }

    /** AGENCY 역할 CustomUserDetails 픽스처 */
    private CustomUserDetails agencyPrincipal() {
        return new CustomUserDetails(agencyUser.getId(), "agency@test.com", "pw", "AGENCY", agency.getId());
    }

    /** 기본 가전 등록 */
    private Appliance saveAppliance() {
        return applianceRepository.save(Appliance.builder()
                .user(customerUser)
                .category(subCategory)
                .brand("삼성")
                .modelName("에어컨 모델")
                .build());
    }

    /** as_request 생성 헬퍼 (agency 배정 포함, status 변경 별도 처리) */
    private AsRequest saveRequest(Appliance appliance) {
        AsRequest req = AsRequest.builder()
                .customer(customerUser)
                .appliance(appliance)
                .symptom(symptom)
                .visitRegion(region)
                .visitAddressDetail("강남구 테헤란로 123")
                .scheduledDate(LocalDate.now())
                .scheduledTime("10:00")
                .build();
        req.assignAgency(agency); // AGENCY_RECEIVED
        return asRequestRepository.save(req);
    }

    /** as_request created_at을 지정 일시로 강제 업데이트 (H2 native update) */
    private void setCreatedAt(Long requestId, String dateTimeStr) {
        jdbcTemplate.update(
                "UPDATE as_requests SET created_at = ? WHERE request_id = ?",
                dateTimeStr, requestId);
    }

    /** as_request status와 updated_at 강제 업데이트 */
    private void setStatusAndUpdatedAt(Long requestId, String status, String updatedAt) {
        jdbcTemplate.update(
                "UPDATE as_requests SET status = ?, updated_at = ? WHERE request_id = ?",
                status, updatedAt, requestId);
    }

    // ──────────────────────────────────────────────────────────────
    // Summary
    // ──────────────────────────────────────────────────────────────
    @Nested
    @DisplayName("GET /summary")
    class Summary {

        @Test
        @DisplayName("H2: as_requests 3건(2완료) → totalReceipts=3, completedCount=2")
        void countCorrect() throws Exception {
            Appliance appliance = saveAppliance();

            AsRequest r1 = saveRequest(appliance);
            AsRequest r2 = saveRequest(appliance);
            AsRequest r3 = saveRequest(appliance);

            setStatusAndUpdatedAt(r1.getId(), "COMPLETED", "2024-06-10 12:00:00");
            setStatusAndUpdatedAt(r2.getId(), "COMPLETED", "2024-06-10 14:00:00");
            // r3 은 AGENCY_RECEIVED 유지

            // created_at 을 조회 기간 내로 고정
            setCreatedAt(r1.getId(), "2024-06-05 10:00:00");
            setCreatedAt(r2.getId(), "2024-06-06 10:00:00");
            setCreatedAt(r3.getId(), "2024-06-07 10:00:00");

            mockMvc.perform(get("/api/agency/statistics/summary")
                            .param("dateFrom", "2024-06-01")
                            .param("dateTo", "2024-06-30")
                            .with(user(agencyPrincipal())))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.totalReceiptCount").value(3))
                    .andExpect(jsonPath("$.completedCount").value(2));
        }

        @Test
        @DisplayName("H2: 빈 기간 조회 → totalReceipts=0, completedCount=0")
        void emptyPeriod() throws Exception {
            mockMvc.perform(get("/api/agency/statistics/summary")
                            .param("dateFrom", "2020-01-01")
                            .param("dateTo", "2020-01-31")
                            .with(user(agencyPrincipal())))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.totalReceiptCount").value(0))
                    .andExpect(jsonPath("$.completedCount").value(0));
        }

        @Test
        @DisplayName("H2: 다른 대행사 데이터는 집계 제외")
        void anotherAgencyExcluded() throws Exception {
            // 다른 대행사 생성
            Agencies other = agenciesRepository.save(Agencies.create("타대행사", "BIZ-999", "부산", 3.0));
            Appliance appliance = saveAppliance();
            AsRequest req = AsRequest.builder()
                    .customer(customerUser).appliance(appliance).symptom(symptom)
                    .visitRegion(region).visitAddressDetail("부산 해운대구 123")
                    .scheduledDate(LocalDate.now()).scheduledTime("10:00").build();
            req.assignAgency(other);
            AsRequest saved = asRequestRepository.save(req);
            setCreatedAt(saved.getId(), "2024-06-10 10:00:00");

            mockMvc.perform(get("/api/agency/statistics/summary")
                            .param("dateFrom", "2024-06-01")
                            .param("dateTo", "2024-06-30")
                            .with(user(agencyPrincipal())))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.totalReceiptCount").value(0));
        }
    }

    // ──────────────────────────────────────────────────────────────
    // Daily Trend
    // ──────────────────────────────────────────────────────────────
    @Nested
    @DisplayName("GET /daily-trend")
    class DailyTrend {

        @Test
        @DisplayName("H2: 3일에 걸친 데이터 → 날짜별 건수 정확")
        void dailyCounts() throws Exception {
            Appliance appliance = saveAppliance();

            AsRequest r1 = saveRequest(appliance);
            AsRequest r2 = saveRequest(appliance);
            AsRequest r3 = saveRequest(appliance);

            setCreatedAt(r1.getId(), "2024-06-01 10:00:00");
            setCreatedAt(r2.getId(), "2024-06-02 10:00:00");
            setCreatedAt(r3.getId(), "2024-06-02 14:00:00");

            mockMvc.perform(get("/api/agency/statistics/daily-trend")
                            .param("dateFrom", "2024-06-01")
                            .param("dateTo", "2024-06-30")
                            .with(user(agencyPrincipal())))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.length()").value(2))
                    .andExpect(jsonPath("$[0].date").value("2024-06-01"))
                    .andExpect(jsonPath("$[0].receiptCount").value(1))
                    .andExpect(jsonPath("$[1].date").value("2024-06-02"))
                    .andExpect(jsonPath("$[1].receiptCount").value(2));
        }

        @Test
        @DisplayName("H2: 빈 기간 → 빈 배열")
        void emptyPeriod() throws Exception {
            mockMvc.perform(get("/api/agency/statistics/daily-trend")
                            .param("dateFrom", "2020-01-01")
                            .param("dateTo", "2020-01-31")
                            .with(user(agencyPrincipal())))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.length()").value(0));
        }

        @Test
        @DisplayName("H2: 조회 범위 외 데이터 → 포함되지 않음")
        void outOfRangeExcluded() throws Exception {
            Appliance appliance = saveAppliance();
            AsRequest req = saveRequest(appliance);
            setCreatedAt(req.getId(), "2024-05-15 10:00:00"); // 조회 범위 전

            mockMvc.perform(get("/api/agency/statistics/daily-trend")
                            .param("dateFrom", "2024-06-01")
                            .param("dateTo", "2024-06-30")
                            .with(user(agencyPrincipal())))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.length()").value(0));
        }
    }

    // ──────────────────────────────────────────────────────────────
    // Hourly
    // ──────────────────────────────────────────────────────────────
    @Nested
    @DisplayName("GET /hourly")
    class Hourly {

        @Test
        @DisplayName("H2: 09시·14시·20시에 각 1건 → 슬롯별 건수 정확, 항상 8개 반환")
        void slotCounts() throws Exception {
            Appliance appliance = saveAppliance();

            AsRequest r1 = saveRequest(appliance);
            AsRequest r2 = saveRequest(appliance);
            AsRequest r3 = saveRequest(appliance);

            setCreatedAt(r1.getId(), "2024-06-10 09:30:00"); // 슬롯 3 (09-12시)
            setCreatedAt(r2.getId(), "2024-06-10 14:00:00"); // 슬롯 4 (12-15시)
            setCreatedAt(r3.getId(), "2024-06-10 20:15:00"); // 슬롯 6 (18-21시)

            mockMvc.perform(get("/api/agency/statistics/hourly")
                            .param("dateFrom", "2024-06-01")
                            .param("dateTo", "2024-06-30")
                            .with(user(agencyPrincipal())))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.length()").value(8))
                    .andExpect(jsonPath("$[3].timeRange").value("09-12"))
                    .andExpect(jsonPath("$[3].count").value(1))
                    .andExpect(jsonPath("$[4].timeRange").value("12-15"))
                    .andExpect(jsonPath("$[4].count").value(1))
                    .andExpect(jsonPath("$[6].timeRange").value("18-21"))
                    .andExpect(jsonPath("$[6].count").value(1))
                    .andExpect(jsonPath("$[0].count").value(0));
        }
    }

    // ──────────────────────────────────────────────────────────────
    // Category Dist
    // ──────────────────────────────────────────────────────────────
    @Nested
    @DisplayName("GET /category-dist")
    class CategoryDist {

        @Test
        @DisplayName("H2: 에어컨 2건·냉장고 1건 → 비율 66.7%, 33.3%")
        void distribution() throws Exception {
            // 냉장고 카테고리 추가
            ApplianceCategory fridgeRoot = categoryRepository.save(ApplianceCategory.createRoot("냉장고", 1));
            ApplianceCategory fridgeSub  = categoryRepository.save(ApplianceCategory.createChild("냉장고 소분류", fridgeRoot, 0));
            Symptom fridgeSymptom = symptomRepository.save(Symptom.builder()
                    .category(fridgeSub).symptomCode("COOL_FAIL").symptomName("냉각 불량").build());

            Appliance ac = saveAppliance(); // 에어컨

            // 냉장고 가전
            Appliance fridge = applianceRepository.save(Appliance.builder()
                    .user(customerUser).category(fridgeSub).brand("LG").modelName("냉장고").build());

            // 에어컨 2건
            AsRequest r1 = AsRequest.builder().customer(customerUser).appliance(ac).symptom(symptom)
                    .visitRegion(region).visitAddressDetail("강남 123").scheduledDate(LocalDate.now()).scheduledTime("10:00").build();
            r1.assignAgency(agency);
            AsRequest saved1 = asRequestRepository.save(r1);
            setCreatedAt(saved1.getId(), "2024-06-10 10:00:00");

            AsRequest r2 = AsRequest.builder().customer(customerUser).appliance(ac).symptom(symptom)
                    .visitRegion(region).visitAddressDetail("강남 456").scheduledDate(LocalDate.now()).scheduledTime("11:00").build();
            r2.assignAgency(agency);
            AsRequest saved2 = asRequestRepository.save(r2);
            setCreatedAt(saved2.getId(), "2024-06-11 10:00:00");

            // 냉장고 1건
            AsRequest r3 = AsRequest.builder().customer(customerUser).appliance(fridge).symptom(fridgeSymptom)
                    .visitRegion(region).visitAddressDetail("강남 789").scheduledDate(LocalDate.now()).scheduledTime("14:00").build();
            r3.assignAgency(agency);
            AsRequest saved3 = asRequestRepository.save(r3);
            setCreatedAt(saved3.getId(), "2024-06-12 10:00:00");

            mockMvc.perform(get("/api/agency/statistics/category-dist")
                            .param("dateFrom", "2024-06-01")
                            .param("dateTo", "2024-06-30")
                            .with(user(agencyPrincipal())))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$[0].categoryName").value("에어컨 소분류"))
                    .andExpect(jsonPath("$[0].count").value(2))
                    .andExpect(jsonPath("$[0].percentage").value(66.7))
                    .andExpect(jsonPath("$[1].count").value(1))
                    .andExpect(jsonPath("$[1].percentage").value(33.3));
        }

        @Test
        @DisplayName("H2: 빈 기간 → 빈 배열")
        void emptyPeriod() throws Exception {
            mockMvc.perform(get("/api/agency/statistics/category-dist")
                            .param("dateFrom", "2020-01-01")
                            .param("dateTo", "2020-01-31")
                            .with(user(agencyPrincipal())))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.length()").value(0));
        }
    }

    // ──────────────────────────────────────────────────────────────
    // Status Count
    // ──────────────────────────────────────────────────────────────
    @Nested
    @DisplayName("GET /status-count")
    class StatusCount {

        @Test
        @DisplayName("H2: PENDING 2건·COMPLETED 1건·CANCELLED 1건 → 레이블별 건수 정확")
        void statusCounts() throws Exception {
            Appliance appliance = saveAppliance();

            AsRequest r1 = saveRequest(appliance);
            AsRequest r2 = saveRequest(appliance);
            AsRequest r3 = saveRequest(appliance);
            AsRequest r4 = saveRequest(appliance);

            setCreatedAt(r1.getId(), "2024-06-05 10:00:00");
            setCreatedAt(r2.getId(), "2024-06-06 10:00:00");
            setCreatedAt(r3.getId(), "2024-06-07 10:00:00");
            setCreatedAt(r4.getId(), "2024-06-08 10:00:00");

            // r3 → COMPLETED, r4 → CANCELLED
            setStatusAndUpdatedAt(r3.getId(), "COMPLETED", "2024-06-10 15:00:00");
            setStatusAndUpdatedAt(r4.getId(), "CANCELLED", "2024-06-09 10:00:00");

            mockMvc.perform(get("/api/agency/statistics/status-count")
                            .param("dateFrom", "2024-06-01")
                            .param("dateTo", "2024-06-30")
                            .with(user(agencyPrincipal())))
                    .andExpect(status().isOk())
                    // AsStatus enum 순서: PENDING(0), AGENCY_RECEIVED(1) ... COMPLETED(7) ... CANCELLED(9)
                    // saveRequest()는 assignAgency() 호출로 AGENCY_RECEIVED 상태가 됨 (r1, r2 → 2건)
                    .andExpect(jsonPath("$[1].status").value("AGENCY_RECEIVED"))
                    .andExpect(jsonPath("$[1].count").value(2))
                    // COMPLETED 1건 (r3)
                    .andExpect(jsonPath("$[7].status").value("COMPLETED"))
                    .andExpect(jsonPath("$[7].count").value(1))
                    // CANCELLED 1건 (r4)
                    .andExpect(jsonPath("$[9].status").value("CANCELLED"))
                    .andExpect(jsonPath("$[9].count").value(1));
        }
    }

    // ──────────────────────────────────────────────────────────────
    // Engineer Top5
    // ──────────────────────────────────────────────────────────────
    @Nested
    @DisplayName("GET /engineer-top5")
    class EngineerTop5 {

        @Test
        @DisplayName("H2: 기사 3명·완료 건수 2·1·1 → rank1 completedCount=2")
        void top5() throws Exception {
            // 기사 3명 생성
            User eng1 = userRepository.save(User.builder().email("eng1@test.com").passwordHash("pw")
                    .name("김일번").role(Role.ENGINEER).agency(agency).build());
            User eng2 = userRepository.save(User.builder().email("eng2@test.com").passwordHash("pw")
                    .name("이이번").role(Role.ENGINEER).agency(agency).build());

            Appliance appliance = saveAppliance();
            AsRequest req1 = saveRequest(appliance);
            AsRequest req2 = saveRequest(appliance);
            AsRequest req3 = saveRequest(appliance);

            setCreatedAt(req1.getId(), "2024-06-05 10:00:00");
            setCreatedAt(req2.getId(), "2024-06-06 10:00:00");
            setCreatedAt(req3.getId(), "2024-06-07 10:00:00");

            // 배차 생성 후 COMPLETED 처리
            AsAssignment a1 = AsAssignment.builder().asRequest(req1).engineer(eng1).agency(agency).assignMethod(AssignType.MANUAL).build();
            AsAssignment a2 = AsAssignment.builder().asRequest(req2).engineer(eng1).agency(agency).assignMethod(AssignType.MANUAL).build();
            AsAssignment a3 = AsAssignment.builder().asRequest(req3).engineer(eng2).agency(agency).assignMethod(AssignType.MANUAL).build();

            AsAssignment saved1 = asAssignmentRepository.save(a1);
            AsAssignment saved2 = asAssignmentRepository.save(a2);
            AsAssignment saved3 = asAssignmentRepository.save(a3);

            jdbcTemplate.update("UPDATE as_assignments SET status='COMPLETED', assigned_at='2024-06-05 12:00:00' WHERE assignment_id=?", saved1.getId());
            jdbcTemplate.update("UPDATE as_assignments SET status='COMPLETED', assigned_at='2024-06-06 12:00:00' WHERE assignment_id=?", saved2.getId());
            jdbcTemplate.update("UPDATE as_assignments SET status='COMPLETED', assigned_at='2024-06-07 12:00:00' WHERE assignment_id=?", saved3.getId());

            mockMvc.perform(get("/api/agency/statistics/engineer-top5")
                            .param("dateFrom", "2024-06-01")
                            .param("dateTo", "2024-06-30")
                            .with(user(agencyPrincipal())))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$[0].rank").value(1))
                    .andExpect(jsonPath("$[0].engineerName").value("김일번"))
                    .andExpect(jsonPath("$[0].completedCount").value(2));
        }

        @Test
        @DisplayName("H2: 완료 건 없음 → 빈 배열")
        void noCompleted() throws Exception {
            mockMvc.perform(get("/api/agency/statistics/engineer-top5")
                            .param("dateFrom", "2020-01-01")
                            .param("dateTo", "2020-01-31")
                            .with(user(agencyPrincipal())))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.length()").value(0));
        }
    }

    // ──────────────────────────────────────────────────────────────
    // Monthly Summary
    // ──────────────────────────────────────────────────────────────
    @Nested
    @DisplayName("GET /monthly-summary")
    class MonthlySummary {

        @Test
        @DisplayName("H2: 데이터 없음 → 모든 필드 기본값(데이터 없음 / 0건 / 0.0)")
        void noData() throws Exception {
            mockMvc.perform(get("/api/agency/statistics/monthly-summary")
                            .with(user(agencyPrincipal())))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.topReceiptDayOfWeek").value("데이터 없음"))
                    .andExpect(jsonPath("$.topReceiptDayCount").value(0))
                    .andExpect(jsonPath("$.topReceiptHour").value("데이터 없음"))
                    .andExpect(jsonPath("$.topReceiptHourCount").value(0))
                    .andExpect(jsonPath("$.topRatingEngineerName").value("데이터 없음"))
                    .andExpect(jsonPath("$.topRatingEngineerScore").value(0.0));
        }

        @Test
        @DisplayName("H2: 리뷰 5건(4점x4, 2점x1) → 최고 평점 기사 평균 3.6")
        void topRatingEngineer() throws Exception {
            Appliance appliance = saveAppliance();

            // 이번 달 as_request 5건 생성 후 리뷰 작성
            for (int i = 0; i < 5; i++) {
                AsRequest req = AsRequest.builder()
                        .customer(customerUser).appliance(appliance).symptom(symptom)
                        .visitRegion(region).visitAddressDetail("강남 " + i)
                        .scheduledDate(LocalDate.now()).scheduledTime("10:00").build();
                req.assignAgency(agency);
                AsRequest saved = asRequestRepository.save(req);

                // 리뷰: 4건 4점, 1건 2점 → 평균 3.6
                int rating = (i < 4) ? 4 : 2;
                Review review = Review.create(saved, customerUser, engineerUser, rating, "테스트 리뷰");
                reviewRepository.save(review);
            }

            mockMvc.perform(get("/api/agency/statistics/monthly-summary")
                            .with(user(agencyPrincipal())))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.topRatingEngineerName").value("기사"))
                    .andExpect(jsonPath("$.topRatingEngineerScore").value(3.6));
        }
    }
}
