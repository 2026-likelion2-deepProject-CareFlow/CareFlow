package com.careflow.assignment.controller;

import com.careflow.agency.entity.Agencies;
import com.careflow.agency.repository.AgenciesRepository;
import com.careflow.notification.repository.NotificationRepository;
import com.careflow.appliance.entity.Appliance;
import com.careflow.appliance.entity.ApplianceCategory;
import com.careflow.appliance.repository.ApplianceCategoryRepository;
import com.careflow.appliance.repository.ApplianceRepository;
import com.careflow.as_request.entity.AsRequest;
import com.careflow.as_request.repository.AsRequestRepository;
import com.careflow.assignment.entity.AsAssignment;
import com.careflow.as_status_log.entity.AsStatusLog;
import com.careflow.assignment.entity.ExpectedRepairCost;
import com.careflow.assignment.repository.AsAssignmentRepository;
import com.careflow.as_status_log.repository.AsStatusLogRepository;
import com.careflow.assignment.repository.ExpectedRepairCostRepository;
import com.careflow.auth.security.JwtProvider;
import com.careflow.common.enums.AgencyStatus;
import com.careflow.common.enums.AssignType;
import com.careflow.common.enums.RegisterMethod;
import com.careflow.common.enums.Role;
import com.careflow.engineer.domain.entity.EngineerProfile;
import com.careflow.common.enums.SkillLevel;
import com.careflow.engineer.repository.EngineerProfileRepository;
import com.careflow.region.entity.Regions;
import com.careflow.region.repository.RegionRepository;
import com.careflow.report.domain.entity.WorkReport;
import com.careflow.common.enums.DiagnosisResult;
import com.careflow.report.repository.WorkReportRepository;
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

