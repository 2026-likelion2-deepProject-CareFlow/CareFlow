package com.careflow.agency.controller;

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
import com.careflow.common.enums.RegisterMethod;
import com.careflow.common.enums.Role;
import com.careflow.engineer.domain.entity.EngineerProfile;
import com.careflow.common.enums.SkillLevel;
import com.careflow.engineer.domain.entity.EngineerSchedule;
import com.careflow.common.enums.ScheduleStatus;
import com.careflow.engineer.repository.EngineerProfileRepository;
import com.careflow.engineer.repository.EngineerScheduleRepository;
import com.careflow.lms.entity.LmsConfirmation;
import com.careflow.lms.entity.LmsContent;
import com.careflow.lms.repository.LmsConfirmationRepository;
import com.careflow.lms.repository.LmsContentRepository;
import com.careflow.payment.entity.Payment;
import com.careflow.payment.repository.PaymentRepository;
import com.careflow.region.entity.Regions;
import com.careflow.region.repository.RegionRepository;
import com.careflow.review.entity.Review;
import com.careflow.review.repository.ReviewRepository;
import com.careflow.settlement.entity.Settlement;
import com.careflow.settlement.repository.SettlementRepository;
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

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * 대행사/기사 추가 API 통합 테스트 (H2 인메모리 DB)
 *
 * 테스트 대상 엔드포인트:
 * - GET /api/agency/engineers/recommended
 * - GET /api/agency/engineers/realtime-status
 * - GET /api/agency/engineers/{engineerUserId}/settlements
 * - GET /api/agency/engineers/{engineerUserId}/lms
 * - GET /api/agency/engineers/{engineerUserId}/reviews
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("local")
@Sql(scripts = "/engineer_additional_cleanup.sql", executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
@DisplayName("AgencyEngineer 추가 API 통합 테스트 (H2)")
class AgencyEngineerAdditionalIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private JwtProvider jwtProvider;

    @Autowired private UserRepository userRepository;
    @Autowired private AgenciesRepository agenciesRepository;
    @Autowired private RegionRepository regionRepository;
    @Autowired private ApplianceCategoryRepository categoryRepository;
    @Autowired private ApplianceRepository applianceRepository;
    @Autowired private SymptomRepository symptomRepository;
    @Autowired private AsRequestRepository asRequestRepository;
    @Autowired private EngineerProfileRepository engineerProfileRepository;
    @Autowired private EngineerScheduleRepository engineerScheduleRepository;
    @Autowired private SettlementRepository settlementRepository;
    @Autowired private PaymentRepository paymentRepository;
    @Autowired private ReviewRepository reviewRepository;
    @Autowired private LmsContentRepository lmsContentRepository;
    @Autowired private LmsConfirmationRepository lmsConfirmationRepository;

    // ── 공통 픽스처 ──
    private Agencies agency;
    private User agencyManager;
    private User engineer;
    private User customer;
    private Regions region;
    private ApplianceCategory category;
    private Appliance appliance;
    private Symptom symptom;
    private EngineerProfile engineerProfile;
    private String agencyToken;

    @BeforeEach
    void setUp() {
        region = regionRepository.save(Regions.create("강남구", null, 2, 0));

        ApplianceCategory rootCat = categoryRepository.save(ApplianceCategory.createRoot("냉장고", 1));
        category = categoryRepository.save(ApplianceCategory.createChild("냉장고 소분류", rootCat, 1));

        agency = agenciesRepository.save(Agencies.builder()
                .agencyName("테스트대행사").businessNumber("BIZ-INT-001")
                .agencyAddress("서울 강남구").agencyFeeRate(5.0)
                .approvalStatus(AgencyStatus.APPROVED).build());

        agencyManager = userRepository.save(User.builder()
                .email("manager@addtest.com").passwordHash("hashed")
                .name("대행사관리자").phone("010-1111-2222").role(Role.AGENCY).agency(agency).build());

        agencyToken = jwtProvider.generateAccessToken(
                agencyManager.getId(), agencyManager.getEmail(), "AGENCY", agency.getId());

        customer = userRepository.save(User.builder()
                .email("customer@addtest.com").passwordHash("hashed")
                .name("홍길동").phone("010-3333-4444").role(Role.CUSTOMER).build());

        engineer = userRepository.save(User.builder()
                .email("engineer@addtest.com").passwordHash("hashed")
                .name("테스트기사").phone("010-5555-6666").role(Role.ENGINEER).agency(agency).build());

        // 기사 프로필 생성 + LMS 이수 완료 처리
        engineerProfile = EngineerProfile.createInitial(engineer);
        engineerProfile.completeProfile(category, 2020, SkillLevel.INTERMEDIATE, "소개글");
        engineerProfile.completeLms();
        engineerProfileRepository.save(engineerProfile);

        appliance = applianceRepository.save(Appliance.create(
                customer, category, "삼성", "비스포크 냉장고",
                null, null, null, RegisterMethod.MANUAL));

        symptom = symptomRepository.save(Symptom.builder()
                .category(category).symptomCode("COOL_FAIL").symptomName("냉방 불량").build());
    }

    // ─────────────────────────────────────────────────────────────────
    //  1. GET /api/agency/engineers/recommended?requestId=
    // ─────────────────────────────────────────────────────────────────
    @Nested
    @DisplayName("GET /api/agency/engineers/recommended — 추천 기사 목록 조회")
    class GetRecommendedEngineers {

        @Test
        @DisplayName("성공: LMS 이수 완료 기사 반환 — 200 OK")
        void success_lmsCompleted_returns200() throws Exception {
            // 해당 날짜 AVAILABLE 근무표 등록 (필터 통과 조건)
            engineerScheduleRepository.save(EngineerSchedule.builder()
                    .user(engineer).workDate(LocalDate.of(2026, 7, 1))
                    .status(ScheduleStatus.AVAILABLE).build());

            AsRequest request = saveRequest(LocalDate.of(2026, 7, 1));

            mockMvc.perform(get("/api/agency/engineers/recommended")
                            .param("requestId", request.getId().toString())
                            .header("Authorization", "Bearer " + agencyToken))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.length()").value(1))
                    .andExpect(jsonPath("$[0].name").value("테스트기사"))
                    .andExpect(jsonPath("$[0].isLmsCompleted").value(true));
        }

        @Test
        @DisplayName("실패: 존재하지 않는 requestId — 404 Not Found")
        void fail_requestNotFound_404() throws Exception {
            mockMvc.perform(get("/api/agency/engineers/recommended")
                            .param("requestId", "99999")
                            .header("Authorization", "Bearer " + agencyToken))
                    .andExpect(status().isNotFound());
        }

        @Test
        @DisplayName("실패: 인증 없음 — 401 Unauthorized")
        void fail_noToken_401() throws Exception {
            mockMvc.perform(get("/api/agency/engineers/recommended")
                            .param("requestId", "1"))
                    .andExpect(status().isUnauthorized());
        }
    }

    // ─────────────────────────────────────────────────────────────────
    //  2. GET /api/agency/engineers/realtime-status
    // ─────────────────────────────────────────────────────────────────
    @Nested
    @DisplayName("GET /api/agency/engineers/realtime-status — 실시간 배정 현황")
    class GetEngineersRealtimeStatus {

        @Test
        @DisplayName("성공: 소속 기사 1명 배정 없음 — 200 OK, asStatus null")
        void success_noActiveAssignment_200() throws Exception {
            mockMvc.perform(get("/api/agency/engineers/realtime-status")
                            .header("Authorization", "Bearer " + agencyToken))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.length()").value(1))
                    .andExpect(jsonPath("$[0].name").value("테스트기사"))
                    .andExpect(jsonPath("$[0].asStatus").doesNotExist());
        }

        @Test
        @DisplayName("실패: 인증 없음 — 401 Unauthorized")
        void fail_noToken_401() throws Exception {
            mockMvc.perform(get("/api/agency/engineers/realtime-status"))
                    .andExpect(status().isUnauthorized());
        }
    }

    // ─────────────────────────────────────────────────────────────────
    //  3. GET /api/agency/engineers/{engineerUserId}/settlements
    // ─────────────────────────────────────────────────────────────────
    @Nested
    @DisplayName("GET /api/agency/engineers/{engineerUserId}/settlements — 기사 정산 내역 조회")
    class GetEngineerSettlements {

        @Test
        @DisplayName("성공: 정산 1건 반환 — 200 OK, grossAmount 검증")
        void success_oneSettlement_200() throws Exception {
            AsRequest request = saveRequest(LocalDate.of(2026, 7, 1));
            Payment payment = paymentRepository.save(Payment.create(request, customer, 150000));
            settlementRepository.save(Settlement.create(
                    payment, request, engineer, agency,
                    150000, 15000, BigDecimal.valueOf(10.0),
                    7500, BigDecimal.valueOf(5.0), 127500));

            mockMvc.perform(get("/api/agency/engineers/{engineerUserId}/settlements", engineer.getId())
                            .header("Authorization", "Bearer " + agencyToken))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.length()").value(1))
                    .andExpect(jsonPath("$[0].grossAmount").value(150000))
                    .andExpect(jsonPath("$[0].engineerNetAmount").value(127500))
                    .andExpect(jsonPath("$[0].status").value("PENDING"));
        }

        @Test
        @DisplayName("성공: 정산 없으면 빈 배열 반환 — 200 OK")
        void success_noSettlement_emptyArray() throws Exception {
            mockMvc.perform(get("/api/agency/engineers/{engineerUserId}/settlements", engineer.getId())
                            .header("Authorization", "Bearer " + agencyToken))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.length()").value(0));
        }

        @Test
        @DisplayName("실패: 타 대행사 소속 기사 조회 — 401 Unauthorized")
        void fail_otherAgencyEngineer_401() throws Exception {
            // 다른 대행사 및 기사 생성
            Agencies otherAgency = agenciesRepository.save(Agencies.builder()
                    .agencyName("타대행사").businessNumber("BIZ-INT-002")
                    .agencyAddress("서울 서초구").agencyFeeRate(3.0)
                    .approvalStatus(AgencyStatus.APPROVED).build());

            User otherEngineer = userRepository.save(User.builder()
                    .email("other@addtest.com").passwordHash("hashed")
                    .name("타기사").phone("010-9999-0000").role(Role.ENGINEER).agency(otherAgency).build());

            EngineerProfile otherProfile = EngineerProfile.createInitial(otherEngineer);
            otherProfile.completeProfile(category, 2020, SkillLevel.BEGINNER, "소개");
            engineerProfileRepository.save(otherProfile);

            mockMvc.perform(get("/api/agency/engineers/{engineerUserId}/settlements", otherEngineer.getId())
                            .header("Authorization", "Bearer " + agencyToken))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("실패: 존재하지 않는 기사 — 404 Not Found")
        void fail_engineerNotFound_404() throws Exception {
            mockMvc.perform(get("/api/agency/engineers/99999/settlements")
                            .header("Authorization", "Bearer " + agencyToken))
                    .andExpect(status().isNotFound());
        }

        @Test
        @DisplayName("실패: 인증 없음 — 401 Unauthorized")
        void fail_noToken_401() throws Exception {
            mockMvc.perform(get("/api/agency/engineers/{engineerUserId}/settlements", engineer.getId()))
                    .andExpect(status().isUnauthorized());
        }
    }

    // ─────────────────────────────────────────────────────────────────
    //  4. GET /api/agency/engineers/{engineerUserId}/lms
    // ─────────────────────────────────────────────────────────────────
    @Nested
    @DisplayName("GET /api/agency/engineers/{engineerUserId}/lms — 기사 LMS 이수 현황")
    class GetEngineerLmsStatus {

        @Test
        @DisplayName("성공: LMS 이수 완료 + 이수 이력 1건 — 200 OK")
        void success_lmsCompleted_withConfirmation() throws Exception {
            // LMS 콘텐츠 및 이수 이력 생성
            LmsContent content = lmsContentRepository.save(LmsContent.builder()
                    .category(category)
                    .title("냉장고 수리 기초")
                    .body("냉장고 수리에 대한 내용입니다.")
                    .requiredLevel(LmsContent.RequiredLevel.INTERMEDIATE)
                    .contentType(LmsContent.ContentType.TEXT)
                    .isActive(true)
                    .version("1.0")
                    .createdBy(agencyManager) // NOT NULL 필드
                    .build());

            lmsConfirmationRepository.save(LmsConfirmation.of(engineer, content, 2026));

            mockMvc.perform(get("/api/agency/engineers/{engineerUserId}/lms", engineer.getId())
                            .header("Authorization", "Bearer " + agencyToken))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.isLmsCompleted").value(true))
                    .andExpect(jsonPath("$.currentYear").value(2026))
                    .andExpect(jsonPath("$.confirmations.length()").value(1))
                    .andExpect(jsonPath("$.confirmations[0].title").value("냉장고 수리 기초"));
        }

        @Test
        @DisplayName("성공: 이수 이력 없음 — 빈 confirmations 배열")
        void success_noConfirmation_emptyList() throws Exception {
            // EngineerProfile의 isLmsCompleted를 false로 재설정
            EngineerProfile notCompletedProfile = EngineerProfile.createInitial(
                    userRepository.save(User.builder()
                            .email("notcomplete@addtest.com").passwordHash("hashed")
                            .name("미이수기사").phone("010-7777-8888").role(Role.ENGINEER).agency(agency).build()));
            notCompletedProfile.completeProfile(category, 2021, SkillLevel.BEGINNER, "소개");
            engineerProfileRepository.save(notCompletedProfile);

            User notCompleteEngineer = notCompletedProfile.getUser();

            mockMvc.perform(get("/api/agency/engineers/{engineerUserId}/lms", notCompleteEngineer.getId())
                            .header("Authorization", "Bearer " + agencyToken))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.isLmsCompleted").value(false))
                    .andExpect(jsonPath("$.confirmations.length()").value(0));
        }

        @Test
        @DisplayName("실패: 타 대행사 소속 기사 조회 — 401 Unauthorized")
        void fail_otherAgencyEngineer_401() throws Exception {
            Agencies otherAgency = agenciesRepository.save(Agencies.builder()
                    .agencyName("타대행사LMS").businessNumber("BIZ-INT-003")
                    .agencyAddress("서울 마포구").agencyFeeRate(4.0)
                    .approvalStatus(AgencyStatus.APPROVED).build());

            User otherEngineer = userRepository.save(User.builder()
                    .email("otherlms@addtest.com").passwordHash("hashed")
                    .name("타기사LMS").phone("010-8888-9999").role(Role.ENGINEER).agency(otherAgency).build());

            EngineerProfile otherProfile = EngineerProfile.createInitial(otherEngineer);
            otherProfile.completeProfile(category, 2020, SkillLevel.BEGINNER, "소개");
            engineerProfileRepository.save(otherProfile);

            mockMvc.perform(get("/api/agency/engineers/{engineerUserId}/lms", otherEngineer.getId())
                            .header("Authorization", "Bearer " + agencyToken))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("실패: 인증 없음 — 401 Unauthorized")
        void fail_noToken_401() throws Exception {
            mockMvc.perform(get("/api/agency/engineers/{engineerUserId}/lms", engineer.getId()))
                    .andExpect(status().isUnauthorized());
        }
    }

    // ─────────────────────────────────────────────────────────────────
    //  5. GET /api/agency/engineers/{engineerUserId}/reviews
    // ─────────────────────────────────────────────────────────────────
    @Nested
    @DisplayName("GET /api/agency/engineers/{engineerUserId}/reviews — 기사 수신 리뷰 목록")
    class GetEngineerReviews {

        @Test
        @DisplayName("성공: 공개 리뷰 1건 반환 — 200 OK, rating 검증")
        void success_oneVisibleReview_200() throws Exception {
            AsRequest request = saveRequest(LocalDate.of(2026, 7, 1));
            reviewRepository.save(Review.create(request, customer, engineer, 5, "정말 친절했어요!"));

            mockMvc.perform(get("/api/agency/engineers/{engineerUserId}/reviews", engineer.getId())
                            .header("Authorization", "Bearer " + agencyToken))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.totalReviews").value(1))
                    .andExpect(jsonPath("$.avgRating").value(5.0))
                    .andExpect(jsonPath("$.reviews[0].customerName").value("홍길동"))
                    .andExpect(jsonPath("$.reviews[0].rating").value(5))
                    .andExpect(jsonPath("$.reviews[0].content").value("정말 친절했어요!"));
        }

        @Test
        @DisplayName("성공: 리뷰 없으면 빈 배열 반환 — 200 OK")
        void success_noReview_emptyArray() throws Exception {
            mockMvc.perform(get("/api/agency/engineers/{engineerUserId}/reviews", engineer.getId())
                            .header("Authorization", "Bearer " + agencyToken))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.totalReviews").value(0))
                    .andExpect(jsonPath("$.reviews.length()").value(0));
        }

        @Test
        @DisplayName("실패: 타 대행사 소속 기사 조회 — 401 Unauthorized")
        void fail_otherAgencyEngineer_401() throws Exception {
            Agencies otherAgency = agenciesRepository.save(Agencies.builder()
                    .agencyName("타대행사리뷰").businessNumber("BIZ-INT-004")
                    .agencyAddress("서울 종로구").agencyFeeRate(4.5)
                    .approvalStatus(AgencyStatus.APPROVED).build());

            User otherEngineer = userRepository.save(User.builder()
                    .email("otherreview@addtest.com").passwordHash("hashed")
                    .name("타기사리뷰").phone("010-6666-7777").role(Role.ENGINEER).agency(otherAgency).build());

            EngineerProfile otherProfile = EngineerProfile.createInitial(otherEngineer);
            otherProfile.completeProfile(category, 2020, SkillLevel.BEGINNER, "소개");
            engineerProfileRepository.save(otherProfile);

            mockMvc.perform(get("/api/agency/engineers/{engineerUserId}/reviews", otherEngineer.getId())
                            .header("Authorization", "Bearer " + agencyToken))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("실패: 존재하지 않는 기사 — 404 Not Found")
        void fail_engineerNotFound_404() throws Exception {
            mockMvc.perform(get("/api/agency/engineers/99999/reviews")
                            .header("Authorization", "Bearer " + agencyToken))
                    .andExpect(status().isNotFound());
        }

        @Test
        @DisplayName("실패: 인증 없음 — 401 Unauthorized")
        void fail_noToken_401() throws Exception {
            mockMvc.perform(get("/api/agency/engineers/{engineerUserId}/reviews", engineer.getId()))
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
}
