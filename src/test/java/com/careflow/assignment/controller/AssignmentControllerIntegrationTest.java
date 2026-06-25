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
import com.careflow.engineer.domain.enums.SkillLevel;
import com.careflow.engineer.repository.EngineerProfileRepository;
import com.careflow.region.entity.Regions;
import com.careflow.region.repository.RegionRepository;
import com.careflow.report.domain.entity.WorkReport;
import com.careflow.report.domain.enums.DiagnosisResult;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
                agencyManager.getId(), agencyManager.getEmail(), "AGENCY");

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
                customer.getId(), customer.getEmail(), "CUSTOMER");

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
            mockMvc.perform(get("/api/assignment/detail/" + assignment.getId())
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

            mockMvc.perform(get("/api/assignment/detail/" + assignment.getId())
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

            mockMvc.perform(get("/api/assignment/detail/" + assignment.getId())
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

            mockMvc.perform(get("/api/assignment/detail/" + assignment.getId())
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

            mockMvc.perform(get("/api/assignment/detail/" + assignment.getId())
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

            mockMvc.perform(get("/api/assignment/detail/" + assignment.getId())
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
            mockMvc.perform(get("/api/assignment/detail/99999")
                            .header("Authorization", "Bearer " + agencyToken))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.success").value(false))
                    .andExpect(jsonPath("$.message").value("해당 배차 내역을 찾을 수 없습니다."));
        }

        @Test
        @DisplayName("실패: CUSTOMER 권한 토큰 → 401 Unauthorized + 에러 포맷 검증")
        void getDetail_customerRole_401() throws Exception {
            mockMvc.perform(get("/api/assignment/detail/" + assignment.getId())
                            .header("Authorization", "Bearer " + customerToken))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.success").value(false));
        }

        @Test
        @DisplayName("실패: 인증 토큰 없음 → 401 Unauthorized")
        void getDetail_noToken_401() throws Exception {
            mockMvc.perform(get("/api/assignment/detail/" + assignment.getId()))
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

            mockMvc.perform(get("/api/assignment/search")
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
            mockMvc.perform(get("/api/assignment/search")
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
            mockMvc.perform(get("/api/assignment/search")
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
            mockMvc.perform(get("/api/assignment/search")
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
            mockMvc.perform(get("/api/assignment/search")
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
            mockMvc.perform(get("/api/assignment/search")
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
            mockMvc.perform(get("/api/assignment/search")
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
            mockMvc.perform(get("/api/assignment/search")
                            .header("Authorization", "Bearer " + customerToken))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("실패: 인증 토큰 없음 → 401 Unauthorized")
        void search_noToken_401() throws Exception {
            mockMvc.perform(get("/api/assignment/search"))
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
            mockMvc.perform(post("/api/assignment/" + assignment.getId() + "/reassign")
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
            mockMvc.perform(post("/api/assignment/" + assignment.getId() + "/reassign")
                            .header("Authorization", "Bearer " + agencyToken))
                    .andExpect(status().isOk());
            mockMvc.perform(post("/api/assignment/" + assignment.getId() + "/reassign")
                            .header("Authorization", "Bearer " + agencyToken))
                    .andExpect(status().isOk());

            // 알림 2건 저장 확인
            assertThat(notificationRepository.findByUser_IdOrderByCreatedAtDesc(customer.getId()))
                    .hasSize(2);
        }

        @Test
        @DisplayName("실패: 존재하지 않는 assignmentId → 404 Not Found")
        void reassign_notFound_404() throws Exception {
            mockMvc.perform(post("/api/assignment/99999/reassign")
                            .header("Authorization", "Bearer " + agencyToken))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.success").value(false))
                    .andExpect(jsonPath("$.message").value("해당 배차 내역을 찾을 수 없습니다."));
        }

        @Test
        @DisplayName("실패: CUSTOMER 권한 토큰 → 401 Unauthorized")
        void reassign_customerRole_401() throws Exception {
            mockMvc.perform(post("/api/assignment/" + assignment.getId() + "/reassign")
                            .header("Authorization", "Bearer " + customerToken))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("실패: 인증 토큰 없음 → 401 Unauthorized")
        void reassign_noToken_401() throws Exception {
            mockMvc.perform(post("/api/assignment/" + assignment.getId() + "/reassign"))
                    .andExpect(status().isUnauthorized());
        }
    }
}