import com.careflow.review.entity.Review;
import com.careflow.review.repository.ReviewRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.MediaType;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("local")
@Sql(scripts = "/assignment_cleanup.sql", executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
@DisplayName("AssignmentController 상세조회 통합 테스트 (H2)")
class AssignmentControllerIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private JwtProvider jwtProvider;

    // ── Repositories ──
    @Autowired private AgenciesRepository agenciesRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private RegionRepository regionRepository;
    @Autowired private ApplianceCategoryRepository categoryRepository;
    @Autowired private ApplianceRepository applianceRepository;
    @Autowired private SymptomRepository symptomRepository;
    @Autowired private AsRequestRepository asRequestRepository;
    @Autowired private AsAssignmentRepository asAssignmentRepository;
    @Autowired private AsStatusLogRepository asStatusLogRepository;
    @Autowired private ExpectedRepairCostRepository expectedRepairCostRepository;
    @Autowired private WorkReportRepository workReportRepository;
    @Autowired private EngineerProfileRepository engineerProfileRepository;
    @Autowired private NotificationRepository notificationRepository;
    @Autowired private ReviewRepository reviewRepository;
    @Autowired private ObjectMapper objectMapper;

    // ── 공통 픽스처 ──
    private Agencies agency;
    private User agencyManager;
    private User engineer;
    private User customer;
    private ApplianceCategory category;
    private Appliance appliance;
    private Symptom symptom;
    private Regions region;
    private AsRequest asRequest;
    private AsAssignment assignment;

    private String agencyToken;
    // JWT의 agencyId 클레임이 채워진 토큰 — JwtFilter는 DB 재조회 없이 토큰 클레임만으로 agencyId를 신뢰하므로
    // 대행사 소속 검증이 필요한 신규 API(진행중/완료/기사변경/일정변경/취소) 테스트에서는 이 토큰을 사용해야 함
    private String agencyTokenWithAgencyId;
    private String customerToken;

    @BeforeEach
    void setUp() {
        // 1. 지역
        region = regionRepository.save(Regions.create("서울특별시 강남구", null, 1, 0));

        // 2. 대행사 (APPROVED)
        agency = agenciesRepository.save(Agencies.builder()
                .agencyName("테스트대행사").businessNumber("TEST-BIZ-001")
                .agencyAddress("서울특별시 강남구").agencyFeeRate(5.0)
                .approvalStatus(AgencyStatus.APPROVED).build());

        // 3. 대행사 관리자 (role=AGENCY, 대행사 소속)
        agencyManager = userRepository.save(User.builder()
                .email("manager@agency.com").passwordHash("hashed")
                .name("대행사관리자").phone("010-0000-0001")
                .role(Role.AGENCY).agency(agency).build());
        agencyToken = jwtProvider.generateAccessToken(
                agencyManager.getId(), agencyManager.getEmail(), "AGENCY", null);
        agencyTokenWithAgencyId = jwtProvider.generateAccessToken(
                agencyManager.getId(), agencyManager.getEmail(), "AGENCY", agency.getId());

        // 4. 수리 기사 (대행사 소속)
        engineer = userRepository.save(User.builder()
                .email("engineer@agency.com").passwordHash("hashed")
                .name("테스트기사").phone("010-0000-0002")
                .role(Role.ENGINEER).agency(agency).build());

        // 5. 고객 (권한 없음 케이스 검증용)
        customer = userRepository.save(User.builder()
                .email("customer@test.com").passwordHash("hashed")
                .name("테스트고객").phone("010-0000-0003")
                .role(Role.CUSTOMER).build());
        customerToken = jwtProvider.generateAccessToken(
                customer.getId(), customer.getEmail(), "CUSTOMER", null);

        // 6. 가전 카테고리 + 가전 + 증상
        ApplianceCategory rootCat = categoryRepository.save(ApplianceCategory.createRoot("에어컨", 1));
        category = categoryRepository.save(ApplianceCategory.createChild("에어컨 소분류", rootCat, 1));
        appliance = applianceRepository.save(Appliance.create(
                customer, category, "삼성", "에어컨 Q9000",
                null, LocalDate.of(2023, 1, 1), LocalDate.of(2026, 1, 1), RegisterMethod.MANUAL));
        symptom = symptomRepository.save(Symptom.builder()
                .category(category).symptomCode("COOLING_FAIL").symptomName("냉방 불량").build());

        // 7. A/S 요청 + 배차
        asRequest = asRequestRepository.save(AsRequest.builder()
                .customer(customer).appliance(appliance).symptom(symptom)
                .symptomDesc("냉방이 전혀 되지 않습니다")
                .visitRegion(region).visitAddressDetail("강남구 테헤란로 123")
                .scheduledDate(LocalDate.of(2026, 7, 1)).scheduledTime("10:00")
                .build());
        assignment = asAssignmentRepository.save(
                AsAssignment.create(asRequest, engineer, agency, AssignType.MANUAL));
    }

    // ─────────────────────────────────────────────────────────────
    //  GET /api/assignment/detail/{assignmentId}
    // ─────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("GET /api/assignment/detail/{assignmentId} — 배차 상세 조회")
    class GetAssignmentDetail {

        @Test
        @DisplayName("성공(기본): 부가 데이터 없을 때 → 200 OK, 핵심 필드 검증")
        void getDetail_minimal_200() throws Exception {
            mockMvc.perform(get("/api/agency/as-assignments/" + assignment.getId() + "/detail")
                            .header("Authorization", "Bearer " + agencyToken))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.assignmentId").value(assignment.getId()))
                    .andExpect(jsonPath("$.assignmentStatus").value("WAITING"))
                    .andExpect(jsonPath("$.requestId").value(asRequest.getId()))
                    .andExpect(jsonPath("$.scheduledDate").value("2026-07-01"))
                    .andExpect(jsonPath("$.scheduledTime").value("10:00"))
                    .andExpect(jsonPath("$.symptomDesc").value("냉방이 전혀 되지 않습니다"))
                    .andExpect(jsonPath("$.symptomName").value("냉방 불량"))
                    .andExpect(jsonPath("$.customerName").value("테스트고객"))
                    .andExpect(jsonPath("$.customerPhone").value("010-0000-0003"))
                    .andExpect(jsonPath("$.modelName").value("에어컨 Q9000"))
                    .andExpect(jsonPath("$.purchaseDate").value("2023-01-01"))
                    .andExpect(jsonPath("$.warrantyEndDate").value("2026-01-01"))
                    .andExpect(jsonPath("$.avgRepairCost").doesNotExist())
                    .andExpect(jsonPath("$.statusLogs").isArray())
                    .andExpect(jsonPath("$.statusLogs.length()").value(0))
                    .andExpect(jsonPath("$.previousWorkReports.length()").value(0))
                    .andExpect(jsonPath("$.engineerProfile").doesNotExist());
        }

        @Test
        @DisplayName("성공: 예상 수리 비용 존재 → avgRepairCost 값 반환, DB 직접 검증")
        void getDetail_withAvgRepairCost_200() throws Exception {
            saveExpectedRepairCost(symptom, category, 150000);

            mockMvc.perform(get("/api/agency/as-assignments/" + assignment.getId() + "/detail")
                            .header("Authorization", "Bearer " + agencyToken))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.avgRepairCost").value(150000));

            // DB 직접 검증
            assertThat(expectedRepairCostRepository.findBySymptom_Id(symptom.getId())).isPresent();
        }

        @Test
        @DisplayName("성공: 처리 이력 2건 존재 → statusLogs 시간 오름차순 반환")
        void getDetail_withStatusLogs_200() throws Exception {
            asStatusLogRepository.save(AsStatusLog.builder()
                    .asRequest(asRequest).changedBy(engineer)
                    .fromStatus(null).toStatus("WAITING").memo("최초 배차").build());
            asStatusLogRepository.save(AsStatusLog.builder()
                    .asRequest(asRequest).changedBy(engineer)
                    .fromStatus("WAITING").toStatus("ENGINEER_DEPARTED").memo("출발").build());

            mockMvc.perform(get("/api/agency/as-assignments/" + assignment.getId() + "/detail")
                            .header("Authorization", "Bearer " + agencyToken))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.statusLogs.length()").value(2))
                    .andExpect(jsonPath("$.statusLogs[0].toStatus").value("WAITING"))
                    .andExpect(jsonPath("$.statusLogs[0].memo").value("최초 배차"))
                    .andExpect(jsonPath("$.statusLogs[1].toStatus").value("ENGINEER_DEPARTED"))
                    .andExpect(jsonPath("$.statusLogs[1].fromStatus").value("WAITING"));
        }

        @Test
        @DisplayName("성공: 동일 가전의 이전 수리 이력 존재 → previousWorkReports 반환, DB 직접 검증")
        void getDetail_withPreviousWorkReports_200() throws Exception {
            // 같은 가전(appliance)의 이전 A/S 에 대해 수리 보고서 작성
            AsRequest prevRequest = asRequestRepository.save(AsRequest.builder()
                    .customer(customer).appliance(appliance).symptom(symptom)
                    .visitRegion(region).visitAddressDetail("이전 주소")
                    .scheduledDate(LocalDate.of(2026, 1, 10)).scheduledTime("09:00")
                    .build());
            workReportRepository.save(WorkReport.builder()
                    .asRequest(prevRequest).engineer(engineer)
                    .diagnosisResult(DiagnosisResult.REPAIRED)
                    .workDurationMin(60).finalAmount(80000).memo("냉매 보충 완료").build());

            mockMvc.perform(get("/api/agency/as-assignments/" + assignment.getId() + "/detail")
                            .header("Authorization", "Bearer " + agencyToken))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.previousWorkReports.length()").value(1))
                    .andExpect(jsonPath("$.previousWorkReports[0].diagnosisResult").value("REPAIRED"))
                    .andExpect(jsonPath("$.previousWorkReports[0].finalAmount").value(80000))
                    .andExpect(jsonPath("$.previousWorkReports[0].memo").value("냉매 보충 완료"));

            // DB 직접 검증 — appliance_id 기준 work_reports 조회
            assertThat(workReportRepository.findByAsRequest_Appliance_Id(appliance.getId()))
                    .hasSize(1);
        }

        @Test
        @DisplayName("성공: 기사 프로필 존재 → engineerProfile 반환")
        void getDetail_withEngineerProfile_200() throws Exception {
            EngineerProfile profile = EngineerProfile.createInitial(engineer);
            profile.completeProfile(category, 2020, SkillLevel.INTERMEDIATE, "성실한 기사입니다");
            engineerProfileRepository.save(profile);

            mockMvc.perform(get("/api/agency/as-assignments/" + assignment.getId() + "/detail")
                            .header("Authorization", "Bearer " + agencyToken))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.engineerProfile.skillLevel").value("INTERMEDIATE"))
                    .andExpect(jsonPath("$.engineerProfile.introduction").value("성실한 기사입니다"))
                    .andExpect(jsonPath("$.engineerProfile.avgRating").value(0.0))
                    .andExpect(jsonPath("$.engineerProfile.totalReviews").value(0));
        }

        @Test
        @DisplayName("성공(풀 데이터): 모든 부가 데이터 존재 → 전체 구조 일괄 검증")
        void getDetail_fullData_200() throws Exception {
            saveExpectedRepairCost(symptom, category, 120000);
            asStatusLogRepository.save(AsStatusLog.builder()
                    .asRequest(asRequest).changedBy(engineer)
                    .fromStatus(null).toStatus("WAITING").memo("배차 완료").build());
            EngineerProfile profile = EngineerProfile.createInitial(engineer);
            profile.completeProfile(category, 2018, SkillLevel.ADVANCED, "전문가 기사");
            engineerProfileRepository.save(profile);

            mockMvc.perform(get("/api/agency/as-assignments/" + assignment.getId() + "/detail")
                            .header("Authorization", "Bearer " + agencyToken))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.avgRepairCost").value(120000))
                    .andExpect(jsonPath("$.statusLogs.length()").value(1))
                    .andExpect(jsonPath("$.statusLogs[0].memo").value("배차 완료"))
                    .andExpect(jsonPath("$.engineerProfile.skillLevel").value("ADVANCED"))
                    .andExpect(jsonPath("$.engineerProfile.introduction").value("전문가 기사"));
        }

        @Test
        @DisplayName("실패: 존재하지 않는 assignmentId → 404 Not Found + 에러 포맷 검증")
        void getDetail_notFound_404() throws Exception {
            mockMvc.perform(get("/api/agency/as-assignments/99999/detail")
                            .header("Authorization", "Bearer " + agencyToken))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.success").value(false))
                    .andExpect(jsonPath("$.message").value("해당 배차 내역을 찾을 수 없습니다."));
        }

        @Test
        @DisplayName("실패: CUSTOMER 권한 토큰 → 401 Unauthorized + 에러 포맷 검증")
        void getDetail_customerRole_401() throws Exception {
            mockMvc.perform(get("/api/agency/as-assignments/" + assignment.getId() + "/detail")
                            .header("Authorization", "Bearer " + customerToken))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.success").value(false));
        }

        @Test
        @DisplayName("실패: 인증 토큰 없음 → 401 Unauthorized")
        void getDetail_noToken_401() throws Exception {
            mockMvc.perform(get("/api/agency/as-assignments/" + assignment.getId() + "/detail"))
                    .andExpect(status().isUnauthorized());
        }
    }

    // ─────────────────────────────────────────────────────────────
    //  GET /api/assignment/search
    // ─────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("GET /api/assignment/search — 날짜·상태 동적 필터 배차 조회")
    class SearchAssignments {

        @Test
        @DisplayName("성공: 날짜 필터만 → 해당 날짜 배차 2건 반환, scheduledDate·symptomName 검증")
        void search_dateOnly_200() throws Exception {
            // 배차 2건 (같은 날짜)
            AsRequest req2 = saveAsRequest(LocalDate.of(2026, 7, 1));
            asAssignmentRepository.save(AsAssignment.create(req2, engineer, agency, AssignType.MANUAL));

            mockMvc.perform(get("/api/agency/as-assignments/search")
                            .param("date", "2026-07-01")
                            .header("Authorization", "Bearer " + agencyToken))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.length()").value(2))
                    .andExpect(jsonPath("$[0].scheduledDate").value("2026-07-01"))
                    .andExpect(jsonPath("$[0].symptomName").value("냉방 불량"))
                    .andExpect(jsonPath("$[0].engineerName").value("테스트기사"));
        }

        @Test
        @DisplayName("성공: 날짜+상태 필터 (date 명시, status=WAITING) → WAITING 배차만 반환")
        void search_statusOnly_200() throws Exception {
            // date 미입력 시 오늘 날짜가 기본값이므로, 2026-07-01 데이터를 보려면 날짜 명시 필요
            mockMvc.perform(get("/api/agency/as-assignments/search")
                            .param("date", "2026-07-01")
                            .param("status", "WAITING")
                            .header("Authorization", "Bearer " + agencyToken))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.length()").value(1))
                    .andExpect(jsonPath("$[0].assignmentStatus").value("WAITING"));
        }

        @Test
        @DisplayName("성공: 날짜+상태 필터 동시 → 교집합 결과만 반환")
        void search_dateAndStatus_200() throws Exception {
            // 같은 날짜, 다른 날짜 배차 각 1건 추가
            AsRequest sameDate  = saveAsRequest(LocalDate.of(2026, 7, 1));
            AsRequest otherDate = saveAsRequest(LocalDate.of(2026, 8, 1));
            asAssignmentRepository.save(AsAssignment.create(sameDate,  engineer, agency, AssignType.MANUAL));
            asAssignmentRepository.save(AsAssignment.create(otherDate, engineer, agency, AssignType.MANUAL));

            // 2026-07-01 + WAITING → 2건 (기본 setup 1건 + sameDate 1건)
            mockMvc.perform(get("/api/agency/as-assignments/search")
                            .param("date", "2026-07-01")
                            .param("status", "WAITING")
                            .header("Authorization", "Bearer " + agencyToken))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.length()").value(2));
        }

        @Test
        @DisplayName("성공: 파라미터 없음 → date 기본값 오늘, 결과 없으면 204 반환")
        void search_noParams_defaultToday_204() throws Exception {
            // BeforeEach 의 배차는 2026-07-01 날짜 → 오늘 날짜(테스트 실행일)와 다르므로 결과 없음
            mockMvc.perform(get("/api/agency/as-assignments/search")
                            .header("Authorization", "Bearer " + agencyToken))
                    .andExpect(status().isNoContent());
        }

        @Test
        @DisplayName("성공: 다른 대행사 배차는 필터 결과에 포함되지 않음")
        void search_otherAgencyExcluded_200() throws Exception {
            Agencies otherAgency = agenciesRepository.save(Agencies.builder()
                    .agencyName("다른대행사").businessNumber("OTHER-BIZ-001")
                    .agencyAddress("서울특별시 서초구").agencyFeeRate(4.0)
                    .approvalStatus(AgencyStatus.APPROVED).build());
            User otherEngineer = userRepository.save(User.builder()
                    .email("other@agency.com").passwordHash("hashed")
                    .name("다른기사").phone("010-9999-8888")
                    .role(Role.ENGINEER).agency(otherAgency).build());
            AsRequest otherReq = saveAsRequest(LocalDate.of(2026, 7, 1));
            asAssignmentRepository.save(AsAssignment.create(otherReq, otherEngineer, otherAgency, AssignType.MANUAL));

            // 현재 대행사 관리자로 조회 → 본 대행사 1건만 반환
            mockMvc.perform(get("/api/agency/as-assignments/search")
                            .param("date", "2026-07-01")
                            .header("Authorization", "Bearer " + agencyToken))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.length()").value(1))
                    .andExpect(jsonPath("$[0].engineerName").value("테스트기사"));
        }

        @Test
        @DisplayName("성공: 같은 날짜 배차 2건 → scheduledDate ASC 정렬 확인")
        void search_resultSortedByDate_200() throws Exception {
            // 같은 날(2026-07-01)에 배차 1건 추가 → 총 2건
            AsRequest secondReq = saveAsRequest(LocalDate.of(2026, 7, 1));
            asAssignmentRepository.save(AsAssignment.create(secondReq, engineer, agency, AssignType.MANUAL));

            // date=2026-07-01, status=WAITING → 2건 반환 (순서 보장)
            mockMvc.perform(get("/api/agency/as-assignments/search")
                            .param("date", "2026-07-01")
                            .param("status", "WAITING")
                            .header("Authorization", "Bearer " + agencyToken))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.length()").value(2))
                    .andExpect(jsonPath("$[0].scheduledDate").value("2026-07-01"))
                    .andExpect(jsonPath("$[1].scheduledDate").value("2026-07-01"));
        }

        @Test
        @DisplayName("실패: 허용되지 않는 status 값 → 400 Bad Request")
        void search_invalidStatus_400() throws Exception {
            mockMvc.perform(get("/api/agency/as-assignments/search")
                            .param("status", "UNKNOWN_STATUS")
                            .header("Authorization", "Bearer " + agencyToken))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.success").value(false))
                    .andExpect(jsonPath("$.message").value(
                            "유효하지 않은 배차 상태입니다. 허용 값: WAITING, ACCEPTED, REJECTED, COMPLETED"));
        }

        @Test
        @DisplayName("실패: CUSTOMER 권한 토큰 → 401 Unauthorized")
        void search_customerRole_401() throws Exception {
            mockMvc.perform(get("/api/agency/as-assignments/search")
                            .header("Authorization", "Bearer " + customerToken))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("실패: 인증 토큰 없음 → 401 Unauthorized")
        void search_noToken_401() throws Exception {
            mockMvc.perform(get("/api/agency/as-assignments/search"))
                    .andExpect(status().isUnauthorized());
        }
    }

    // ── 헬퍼 ──────────────────────────────────────────────────────

    private void saveExpectedRepairCost(Symptom symptom, ApplianceCategory cat, int avgCost) {
        expectedRepairCostRepository.save(
                ExpectedRepairCost.createForTest(cat, symptom, avgCost, 5));
    }

    private AsRequest saveAsRequest(LocalDate scheduledDate) {
        return asRequestRepository.save(AsRequest.builder()
                .customer(customer).appliance(appliance).symptom(symptom)
                .visitRegion(region).visitAddressDetail("강남구 테헤란로 123")
                .scheduledDate(scheduledDate).scheduledTime("10:00")
                .build());
    }

    // ─────────────────────────────────────────────────────────────
    //  POST /api/assignment/{assignmentId}/reassign
    // ─────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("POST /api/assignment/{assignmentId}/reassign — 재배차")
    class ReassignAssignment {

        @Test
        @DisplayName("성공: MANUAL 배차 건 → 재배차 없음, 고객 알림 저장 후 MANUAL_NOTIFIED 반환")
        void reassign_manual_notified_200() throws Exception {
            // BeforeEach 의 assignment 는 assignType=MANUAL (AsRequest 현재 MVP 고정값)
            mockMvc.perform(post("/api/agency/as-assignments/" + assignment.getId() + "/reassign")
                            .header("Authorization", "Bearer " + agencyToken))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.result").value("MANUAL_NOTIFIED"))
                    .andExpect(jsonPath("$.notificationId").isNumber())
                    .andExpect(jsonPath("$.newAssignmentId").isEmpty())
                    // MANUAL 재배차 — matchReason 없음
                    .andExpect(jsonPath("$.matchReason").isEmpty())
                    .andExpect(jsonPath("$.message").exists());

            // DB 직접 검증 — 고객에게 알림 1건 저장
            assertThat(notificationRepository.findByUser_IdOrderByCreatedAtDesc(customer.getId()))
                    .hasSize(1)
                    .allSatisfy(n -> {
                        assertThat(n.getType()).isEqualTo("AS_STATUS");
                        assertThat(n.getTitle()).isEqualTo("A/S 재신청 필요");
                        assertThat(n.getBody()).contains(asRequest.getId().toString());
                    });
        }

        @Test
        @DisplayName("성공: MANUAL 재배차 반복 요청 시 알림이 누적 저장됨")
        void reassign_manual_twice_bothNotified_200() throws Exception {
            // 2회 재배차 요청
            mockMvc.perform(post("/api/agency/as-assignments/" + assignment.getId() + "/reassign")
                            .header("Authorization", "Bearer " + agencyToken))
                    .andExpect(status().isOk());
            mockMvc.perform(post("/api/agency/as-assignments/" + assignment.getId() + "/reassign")
                            .header("Authorization", "Bearer " + agencyToken))
                    .andExpect(status().isOk());

            // 알림 2건 저장 확인
            assertThat(notificationRepository.findByUser_IdOrderByCreatedAtDesc(customer.getId()))
                    .hasSize(2);
        }

        @Test
        @DisplayName("실패: 존재하지 않는 assignmentId → 404 Not Found")
        void reassign_notFound_404() throws Exception {
            mockMvc.perform(post("/api/agency/as-assignments/99999/reassign")
                            .header("Authorization", "Bearer " + agencyToken))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.success").value(false))
                    .andExpect(jsonPath("$.message").value("해당 배차 내역을 찾을 수 없습니다."));
        }

        @Test
        @DisplayName("실패: CUSTOMER 권한 토큰 → 401 Unauthorized")
        void reassign_customerRole_401() throws Exception {
            mockMvc.perform(post("/api/agency/as-assignments/" + assignment.getId() + "/reassign")
                            .header("Authorization", "Bearer " + customerToken))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("실패: 인증 토큰 없음 → 401 Unauthorized")
        void reassign_noToken_401() throws Exception {
            mockMvc.perform(post("/api/agency/as-assignments/" + assignment.getId() + "/reassign"))
                    .andExpect(status().isUnauthorized());
        }
    }

    // ─────────────────────────────────────────────────────────────
    //  GET /api/agency/as-assignments/in-progress
    // ─────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("GET /api/agency/as-assignments/in-progress — 진행 중 배정 목록 조회")
    class GetInProgress {

        @Test
        @DisplayName("성공: ACCEPTED 배정 없음 → 200 OK, content 빈 배열 + stats 0")
        void inProgress_empty_200() throws Exception {
            // BeforeEach 의 assignment 는 WAITING 상태 → ACCEPTED 없음
            mockMvc.perform(get("/api/agency/as-assignments/in-progress")
                            .header("Authorization", "Bearer " + agencyTokenWithAgencyId))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.stats.totalCount").value(0))
                    .andExpect(jsonPath("$.content").isArray())
                    .andExpect(jsonPath("$.content.length()").value(0))
                    .andExpect(jsonPath("$.totalElements").value(0));
        }

        @Test
        @DisplayName("성공: ACCEPTED 배정 1건 → stats·content 핵심 필드 검증")
        void inProgress_oneAssignment_200() throws Exception {
            asAssignmentRepository.updateStatus(assignment.getId(), "ACCEPTED");

            mockMvc.perform(get("/api/agency/as-assignments/in-progress")
                            .header("Authorization", "Bearer " + agencyTokenWithAgencyId))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.stats.totalCount").value(1))
                    .andExpect(jsonPath("$.content.length()").value(1))
                    .andExpect(jsonPath("$.content[0].assignmentId").value(assignment.getId()))
                    .andExpect(jsonPath("$.content[0].requestId").value(asRequest.getId()))
                    .andExpect(jsonPath("$.content[0].createdAt").exists())
                    .andExpect(jsonPath("$.content[0].customerName").value("테스트고객"))
                    .andExpect(jsonPath("$.content[0].engineerName").value("테스트기사"))
                    .andExpect(jsonPath("$.content[0].assignmentStatus").value("ACCEPTED"))
                    .andExpect(jsonPath("$.content[0].visitDate").value("2026-07-01"))
                    .andExpect(jsonPath("$.content[0].logs").isArray())
                    .andExpect(jsonPath("$.content[0].stepTimes").isMap())
                    .andExpect(jsonPath("$.totalElements").value(1))
                    .andExpect(jsonPath("$.currentPage").value(0))
                    .andExpect(jsonPath("$.size").value(10));
        }

        @Test
        @DisplayName("성공: 다른 대행사 배정은 content에 포함되지 않음")
        void inProgress_otherAgencyExcluded_200() throws Exception {
            asAssignmentRepository.updateStatus(assignment.getId(), "ACCEPTED");

            Agencies other = agenciesRepository.save(Agencies.builder()
                    .agencyName("다른대행사").businessNumber("OTHER-002")
                    .agencyAddress("서울 서초구").agencyFeeRate(3.0)
                    .approvalStatus(com.careflow.common.enums.AgencyStatus.APPROVED).build());
            User otherEng = userRepository.save(User.builder()
                    .email("other@eng.com").passwordHash("hash").name("다른기사")
                    .phone("010-9999-0000").role(Role.ENGINEER).agency(other).build());
            AsRequest otherReq = saveAsRequest(LocalDate.of(2026, 7, 1));
            AsAssignment otherA = asAssignmentRepository.save(
                    AsAssignment.create(otherReq, otherEng, other, AssignType.MANUAL));
            asAssignmentRepository.updateStatus(otherA.getId(), "ACCEPTED");

            mockMvc.perform(get("/api/agency/as-assignments/in-progress")
                            .header("Authorization", "Bearer " + agencyTokenWithAgencyId))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content.length()").value(1))
                    .andExpect(jsonPath("$.content[0].engineerName").value("테스트기사"));
        }

        @Test
        @DisplayName("성공: AsStatusLog 존재 → content[0].logs에 포함")
        void inProgress_withStatusLog_logsIncluded() throws Exception {
            asAssignmentRepository.updateStatus(assignment.getId(), "ACCEPTED");
            asStatusLogRepository.save(AsStatusLog.builder()
                    .asRequest(asRequest).changedBy(engineer)
                    .fromStatus(null).toStatus("ENGINEER_DEPARTED").memo("출발").build());

            mockMvc.perform(get("/api/agency/as-assignments/in-progress")
                            .header("Authorization", "Bearer " + agencyTokenWithAgencyId))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content[0].logs.length()").value(1))
                    .andExpect(jsonPath("$.content[0].logs[0].toStatus").value("ENGINEER_DEPARTED"))
                    .andExpect(jsonPath("$.content[0].latestLogStatus").value("ENGINEER_DEPARTED"));
        }

        @Test
        @DisplayName("성공: latestLogStatus 필터 파라미터 → 해당 상태만 반환")
        void inProgress_latestLogStatusFilter_applied() throws Exception {
            // ACCEPTED 배정 2건 세팅
            asAssignmentRepository.updateStatus(assignment.getId(), "ACCEPTED");
            AsRequest req2 = saveAsRequest(LocalDate.of(2026, 7, 1));
            AsAssignment assign2 = asAssignmentRepository.save(
                    AsAssignment.create(req2, engineer, agency, AssignType.MANUAL));
            asAssignmentRepository.updateStatus(assign2.getId(), "ACCEPTED");

            // 첫 번째 배정에만 ENGINEER_DEPARTED 로그 추가
            asStatusLogRepository.save(AsStatusLog.builder()
                    .asRequest(asRequest).changedBy(engineer)
                    .fromStatus(null).toStatus("ENGINEER_DEPARTED").memo("출발").build());

            // latestLogStatus=ENGINEER_DEPARTED 필터 → 1건만 반환
            mockMvc.perform(get("/api/agency/as-assignments/in-progress")
                            .param("latestLogStatus", "ENGINEER_DEPARTED")
                            .header("Authorization", "Bearer " + agencyTokenWithAgencyId))
                    .andExpect(status().isOk())
                    // stats.totalCount 는 필터 전 전체 2건
                    .andExpect(jsonPath("$.stats.totalCount").value(2))
                    // content 는 필터 후 1건
                    .andExpect(jsonPath("$.content.length()").value(1))
                    .andExpect(jsonPath("$.content[0].latestLogStatus").value("ENGINEER_DEPARTED"));
        }

        @Test
        @DisplayName("성공: 페이지네이션 — ACCEPTED 3건, size=2, page=0 → content 2건·totalElements=3")
        void inProgress_pagination_page0() throws Exception {
            asAssignmentRepository.updateStatus(assignment.getId(), "ACCEPTED");
            AsRequest req2 = saveAsRequest(LocalDate.of(2026, 7, 1));
            AsAssignment a2 = asAssignmentRepository.save(
                    AsAssignment.create(req2, engineer, agency, AssignType.MANUAL));
            asAssignmentRepository.updateStatus(a2.getId(), "ACCEPTED");
            AsRequest req3 = saveAsRequest(LocalDate.of(2026, 7, 1));
            AsAssignment a3 = asAssignmentRepository.save(
                    AsAssignment.create(req3, engineer, agency, AssignType.MANUAL));
            asAssignmentRepository.updateStatus(a3.getId(), "ACCEPTED");

            mockMvc.perform(get("/api/agency/as-assignments/in-progress")
                            .param("page", "0")
                            .param("size", "2")
                            .header("Authorization", "Bearer " + agencyTokenWithAgencyId))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content.length()").value(2))
                    .andExpect(jsonPath("$.totalElements").value(3))
                    .andExpect(jsonPath("$.totalPages").value(2))
                    .andExpect(jsonPath("$.currentPage").value(0));
        }

        @Test
        @DisplayName("실패: CUSTOMER 권한 → 401 Unauthorized")
        void inProgress_customerRole_401() throws Exception {
            mockMvc.perform(get("/api/agency/as-assignments/in-progress")
                            .header("Authorization", "Bearer " + customerToken))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("실패: 토큰 없음 → 401 Unauthorized")
        void inProgress_noToken_401() throws Exception {
            mockMvc.perform(get("/api/agency/as-assignments/in-progress"))
                    .andExpect(status().isUnauthorized());
        }
    }

    // ─────────────────────────────────────────────────────────────
    //  GET /api/agency/as-assignments/completed
    // ─────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("GET /api/agency/as-assignments/completed — 완료 배정 목록 조회")
    class GetCompleted {

        @Test
        @DisplayName("성공: COMPLETED 배정 없음 → 204 No Content")
        void completed_empty_204() throws Exception {
            mockMvc.perform(get("/api/agency/as-assignments/completed")
                            .header("Authorization", "Bearer " + agencyTokenWithAgencyId))
                    .andExpect(status().isNoContent());
        }

        @Test
        @DisplayName("성공: COMPLETED + 작업보고서 있고 리뷰 없음 → rating null 반환")
        void completed_withReport_noReview_200() throws Exception {
            asAssignmentRepository.updateStatus(assignment.getId(), "COMPLETED");
            workReportRepository.save(WorkReport.builder()
                    .asRequest(asRequest).engineer(engineer)
                    .diagnosisResult(DiagnosisResult.REPAIRED)
                    .workDurationMin(60).finalAmount(80000).memo("수리 완료").build());

            mockMvc.perform(get("/api/agency/as-assignments/completed")
                            .header("Authorization", "Bearer " + agencyTokenWithAgencyId))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.length()").value(1))
                    .andExpect(jsonPath("$[0].assignmentId").value(assignment.getId()))
                    .andExpect(jsonPath("$[0].customerName").value("테스트고객"))
                    .andExpect(jsonPath("$[0].diagnosisResult").value("REPAIRED"))
                    .andExpect(jsonPath("$[0].finalAmount").value(80000))
                    .andExpect(jsonPath("$[0].rating").doesNotExist());
        }

        @Test
        @DisplayName("성공: 리뷰 존재 → rating·reviewContent 포함")
        void completed_withReview_200() throws Exception {
            asAssignmentRepository.updateStatus(assignment.getId(), "COMPLETED");
            workReportRepository.save(WorkReport.builder()
                    .asRequest(asRequest).engineer(engineer)
                    .diagnosisResult(DiagnosisResult.REPAIRED)
                    .workDurationMin(60).finalAmount(80000).memo("완료").build());
            reviewRepository.save(Review.create(asRequest, customer, engineer, 5, "정말 감사합니다"));

            mockMvc.perform(get("/api/agency/as-assignments/completed")
                            .header("Authorization", "Bearer " + agencyTokenWithAgencyId))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$[0].rating").value(5))
                    .andExpect(jsonPath("$[0].reviewContent").value("정말 감사합니다"));
        }

        @Test
        @DisplayName("성공: 작업보고서 없는 COMPLETED 배정 → 결과에서 제외 → 204 No Content")
        void completed_noReport_excluded_204() throws Exception {
            asAssignmentRepository.updateStatus(assignment.getId(), "COMPLETED");
            // 보고서 저장 안 함

            mockMvc.perform(get("/api/agency/as-assignments/completed")
                            .header("Authorization", "Bearer " + agencyTokenWithAgencyId))
                    .andExpect(status().isNoContent());
        }

        @Test
        @DisplayName("실패: CUSTOMER 권한 → 401 Unauthorized")
        void completed_customerRole_401() throws Exception {
            mockMvc.perform(get("/api/agency/as-assignments/completed")
                            .header("Authorization", "Bearer " + customerToken))
                    .andExpect(status().isUnauthorized());
        }
    }

    // ─────────────────────────────────────────────────────────────
    //  POST /api/agency/as-assignments/change — 기사 변경
    // ─────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("POST /api/agency/as-assignments/change — 배정 기사 변경")
    class ChangeEngineer {

        @Test
        @DisplayName("성공: WAITING 배정 기사 변경 → 기존 REJECTED, 신규 WAITING 생성, DB 검증")
        void change_waiting_201() throws Exception {
            User newEngineer = userRepository.save(User.builder()
                    .email("new@eng.com").passwordHash("hash").name("신규기사")
                    .phone("010-5555-6666").role(Role.ENGINEER).agency(agency).build());

            String body = objectMapper.writeValueAsString(
                    new com.careflow.assignment.dto.AssignmentChangeEngineerRequest(
                            assignment.getId(), newEngineer.getId()));

            mockMvc.perform(post("/api/agency/as-assignments/change")
                            .header("Authorization", "Bearer " + agencyTokenWithAgencyId)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.newAssignmentId").isNumber())
                    .andExpect(jsonPath("$.requestId").value(asRequest.getId()))
                    .andExpect(jsonPath("$.newEngineerId").value(newEngineer.getId()))
                    .andExpect(jsonPath("$.newEngineerName").value("신규기사"))
                    .andExpect(jsonPath("$.assignmentStatus").value("WAITING"));

            // DB 직접 검증: 기존 배정 → REJECTED
            String oldStatus = asAssignmentRepository.findById(assignment.getId())
                    .orElseThrow().getStatus();
            assertThat(oldStatus).isEqualTo("REJECTED");
        }

        @Test
        @DisplayName("성공: REJECTED 배정도 기사 변경 가능 → 201 Created (재배차 알림 수동 배정)")
        void change_rejected_201() throws Exception {
            asAssignmentRepository.updateStatus(assignment.getId(), "REJECTED");
            User newEngineer = userRepository.save(User.builder()
                    .email("new-rejected@eng.com").passwordHash("hash").name("재배차기사")
                    .phone("010-6666-5555").role(Role.ENGINEER).agency(agency).build());

            String body = objectMapper.writeValueAsString(
                    new com.careflow.assignment.dto.AssignmentChangeEngineerRequest(
                            assignment.getId(), newEngineer.getId()));

            mockMvc.perform(post("/api/agency/as-assignments/change")
                            .header("Authorization", "Bearer " + agencyTokenWithAgencyId)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.newEngineerId").value(newEngineer.getId()))
                    .andExpect(jsonPath("$.assignmentStatus").value("WAITING"));

            // DB 직접 검증: 기존 배정은 계속 REJECTED로 유지(재취소되지 않음)
            String oldStatus = asAssignmentRepository.findById(assignment.getId())
                    .orElseThrow().getStatus();
            assertThat(oldStatus).isEqualTo("REJECTED");
        }

        @Test
        @DisplayName("실패: ACCEPTED 상태 배정은 기사 변경 불가 → 403 Forbidden")
        void change_accepted_403() throws Exception {
            asAssignmentRepository.updateStatus(assignment.getId(), "ACCEPTED");
            User newEng = userRepository.save(User.builder()
                    .email("new2@eng.com").passwordHash("hash").name("다른기사")
                    .phone("010-7777-8888").role(Role.ENGINEER).agency(agency).build());

            String body = objectMapper.writeValueAsString(
                    new com.careflow.assignment.dto.AssignmentChangeEngineerRequest(
                            assignment.getId(), newEng.getId()));

            mockMvc.perform(post("/api/agency/as-assignments/change")
                            .header("Authorization", "Bearer " + agencyTokenWithAgencyId)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("실패: 존재하지 않는 assignmentId → 404 Not Found")
        void change_notFound_404() throws Exception {
            String body = objectMapper.writeValueAsString(
                    new com.careflow.assignment.dto.AssignmentChangeEngineerRequest(99999L, engineer.getId()));

            mockMvc.perform(post("/api/agency/as-assignments/change")
                            .header("Authorization", "Bearer " + agencyTokenWithAgencyId)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isNotFound());
        }

        @Test
        @DisplayName("실패: 존재하지 않는 newEngineerId → 404 Not Found")
        void change_engineerNotFound_404() throws Exception {
            String body = objectMapper.writeValueAsString(
                    new com.careflow.assignment.dto.AssignmentChangeEngineerRequest(
                            assignment.getId(), 99999L));

            mockMvc.perform(post("/api/agency/as-assignments/change")
                            .header("Authorization", "Bearer " + agencyTokenWithAgencyId)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isNotFound());
        }

        @Test
        @DisplayName("실패: CUSTOMER 권한 → 401 Unauthorized")
        void change_customerRole_401() throws Exception {
            String body = objectMapper.writeValueAsString(
                    new com.careflow.assignment.dto.AssignmentChangeEngineerRequest(
                            assignment.getId(), engineer.getId()));

            mockMvc.perform(post("/api/agency/as-assignments/change")
                            .header("Authorization", "Bearer " + customerToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isUnauthorized());
        }
    }

    // ─────────────────────────────────────────────────────────────
    //  PUT /api/agency/as-assignments/{assignmentId}/schedule
    // ─────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("PUT /api/agency/as-assignments/{id}/schedule — 방문 일정 변경")
    class UpdateSchedule {

        private String scheduleBody(String date, String time) throws Exception {
            return objectMapper.writeValueAsString(
                    new com.careflow.assignment.dto.AssignmentScheduleRequest(
                            java.time.LocalDate.parse(date), time));
        }

        @Test
        @DisplayName("성공: WAITING 상태 → 200 OK, scheduledDate·scheduledTime 응답 검증")
        void schedule_waiting_200() throws Exception {
            mockMvc.perform(put("/api/agency/as-assignments/" + assignment.getId() + "/schedule")
                            .header("Authorization", "Bearer " + agencyTokenWithAgencyId)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(scheduleBody("2026-08-10", "14:00")))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.assignmentId").value(assignment.getId()))
                    .andExpect(jsonPath("$.requestId").value(asRequest.getId()))
                    .andExpect(jsonPath("$.scheduledDate").value("2026-08-10"))
                    .andExpect(jsonPath("$.scheduledTime").value("14:00"));
        }

        @Test
        @DisplayName("성공: ACCEPTED 상태도 일정 변경 가능 → 200 OK")
        void schedule_accepted_200() throws Exception {
            asAssignmentRepository.updateStatus(assignment.getId(), "ACCEPTED");

            mockMvc.perform(put("/api/agency/as-assignments/" + assignment.getId() + "/schedule")
                            .header("Authorization", "Bearer " + agencyTokenWithAgencyId)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(scheduleBody("2026-08-15", "10:00")))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.scheduledDate").value("2026-08-15"));
        }

        @Test
        @DisplayName("실패: REJECTED 상태 → 403 Forbidden")
        void schedule_rejected_403() throws Exception {
            asAssignmentRepository.updateStatus(assignment.getId(), "REJECTED");

            mockMvc.perform(put("/api/agency/as-assignments/" + assignment.getId() + "/schedule")
                            .header("Authorization", "Bearer " + agencyTokenWithAgencyId)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(scheduleBody("2026-08-10", "14:00")))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("실패: scheduledDate 누락 → 400 Bad Request (Bean Validation)")
        void schedule_missingDate_400() throws Exception {
            String body = "{\"scheduledTime\":\"14:00\"}";

            mockMvc.perform(put("/api/agency/as-assignments/" + assignment.getId() + "/schedule")
                            .header("Authorization", "Bearer " + agencyTokenWithAgencyId)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("실패: 잘못된 시간 형식 → 400 Bad Request (Bean Validation)")
        void schedule_invalidTime_400() throws Exception {
            String body = "{\"scheduledDate\":\"2026-08-10\",\"scheduledTime\":\"25:99\"}";

            mockMvc.perform(put("/api/agency/as-assignments/" + assignment.getId() + "/schedule")
                            .header("Authorization", "Bearer " + agencyTokenWithAgencyId)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("실패: 존재하지 않는 assignmentId → 404 Not Found")
        void schedule_notFound_404() throws Exception {
            mockMvc.perform(put("/api/agency/as-assignments/99999/schedule")
                            .header("Authorization", "Bearer " + agencyTokenWithAgencyId)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(scheduleBody("2026-08-10", "14:00")))
                    .andExpect(status().isNotFound());
        }

        @Test
        @DisplayName("실패: CUSTOMER 권한 → 401 Unauthorized")
        void schedule_customerRole_401() throws Exception {
            mockMvc.perform(put("/api/agency/as-assignments/" + assignment.getId() + "/schedule")
                            .header("Authorization", "Bearer " + customerToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(scheduleBody("2026-08-10", "14:00")))
                    .andExpect(status().isUnauthorized());
        }
    }

    // ─────────────────────────────────────────────────────────────
    //  DELETE /api/agency/as-assignments/{assignmentId}
    // ─────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("DELETE /api/agency/as-assignments/{id} — 배정 취소")
    class CancelAssignment {

        @Test
        @DisplayName("성공: WAITING 배정 취소 → 200 OK, DB: assignment REJECTED · asRequest AGENCY_RECEIVED")
        void cancel_waiting_200() throws Exception {
            mockMvc.perform(delete("/api/agency/as-assignments/" + assignment.getId())
                            .header("Authorization", "Bearer " + agencyTokenWithAgencyId))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.assignmentId").value(assignment.getId()))
                    .andExpect(jsonPath("$.requestId").value(asRequest.getId()))
                    .andExpect(jsonPath("$.cancelledStatus").value("REJECTED"))
                    .andExpect(jsonPath("$.message").isString());

            // DB 직접 검증
            String assignStatus = asAssignmentRepository.findById(assignment.getId())
                    .orElseThrow().getStatus();
            assertThat(assignStatus).isEqualTo("REJECTED");

            String requestStatus = asRequestRepository.findById(asRequest.getId())
                    .orElseThrow().getStatus().name();
            assertThat(requestStatus).isEqualTo("AGENCY_RECEIVED");
        }

        @Test
        @DisplayName("성공: ACCEPTED 배정도 취소 가능 → 200 OK")
        void cancel_accepted_200() throws Exception {
            asAssignmentRepository.updateStatus(assignment.getId(), "ACCEPTED");

            mockMvc.perform(delete("/api/agency/as-assignments/" + assignment.getId())
                            .header("Authorization", "Bearer " + agencyTokenWithAgencyId))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.cancelledStatus").value("REJECTED"));
        }

        @Test
        @DisplayName("실패: 이미 REJECTED 상태 → 403 Forbidden")
        void cancel_alreadyRejected_403() throws Exception {
            asAssignmentRepository.updateStatus(assignment.getId(), "REJECTED");

            mockMvc.perform(delete("/api/agency/as-assignments/" + assignment.getId())
                            .header("Authorization", "Bearer " + agencyTokenWithAgencyId))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("실패: 이미 COMPLETED 상태 → 403 Forbidden")
        void cancel_completed_403() throws Exception {
            asAssignmentRepository.updateStatus(assignment.getId(), "COMPLETED");

            mockMvc.perform(delete("/api/agency/as-assignments/" + assignment.getId())
                            .header("Authorization", "Bearer " + agencyTokenWithAgencyId))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("실패: 존재하지 않는 assignmentId → 404 Not Found")
        void cancel_notFound_404() throws Exception {
            mockMvc.perform(delete("/api/agency/as-assignments/99999")
                            .header("Authorization", "Bearer " + agencyTokenWithAgencyId))
                    .andExpect(status().isNotFound());
        }

        @Test
        @DisplayName("실패: CUSTOMER 권한 → 401 Unauthorized")
        void cancel_customerRole_401() throws Exception {
            mockMvc.perform(delete("/api/agency/as-assignments/" + assignment.getId())
                            .header("Authorization", "Bearer " + customerToken))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("실패: 토큰 없음 → 401 Unauthorized")
        void cancel_noToken_401() throws Exception {
            mockMvc.perform(delete("/api/agency/as-assignments/" + assignment.getId()))
                    .andExpect(status().isUnauthorized());
        }
    }
}
