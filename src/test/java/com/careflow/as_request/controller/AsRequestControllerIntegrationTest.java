package com.careflow.as_request.controller;

import com.careflow.agency.entity.Agencies;
import com.careflow.agency.repository.AgenciesRepository;
import com.careflow.appliance.entity.Appliance;
import com.careflow.appliance.entity.ApplianceCategory;
import com.careflow.appliance.repository.ApplianceCategoryRepository;
import com.careflow.appliance.repository.ApplianceRepository;
import com.careflow.as_request.entity.AsRequest;
import com.careflow.as_request.repository.AsRequestRepository;
import com.careflow.assignment.repository.AsAssignmentRepository;
import com.careflow.auth.security.JwtProvider;
import com.careflow.common.enums.AgencyStatus;
import com.careflow.common.enums.AsStatus;
import com.careflow.common.enums.RegisterMethod;
import com.careflow.common.enums.Role;
import com.careflow.engineer.domain.entity.*;
import com.careflow.engineer.domain.enums.ScheduleStatus;
import com.careflow.engineer.domain.enums.SkillLevel;
import com.careflow.engineer.repository.*;
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
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.time.LocalTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("local")
@Sql(scripts = "/as_request_cleanup.sql", executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
@DisplayName("AsRequestController 통합 테스트 (H2)")
class AsRequestControllerIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private JwtProvider jwtProvider;

    // ── Repositories ──
    @Autowired private UserRepository userRepository;
    @Autowired private AgenciesRepository agenciesRepository;
    @Autowired private RegionRepository regionRepository;
    @Autowired private ApplianceCategoryRepository categoryRepository;
    @Autowired private ApplianceRepository applianceRepository;
    @Autowired private SymptomRepository symptomRepository;
    @Autowired private EngineerProfileRepository engineerProfileRepository;
    @Autowired private EngineerScheduleRepository scheduleRepository;
    @Autowired private EngineerExpertBrandRepository expertBrandRepository;
    @Autowired private EngineerServiceRegionRepository serviceRegionRepository;
    @Autowired private AsRequestRepository asRequestRepository;
    @Autowired private AsAssignmentRepository asAssignmentRepository;

    // ── 공통 픽스처 ──
    private User customer;
    private User engineer;
    private Agencies agency;
    private Regions region;
    private ApplianceCategory category;
    private Appliance appliance;
    private Symptom symptom;
    private String customerToken;

    /** AUTO 배정 테스트에 사용하는 예약 날짜 — 이 날짜로 기사 스케줄 등록 */
    private static final LocalDate SCHEDULED_DATE = LocalDate.of(2026, 7, 1);

    @BeforeEach
    void setUp() {
        // 1. 방문 지역
        region = regionRepository.save(Regions.create("서울특별시 강남구", null, 1, 0));

        // 2. 가전 카테고리 (depth=2 소분류) — v5 신규 API: createRoot → createChild 2단계 생성
        ApplianceCategory rootCat = categoryRepository.save(ApplianceCategory.createRoot("에어컨", 1));
        category = categoryRepository.save(ApplianceCategory.createChild("에어컨 소분류", rootCat, 1));

        // 3. 대행사 (APPROVED)
        agency = agenciesRepository.save(Agencies.builder()
                .agencyName("테스트대행사").businessNumber("TEST-BIZ-001")
                .agencyAddress("서울특별시 강남구").agencyFeeRate(5.0)
                .approvalStatus(AgencyStatus.APPROVED).build());

        // 4. 고객 계정 + JWT
        customer = userRepository.save(User.builder()
                .email("customer@test.com").passwordHash("hashed")
                .name("테스트고객").phone("010-1111-2222").role(Role.CUSTOMER).build());
        customerToken = jwtProvider.generateAccessToken(
                customer.getId(), customer.getEmail(), "CUSTOMER");

        // 5. 수리 기사 계정 (대행사 소속)
        engineer = userRepository.save(User.builder()
                .email("engineer@test.com").passwordHash("hashed")
                .name("테스트기사").phone("010-3333-4444").role(Role.ENGINEER).agency(agency).build());

        // 6. 기사 프로필 (카테고리 설정 완료, LMS 이수 완료)
        EngineerProfile profile = EngineerProfile.createInitial(engineer);
        profile.completeProfile(category, 2020, SkillLevel.INTERMEDIATE, "성실한 기사입니다");
        profile.completeLms();
        engineerProfileRepository.save(profile);

        // 7. 기사 스케줄 + 시간 슬롯 (SCHEDULED_DATE 09:00~17:00 AVAILABLE)
        EngineerSchedule schedule = EngineerSchedule.builder()
                .user(engineer).workDate(SCHEDULED_DATE).status(ScheduleStatus.AVAILABLE).build();
        EngineerScheduleSlot slot = EngineerScheduleSlot.builder()
                .startTime(LocalTime.of(9, 0)).endTime(LocalTime.of(17, 0)).build();
        schedule.addTimeSlot(slot);
        scheduleRepository.save(schedule);

        // 8. 기사 전문 브랜드
        expertBrandRepository.save(EngineerExpertBrand.builder()
                .engineer(engineer).brandName("삼성").build());

        // 9. 기사 서비스 지역
        serviceRegionRepository.save(EngineerServiceRegion.builder()
                .engineer(engineer).region(region).build());

        // 10. 고객 소유 가전 (삼성 에어컨, category 일치)
        appliance = applianceRepository.save(Appliance.create(
                customer, category, "삼성", "에어컨 Q9000",
                null, null, null, RegisterMethod.MANUAL));

        // 11. 증상 마스터
        symptom = symptomRepository.save(Symptom.builder()
                .category(category).symptomCode("COOLING_FAIL").symptomName("냉방 불량").build());
    }

    // ─────────────────────────────────────────────────────────────
    //  POST /api/as-requests
    // ─────────────────────────────────────────────────────────────
    @Nested
    @DisplayName("POST /api/as-requests — A/S 접수 및 배정")
    class CreateAsRequest {

        @Test
        @DisplayName("AUTO 성공: 모든 조건(스케줄+브랜드+카테고리+지역) 일치 → 201, as_requests + as_assignments DB 저장 확인")
        void auto_fullConditions_success() throws Exception {
            String body = requestBody(appliance.getId(), symptom.getId(), region.getId(),
                    SCHEDULED_DATE.toString(), "10:00", "AUTO", null);

            String response = mockMvc.perform(post("/api/as-requests")
                            .header("Authorization", "Bearer " + customerToken)
                            .contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.requestId").isNumber())
                    .andExpect(jsonPath("$.assignmentId").isNumber())
                    // 모든 조건 충족(Fallback 0)으로 매칭 — 사유 문자열 확인
                    .andExpect(jsonPath("$.matchReason").value(
                            "스케줄, 브랜드, 카테고리, 서비스 지역 4가지 조건 모두 충족"))
                    .andReturn().getResponse().getContentAsString();

            // DB 검증: as_requests 저장 및 상태 ASSIGNED 전환
            assertThat(asRequestRepository.findAll()).hasSize(1);
            AsRequest saved = asRequestRepository.findAll().get(0);
            assertThat(saved.getStatus()).isEqualTo(AsStatus.ASSIGNED);
            assertThat(saved.getCustomer().getId()).isEqualTo(customer.getId());
            assertThat(saved.getSymptom().getId()).isEqualTo(symptom.getId());

            // DB 검증: as_assignments 저장 및 배정 기사 확인
            assertThat(asAssignmentRepository.findAll()).hasSize(1);
            assertThat(asAssignmentRepository.findAll().get(0).getEngineer().getId())
                    .isEqualTo(engineer.getId());
        }

        @Test
        @DisplayName("AUTO 성공 (Fallback 3): 스케줄 날짜 일치, 브랜드 불일치 가전 → Fallback 탐색 후 배정 완료")
        void auto_fallback_brandMismatch_success() throws Exception {
            // 브랜드가 "LG"인 가전 → 기사의 전문 브랜드("삼성")와 불일치
            // Fallback 1/2/3 단계를 거쳐 스케줄 조건만으로 기사 매칭
            Appliance lgAppliance = applianceRepository.save(
                    Appliance.create(customer, category, "LG", "에어컨 LX500",
                            null, null, null, RegisterMethod.MANUAL));

            String body = requestBody(lgAppliance.getId(), symptom.getId(), region.getId(),
                    SCHEDULED_DATE.toString(), "10:00", "AUTO", null);

            mockMvc.perform(post("/api/as-requests")
                            .header("Authorization", "Bearer " + customerToken)
                            .contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.assignmentId").isNumber())
                    // LG 브랜드 → Fallback 0 실패, 서비스 지역은 일치하므로 Fallback 1 으로 매칭
                    .andExpect(jsonPath("$.matchReason").value(
                            "스케줄, 카테고리, 서비스 지역 조건 충족 (브랜드 조건 완화 적용)"));
        }

        @Test
        @DisplayName("AUTO 실패: 요청 날짜에 가용 기사 없음 → 403 Forbidden")
        void auto_noEngineerAvailable_403() throws Exception {
            // 스케줄이 등록된 날짜(SCHEDULED_DATE)가 아닌 다른 날짜로 요청
            String body = requestBody(appliance.getId(), symptom.getId(), region.getId(),
                    "2026-08-01", "10:00", "AUTO", null);

            mockMvc.perform(post("/api/as-requests")
                            .header("Authorization", "Bearer " + customerToken)
                            .contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.success").value(false))
                    .andExpect(jsonPath("$.message").exists());

            // as_requests 는 저장됐지만 배정 실패 → as_assignments 없음
            assertThat(asAssignmentRepository.findAll()).isEmpty();
        }

        @Test
        @DisplayName("MANUAL 성공: 유효한 ENGINEER 지정 → 201, 지정 기사로 배정 확인")
        void manual_success() throws Exception {
            String body = requestBody(appliance.getId(), symptom.getId(), region.getId(),
                    SCHEDULED_DATE.toString(), "10:00", "MANUAL", engineer.getId());

            mockMvc.perform(post("/api/as-requests")
                            .header("Authorization", "Bearer " + customerToken)
                            .contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.requestId").isNumber())
                    .andExpect(jsonPath("$.assignmentId").isNumber())
                    // MANUAL 배차 — matchReason 없음(null → JSON 직렬화 시 필드 포함되지 않음)
                    .andExpect(jsonPath("$.matchReason").isEmpty());

            // 지정한 기사로 배정됐는지 확인
            assertThat(asAssignmentRepository.findAll().get(0).getEngineer().getId())
                    .isEqualTo(engineer.getId());
        }

        @Test
        @DisplayName("MANUAL 실패: preferredEngineerId 미입력 → 400 Bad Request")
        void manual_noPreferredEngineer_400() throws Exception {
            // preferredEngineerId 필드 없이 MANUAL 요청
            String body = """
                    {
                      "applianceId": %d,
                      "symptomId": %d,
                      "symptomDesc": "냉각 불량",
                      "visitRegionId": %d,
                      "visitAddressDetail": "강남구 테헤란로 123",
                      "scheduledDate": "%s",
                      "scheduledTime": "10:00",
                      "assignType": "MANUAL"
                    }
                    """.formatted(appliance.getId(), symptom.getId(), region.getId(),
                    SCHEDULED_DATE);

            mockMvc.perform(post("/api/as-requests")
                            .header("Authorization", "Bearer " + customerToken)
                            .contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.success").value(false));
        }

        @Test
        @DisplayName("MANUAL 실패: 지정 사용자가 ENGINEER 역할 아님 (CUSTOMER) → 400 Bad Request")
        void manual_notEngineerRole_400() throws Exception {
            // 다른 고객 계정을 preferredEngineerId 로 지정
            User anotherCustomer = userRepository.save(User.builder()
                    .email("another@test.com").passwordHash("hashed")
                    .name("다른고객").role(Role.CUSTOMER).build());

            String body = requestBody(appliance.getId(), symptom.getId(), region.getId(),
                    SCHEDULED_DATE.toString(), "10:00", "MANUAL", anotherCustomer.getId());

            mockMvc.perform(post("/api/as-requests")
                            .header("Authorization", "Bearer " + customerToken)
                            .contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.success").value(false))
                    .andExpect(jsonPath("$.message").value("선택한 사용자는 수리 기사가 아닙니다."));
        }

        @Test
        @DisplayName("실패: 존재하지 않는 symptomId → 400 Bad Request")
        void symptomNotFound_400() throws Exception {
            String body = requestBody(appliance.getId(), 99999L, region.getId(),
                    SCHEDULED_DATE.toString(), "10:00", "AUTO", null);

            mockMvc.perform(post("/api/as-requests")
                            .header("Authorization", "Bearer " + customerToken)
                            .contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.success").value(false))
                    .andExpect(jsonPath("$.message").value("존재하지 않는 증상 코드입니다."));
        }

        @Test
        @DisplayName("실패: 존재하지 않는 applianceId → 400 Bad Request")
        void applianceNotFound_400() throws Exception {
            String body = requestBody(99999L, symptom.getId(), region.getId(),
                    SCHEDULED_DATE.toString(), "10:00", "AUTO", null);

            mockMvc.perform(post("/api/as-requests")
                            .header("Authorization", "Bearer " + customerToken)
                            .contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.success").value(false))
                    .andExpect(jsonPath("$.message").value("존재하지 않는 가전제품입니다."));
        }

        @Test
        @DisplayName("실패: 필수 필드(visitAddressDetail) 누락 — Bean Validation → 400 Bad Request")
        void missingRequiredField_400() throws Exception {
            String body = """
                    {
                      "applianceId": %d,
                      "symptomId": %d,
                      "visitRegionId": %d,
                      "scheduledDate": "%s",
                      "scheduledTime": "10:00",
                      "assignType": "AUTO"
                    }
                    """.formatted(appliance.getId(), symptom.getId(), region.getId(), SCHEDULED_DATE);

            mockMvc.perform(post("/api/as-requests")
                            .header("Authorization", "Bearer " + customerToken)
                            .contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("실패: 인증 토큰 없음 → 401 Unauthorized")
        void noToken_401() throws Exception {
            String body = requestBody(appliance.getId(), symptom.getId(), region.getId(),
                    SCHEDULED_DATE.toString(), "10:00", "AUTO", null);

            mockMvc.perform(post("/api/as-requests")
                            .contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isUnauthorized());
        }
    }

    // ─────────────────────────────────────────────────────────────
    //  GET /api/as-requests/me
    // ─────────────────────────────────────────────────────────────
    @Nested
    @DisplayName("GET /api/as-requests/me — 내 A/S 목록 조회")
    class GetMyAsRequests {

        @Test
        @DisplayName("성공: 본인 요청 2건 조회 → 200, 목록 크기 및 requestId 확인")
        void getMyRequests_success() throws Exception {
            // 픽스처 내 이미 만들어진 기사의 스케줄을 활용해 A/S 요청 2건 생성
            AsRequest req1 = saveAsRequest(AsStatus.PENDING);
            AsRequest req2 = saveAsRequest(AsStatus.ASSIGNED);

            mockMvc.perform(get("/api/as-requests/me")
                            .header("Authorization", "Bearer " + customerToken))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.length()").value(2))
                    .andExpect(jsonPath("$[0].requestId").isNumber())
                    .andExpect(jsonPath("$[0].symptomCode").value("COOLING_FAIL"));
        }

        @Test
        @DisplayName("성공: 요청 없을 때 빈 배열 반환 → 200, []")
        void getMyRequests_empty_success() throws Exception {
            mockMvc.perform(get("/api/as-requests/me")
                            .header("Authorization", "Bearer " + customerToken))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.length()").value(0));
        }
    }

    // ─────────────────────────────────────────────────────────────
    //  PATCH /api/as-requests/{id}/cancel
    // ─────────────────────────────────────────────────────────────
    @Nested
    @DisplayName("PATCH /api/as-requests/{id}/cancel — A/S 취소")
    class CancelAsRequest {

        @Test
        @DisplayName("성공: PENDING 상태 취소 → 200, DB에서 status = CANCELLED 확인")
        void cancel_pending_success() throws Exception {
            AsRequest req = saveAsRequest(AsStatus.PENDING);

            mockMvc.perform(patch("/api/as-requests/" + req.getId() + "/cancel")
                            .header("Authorization", "Bearer " + customerToken)
                            .param("cancelReason", "단순 변심"))
                    .andExpect(status().isOk());

            // DB에서 상태 변경 확인
            AsRequest updated = asRequestRepository.findById(req.getId()).orElseThrow();
            assertThat(updated.getStatus()).isEqualTo(AsStatus.CANCELLED);
            assertThat(updated.getCancelReason()).isEqualTo("단순 변심");
        }

        @Test
        @DisplayName("실패: ASSIGNED 상태 취소 시도 → 403 Forbidden, DB 상태 변경 없음")
        void cancel_assigned_403() throws Exception {
            AsRequest req = saveAsRequest(AsStatus.ASSIGNED);

            mockMvc.perform(patch("/api/as-requests/" + req.getId() + "/cancel")
                            .header("Authorization", "Bearer " + customerToken))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.success").value(false))
                    .andExpect(jsonPath("$.message").value("기사 배정이 진행된 요청은 취소할 수 없습니다."));

            // DB 상태 변경 없음 확인
            assertThat(asRequestRepository.findById(req.getId()).orElseThrow().getStatus())
                    .isEqualTo(AsStatus.ASSIGNED);
        }

        @Test
        @DisplayName("실패: 타인의 A/S 요청 취소 시도 → 403 Forbidden")
        void cancel_notOwner_403() throws Exception {
            // 다른 고객이 만든 요청
            User otherCustomer = userRepository.save(User.builder()
                    .email("other@test.com").passwordHash("hashed")
                    .name("타인고객").role(Role.CUSTOMER).build());
            AsRequest otherReq = asRequestRepository.save(AsRequest.builder()
                    .customer(otherCustomer).appliance(appliance).symptom(symptom)
                    .visitRegion(region).visitAddressDetail("타인 주소")
                    .scheduledDate(SCHEDULED_DATE).scheduledTime("14:00").build());

            // 현재 로그인 고객(customer)이 타인의 요청을 취소 시도
            mockMvc.perform(patch("/api/as-requests/" + otherReq.getId() + "/cancel")
                            .header("Authorization", "Bearer " + customerToken))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.success").value(false));

            // DB 상태 변경 없음
            assertThat(asRequestRepository.findById(otherReq.getId()).orElseThrow().getStatus())
                    .isEqualTo(AsStatus.PENDING);
        }

        @Test
        @DisplayName("실패: 존재하지 않는 requestId → 400 Bad Request")
        void cancel_notFound_400() throws Exception {
            mockMvc.perform(patch("/api/as-requests/999999/cancel")
                            .header("Authorization", "Bearer " + customerToken))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.success").value(false))
                    .andExpect(jsonPath("$.message").value("존재하지 않는 A/S 요청입니다."));
        }
    }

    // ── 헬퍼 ──────────────────────────────────────────────────────

    /**
     * 요청 바디 JSON 생성 헬퍼
     * preferredEngineerId 는 null 이면 필드 자체를 제외
     */
    private String requestBody(Long applianceId, Long symptomId, Integer regionId,
                               String date, String time, String assignType,
                               Long preferredEngineerId) {
        String preferred = preferredEngineerId != null
                ? ",\"preferredEngineerId\": " + preferredEngineerId
                : "";
        return """
                {
                  "applianceId": %d,
                  "symptomId": %d,
                  "symptomDesc": "냉각 기능이 작동하지 않습니다",
                  "visitRegionId": %d,
                  "visitAddressDetail": "강남구 테헤란로 123",
                  "scheduledDate": "%s",
                  "scheduledTime": "%s",
                  "assignType": "%s"%s
                }
                """.formatted(applianceId, symptomId, regionId, date, time, assignType, preferred);
    }

    /**
     * 특정 상태의 AsRequest 를 직접 저장하는 헬퍼
     * ASSIGNED 상태가 필요하면 processAssignment() 로 전환 후 저장
     */
    private AsRequest saveAsRequest(AsStatus targetStatus) {
        AsRequest req = AsRequest.builder()
                .customer(customer).appliance(appliance).symptom(symptom)
                .visitRegion(region).visitAddressDetail("강남구 테헤란로 123")
                .scheduledDate(SCHEDULED_DATE).scheduledTime("10:00").build();

        if (targetStatus == AsStatus.ASSIGNED) {
            req.processAssignment(agency);
        } else if (targetStatus == AsStatus.CANCELLED) {
            req.cancel("테스트 취소");
        }
        // PENDING 은 빌더 기본값이므로 별도 처리 불필요

        return asRequestRepository.save(req);
    }
}
