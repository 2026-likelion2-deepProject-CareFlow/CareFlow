package com.careflow.assignment.controller;

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

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("local")
@Sql(scripts = "/assignment_cleanup.sql", executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
@DisplayName("AgenciesAssignmentController 통합 테스트 (H2)")
class AgenciesAssignmentControllerIntegrationTest {

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

    // ── 공통 픽스처 ──
    private Agencies agency;
    private User agencyManager;   // role=AGENCY, agency 소속
    private User engineer;        // role=ENGINEER, agency 소속
    private User customer;        // role=CUSTOMER, A/S 신청자
    private Appliance appliance;
    private Symptom symptom;
    private Regions region;

    private String agencyToken;     // AGENCY 관리자 JWT
    private String customerToken;   // CUSTOMER JWT (권한 없음 케이스에 사용)

    @BeforeEach
    void setUp() {
        // 1. 지역
        region = regionRepository.save(Regions.create("서울특별시 강남구", null, 1, 0));

        // 2. 대행사 (APPROVED)
        agency = agenciesRepository.save(Agencies.builder()
                .agencyName("테스트대행사").businessNumber("TEST-BIZ-001")
                .agencyAddress("서울특별시 강남구").agencyFeeRate(5.0)
                .approvalStatus(AgencyStatus.APPROVED).build());

        // 3. 대행사 관리자 계정 (role=AGENCY, 대행사 소속)
        agencyManager = userRepository.save(User.builder()
                .email("manager@agency.com").passwordHash("hashed")
                .name("대행사관리자").phone("010-0000-0001")
                .role(Role.AGENCY).agency(agency).build());
        agencyToken = jwtProvider.generateAccessToken(
                agencyManager.getId(), agencyManager.getEmail(), "AGENCY");

        // 4. 수리 기사 계정 (같은 대행사 소속)
        engineer = userRepository.save(User.builder()
                .email("engineer@agency.com").passwordHash("hashed")
                .name("테스트기사").phone("010-0000-0002")
                .role(Role.ENGINEER).agency(agency).build());

        // 5. 고객 계정 + JWT (권한 없음 케이스 검증용)
        customer = userRepository.save(User.builder()
                .email("customer@test.com").passwordHash("hashed")
                .name("테스트고객").phone("010-0000-0003")
                .role(Role.CUSTOMER).build());
        customerToken = jwtProvider.generateAccessToken(
                customer.getId(), customer.getEmail(), "CUSTOMER");

        // 6. 가전 카테고리 + 고객 소유 가전 (AsRequest 생성에 필요)
        ApplianceCategory rootCat = categoryRepository.save(ApplianceCategory.createRoot("에어컨", 1));
        ApplianceCategory category = categoryRepository.save(ApplianceCategory.createChild("에어컨 소분류", rootCat, 1));
        appliance = applianceRepository.save(Appliance.create(
                customer, category, "삼성", "에어컨 Q9000",
                null, null, null, RegisterMethod.MANUAL));

        // 7. 증상 마스터
        symptom = symptomRepository.save(Symptom.builder()
                .category(category).symptomCode("COOLING_FAIL").symptomName("냉방 불량").build());
    }

    // ─────────────────────────────────────────────────────────────
    //  GET /api/agencies/assignment
    // ─────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("GET /api/agencies/assignment — 대행사 배차 내역 조회")
    class GetAgenciesAssignment {

        @Test
        @DisplayName("성공: 배차 내역 2건 존재 → 200 OK, 리스트 크기 및 필드값 DB 검증")
        void getAssignment_withData_200() throws Exception {
            // 배차 내역 2건 저장
            AsRequest req1 = saveAsRequest();
            AsRequest req2 = saveAsRequest();
            AsAssignment a1 = asAssignmentRepository.save(
                    AsAssignment.create(req1, engineer, agency, AssignType.MANUAL));
            AsAssignment a2 = asAssignmentRepository.save(
                    AsAssignment.create(req2, engineer, agency, AssignType.AUTO));

            mockMvc.perform(get("/api/agencies/assignment")
                            .header("Authorization", "Bearer " + agencyToken))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.length()").value(2))
                    .andExpect(jsonPath("$[0].agencyId").value(agency.getId()))
                    .andExpect(jsonPath("$[0].engineerId").value(engineer.getId()))
                    .andExpect(jsonPath("$[0].engineerName").value("테스트기사"))
                    .andExpect(jsonPath("$[0].status").value("WAITING"));

            // DB 직접 검증 — as_assignments 테이블에 2건 저장됐는지 확인
            assertThat(asAssignmentRepository.findByAgency_Id(agency.getId())).hasSize(2);
        }

        @Test
        @DisplayName("성공: 배차 내역 없음 → 204 No Content")
        void getAssignment_noData_204() throws Exception {
            // as_assignments 에 아무 데이터도 없는 상태
            mockMvc.perform(get("/api/agencies/assignment")
                            .header("Authorization", "Bearer " + agencyToken))
                    .andExpect(status().isNoContent());
        }

        @Test
        @DisplayName("성공: 다른 대행사 배차 내역은 포함되지 않음 → 본 대행사 건수만 반환")
        void getAssignment_otherAgencyFiltered_200() throws Exception {
            // 다른 대행사 + 소속 기사 생성
            Agencies otherAgency = agenciesRepository.save(Agencies.builder()
                    .agencyName("다른대행사").businessNumber("OTHER-BIZ-001")
                    .agencyAddress("서울특별시 서초구").agencyFeeRate(4.0)
                    .approvalStatus(AgencyStatus.APPROVED).build());
            User otherEngineer = userRepository.save(User.builder()
                    .email("other_eng@agency.com").passwordHash("hashed")
                    .name("다른기사").phone("010-9999-8888")
                    .role(Role.ENGINEER).agency(otherAgency).build());

            // 현재 대행사 배차 1건, 다른 대행사 배차 1건 저장
            AsRequest req1 = saveAsRequest();
            AsRequest req2 = saveAsRequest();
            asAssignmentRepository.save(AsAssignment.create(req1, engineer, agency, AssignType.MANUAL));
            asAssignmentRepository.save(AsAssignment.create(req2, otherEngineer, otherAgency, AssignType.MANUAL));

            // 현재 로그인한 관리자는 agency 소속 → 본 대행사 1건만 반환
            mockMvc.perform(get("/api/agencies/assignment")
                            .header("Authorization", "Bearer " + agencyToken))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.length()").value(1))
                    .andExpect(jsonPath("$[0].agencyId").value(agency.getId()));
        }

        @Test
        @DisplayName("실패: CUSTOMER 권한 토큰으로 요청 → 401 Unauthorized")
        void getAssignment_customerRole_401() throws Exception {
            mockMvc.perform(get("/api/agencies/assignment")
                            .header("Authorization", "Bearer " + customerToken))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.success").value(false))
                    .andExpect(jsonPath("$.message").exists());
        }

        @Test
        @DisplayName("실패: 인증 토큰 없이 요청 → 401 Unauthorized")
        void getAssignment_noToken_401() throws Exception {
            mockMvc.perform(get("/api/agencies/assignment"))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("실패: 소속 대행사가 없는 AGENCY 사용자 → 403 Forbidden")
        void getAssignment_noAgency_403() throws Exception {
            // agency = null 인 AGENCY role 사용자 생성 후 토큰 발급
            User noAgencyManager = userRepository.save(User.builder()
                    .email("no_agency@test.com").passwordHash("hashed")
                    .name("대행사없는관리자").phone("010-0000-9999")
                    .role(Role.AGENCY).build()); // agency 미설정
            String noAgencyToken = jwtProvider.generateAccessToken(
                    noAgencyManager.getId(), noAgencyManager.getEmail(), "AGENCY");

            mockMvc.perform(get("/api/agencies/assignment")
                            .header("Authorization", "Bearer " + noAgencyToken))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.success").value(false))
                    .andExpect(jsonPath("$.message").value("소속 대행사 정보가 없습니다."));
        }
    }

    // ── 헬퍼 ──────────────────────────────────────────────────────

    /** AsRequest 를 PENDING 상태로 저장하는 헬퍼 */
    private AsRequest saveAsRequest() {
        return asRequestRepository.save(AsRequest.builder()
                .customer(customer).appliance(appliance).symptom(symptom)
                .visitRegion(region).visitAddressDetail("강남구 테헤란로 123")
                .scheduledDate(LocalDate.of(2026, 7, 1)).scheduledTime("10:00")
                .build());
    }
}
