package com.careflow.user.controller;

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
import com.careflow.engineer.domain.entity.*;
import com.careflow.common.enums.ScheduleStatus;
import com.careflow.common.enums.SkillLevel;
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
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.time.LocalTime;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("local")
@Sql(scripts = "/as_request_cleanup.sql", executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
@DisplayName("CustomerController - 수동 배정 기사 조회 통합 테스트 (H2)")
class CustomerEngineerIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private JwtProvider jwtProvider;

    @Autowired private UserRepository userRepository;
    @Autowired private AgenciesRepository agenciesRepository;
    @Autowired private RegionRepository regionRepository;
    @Autowired private ApplianceCategoryRepository categoryRepository;
    @Autowired private EngineerProfileRepository engineerProfileRepository;
    @Autowired private EngineerScheduleRepository scheduleRepository;
    @Autowired private EngineerExpertBrandRepository expertBrandRepository;
    @Autowired private EngineerServiceRegionRepository serviceRegionRepository;
    @Autowired private ApplianceRepository applianceRepository;
    @Autowired private SymptomRepository symptomRepository;
    @Autowired private AsRequestRepository asRequestRepository;
    @Autowired private AsAssignmentRepository asAssignmentRepository;

    private User customer;
    private User engineer;
    private Agencies agency;
    private Regions region;
    private Regions otherRegion;
    private ApplianceCategory category;
    private String customerToken;

    private static final LocalDate WORK_DATE = LocalDate.of(2026, 7, 1);

    @BeforeEach
    void setUp() {
        region = regionRepository.save(Regions.create("서울특별시 강남구", null, 1, 0));
        otherRegion = regionRepository.save(Regions.create("부산광역시 해운대구", null, 1, 0));

        ApplianceCategory rootCat = categoryRepository.save(ApplianceCategory.createRoot("냉장고", 1));
        category = categoryRepository.save(ApplianceCategory.createChild("냉장고 소분류", rootCat, 1));

        agency = agenciesRepository.save(Agencies.builder()
                .agencyName("테스트대행사").businessNumber("TEST-BIZ-002")
                .agencyAddress("서울특별시 강남구").agencyFeeRate(5.0)
                .approvalStatus(AgencyStatus.APPROVED).build());

        customer = userRepository.save(User.builder()
                .email("customer2@test.com").passwordHash("hashed")
                .name("테스트고객").phone("010-1111-3333").role(Role.CUSTOMER).build());
        customerToken = jwtProvider.generateAccessToken(
                customer.getId(), customer.getEmail(), "CUSTOMER", null);

        engineer = userRepository.save(User.builder()
                .email("engineer2@test.com").passwordHash("hashed")
                .name("김민수").phone("010-3333-5555").role(Role.ENGINEER).agency(agency).build());

        EngineerProfile profile = EngineerProfile.createInitial(engineer);
        profile.completeProfile(category, 2020, SkillLevel.INTERMEDIATE, "성실한 기사입니다");
        profile.completeLms();
        engineerProfileRepository.save(profile);

        EngineerSchedule schedule = EngineerSchedule.builder()
                .user(engineer).workDate(WORK_DATE).status(ScheduleStatus.AVAILABLE).build();
        EngineerScheduleSlot slot = EngineerScheduleSlot.builder()
                .startTime(LocalTime.of(9, 0)).endTime(LocalTime.of(11, 0)).build();
        schedule.addTimeSlot(slot);
        scheduleRepository.save(schedule);

        expertBrandRepository.save(EngineerExpertBrand.builder()
                .engineer(engineer).brandName("LG").build());

        serviceRegionRepository.save(EngineerServiceRegion.builder()
                .engineer(engineer).region(region).build());
    }

    @Nested
    @DisplayName("GET /api/customers/{customerId}/engineers/available — 후보 기사 목록 조회")
    class GetAvailableEngineers {

        @Test
        @DisplayName("성공: 지역만으로 조회 — 등록된 기사 1명 반환")
        void success_regionOnly() throws Exception {
            mockMvc.perform(get("/api/customers/{customerId}/engineers/available", customer.getId())
                            .header("Authorization", "Bearer " + customerToken)
                            .param("regionId", String.valueOf(region.getId())))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.length()").value(1))
                    .andExpect(jsonPath("$[0].engineerId").value(engineer.getId()))
                    .andExpect(jsonPath("$[0].name").value("김민수"))
                    .andExpect(jsonPath("$[0].brands[0]").value("LG"))
                    .andExpect(jsonPath("$[0].skills").value("냉장고 소분류"));
        }

        @Test
        @DisplayName("성공: 브랜드/기술 필터 일치 — 기사 반환")
        void success_brandAndSkillFilter() throws Exception {
            mockMvc.perform(get("/api/customers/{customerId}/engineers/available", customer.getId())
                            .header("Authorization", "Bearer " + customerToken)
                            .param("regionId", String.valueOf(region.getId()))
                            .param("brand", "LG")
                            .param("skill", "냉장고 소분류"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.length()").value(1));
        }

        @Test
        @DisplayName("성공: 브랜드 필터 불일치 — 빈 배열 반환")
        void success_brandFilterMismatch_empty() throws Exception {
            mockMvc.perform(get("/api/customers/{customerId}/engineers/available", customer.getId())
                            .header("Authorization", "Bearer " + customerToken)
                            .param("regionId", String.valueOf(region.getId()))
                            .param("brand", "삼성"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.length()").value(0));
        }

        @Test
        @DisplayName("성공: 서비스 지역이 다른 지역 — 빈 배열 반환")
        void success_otherRegion_empty() throws Exception {
            mockMvc.perform(get("/api/customers/{customerId}/engineers/available", customer.getId())
                            .header("Authorization", "Bearer " + customerToken)
                            .param("regionId", String.valueOf(otherRegion.getId())))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.length()").value(0));
        }

        @Test
        @DisplayName("실패: 존재하지 않는 지역 — 404 Not Found")
        void fail_regionNotFound() throws Exception {
            mockMvc.perform(get("/api/customers/{customerId}/engineers/available", customer.getId())
                            .header("Authorization", "Bearer " + customerToken)
                            .param("regionId", "999999"))
                    .andExpect(status().isNotFound());
        }

        @Test
        @DisplayName("실패: 인증 토큰 없음 — 401 Unauthorized")
        void fail_noToken() throws Exception {
            mockMvc.perform(get("/api/customers/{customerId}/engineers/available", customer.getId())
                            .param("regionId", String.valueOf(region.getId())))
                    .andExpect(status().isUnauthorized());
        }
    }

    @Nested
    @DisplayName("GET /api/customers/{customerId}/engineers/{engineerId}/availability — 가능 일정 조회")
    class GetEngineerAvailability {

        @Test
        @DisplayName("성공: 등록된 근무표 범위 내 조회 — 가능 날짜/시간 반환")
        void success_returnsAvailability() throws Exception {
            mockMvc.perform(get("/api/customers/{customerId}/engineers/{engineerId}/availability",
                            customer.getId(), engineer.getId())
                            .header("Authorization", "Bearer " + customerToken)
                            .param("from", WORK_DATE.toString())
                            .param("to", WORK_DATE.toString()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.engineerId").value(engineer.getId()))
                    .andExpect(jsonPath("$.availableDates['" + WORK_DATE + "'][0]").value("09:00"));
        }

        @Test
        @DisplayName("성공: 조회 범위 밖 — 빈 가능일정 반환")
        void success_outOfRange_empty() throws Exception {
            mockMvc.perform(get("/api/customers/{customerId}/engineers/{engineerId}/availability",
                            customer.getId(), engineer.getId())
                            .header("Authorization", "Bearer " + customerToken)
                            .param("from", "2026-08-01")
                            .param("to", "2026-08-07"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.availableDates.length()").value(0));
        }

        @Test
        @DisplayName("성공: 슬롯 중 하나에 이미 배정이 있어도 같은 날 다른 슬롯은 그대로 노출된다 (근무표 하루 단위 status로 전체를 막지 않음)")
        void success_bookedSlotExcluded_otherSlotsStillShown() throws Exception {
            LocalDate conflictDate = WORK_DATE.plusDays(1);

            // 이 날짜에 09~11, 11~13 두 개 슬롯을 가진 근무표 등록
            EngineerSchedule schedule = EngineerSchedule.builder()
                    .user(engineer).workDate(conflictDate).status(ScheduleStatus.AVAILABLE).build();
            schedule.addTimeSlot(EngineerScheduleSlot.builder()
                    .startTime(LocalTime.of(9, 0)).endTime(LocalTime.of(11, 0)).build());
            schedule.addTimeSlot(EngineerScheduleSlot.builder()
                    .startTime(LocalTime.of(11, 0)).endTime(LocalTime.of(13, 0)).build());
            scheduleRepository.save(schedule);

            // 09:00 슬롯에는 이미 활성 배정이 있는 상태로 픽스처 구성
            ApplianceCategory rootCat = categoryRepository.save(ApplianceCategory.createRoot("세탁기", 1));
            ApplianceCategory bookedCategory = categoryRepository.save(ApplianceCategory.createChild("세탁기 소분류", rootCat, 1));
            Appliance appliance = applianceRepository.save(Appliance.create(
                    customer, bookedCategory, "LG", "세탁기 T100",
                    null, null, null, RegisterMethod.MANUAL));
            Symptom symptom = symptomRepository.save(Symptom.builder()
                    .category(bookedCategory).symptomCode("SPIN_FAIL").symptomName("탈수 불량").build());
            AsRequest bookedRequest = asRequestRepository.save(AsRequest.builder()
                    .customer(customer).appliance(appliance).symptom(symptom)
                    .visitRegion(region).visitAddressDetail("강남구 테헤란로 123")
                    .scheduledDate(conflictDate).scheduledTime("09:00").build());
            asAssignmentRepository.save(AsAssignment.create(bookedRequest, engineer, agency, AssignType.AUTO));

            mockMvc.perform(get("/api/customers/{customerId}/engineers/{engineerId}/availability",
                            customer.getId(), engineer.getId())
                            .header("Authorization", "Bearer " + customerToken)
                            .param("from", conflictDate.toString())
                            .param("to", conflictDate.toString()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.availableDates['" + conflictDate + "'].length()").value(1))
                    .andExpect(jsonPath("$.availableDates['" + conflictDate + "'][0]").value("11:00"));
        }

        @Test
        @DisplayName("실패: 존재하지 않는 기사 — 404 Not Found")
        void fail_engineerNotFound() throws Exception {
            mockMvc.perform(get("/api/customers/{customerId}/engineers/{engineerId}/availability",
                            customer.getId(), 999999L)
                            .header("Authorization", "Bearer " + customerToken))
                    .andExpect(status().isNotFound());
        }

        @Test
        @DisplayName("실패: from > to — 400 Bad Request")
        void fail_invalidRange() throws Exception {
            mockMvc.perform(get("/api/customers/{customerId}/engineers/{engineerId}/availability",
                            customer.getId(), engineer.getId())
                            .header("Authorization", "Bearer " + customerToken)
                            .param("from", "2026-07-10")
                            .param("to", "2026-07-01"))
                    .andExpect(status().isBadRequest());
        }
    }
}
