package com.careflow.notification.controller;

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
import com.careflow.notification.entity.Notification;
import com.careflow.notification.repository.NotificationRepository;
import com.careflow.region.entity.Regions;
import com.careflow.region.repository.RegionRepository;
import com.careflow.symptom.entity.Symptom;
import com.careflow.symptom.repository.SymptomRepository;
import com.careflow.user.entity.User;
import com.careflow.user.repository.UserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("local")
@Sql(scripts = "/assignment_cleanup.sql", executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
@DisplayName("AgencyNoticeDetailController 통합 테스트 (H2)")
class AgencyNoticeDetailControllerIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private JwtProvider jwtProvider;
    @Autowired private ObjectMapper objectMapper;

    @Autowired private AgenciesRepository agenciesRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private RegionRepository regionRepository;
    @Autowired private ApplianceCategoryRepository categoryRepository;
    @Autowired private ApplianceRepository applianceRepository;
    @Autowired private SymptomRepository symptomRepository;
    @Autowired private AsRequestRepository asRequestRepository;
    @Autowired private NotificationRepository notificationRepository;

    private Agencies agency;
    private Agencies otherAgency;
    private User agencyManager;
    private User engineer;
    private User customer;
    private User otherEngineer;
    private Regions region;
    private ApplianceCategory category;
    private Appliance appliance;
    private Symptom symptom;

    private String agencyTokenWithAgencyId;
    private String customerToken;

    @BeforeEach
    void setUp() {
        region = regionRepository.save(Regions.create("서울특별시 강남구", null, 1, 0));

        agency = agenciesRepository.save(Agencies.builder()
                .agencyName("테스트대행사").businessNumber("TEST-BIZ-001")
                .agencyAddress("서울특별시 강남구").agencyFeeRate(5.0)
                .approvalStatus(AgencyStatus.APPROVED).build());
        otherAgency = agenciesRepository.save(Agencies.builder()
                .agencyName("다른대행사").businessNumber("OTHER-BIZ-002")
                .agencyAddress("서울특별시 서초구").agencyFeeRate(4.0)
                .approvalStatus(AgencyStatus.APPROVED).build());

        agencyManager = userRepository.save(User.builder()
                .email("manager@agency.com").passwordHash("hashed")
                .name("대행사관리자").phone("010-0000-0001")
                .role(Role.AGENCY).agency(agency).build());
        agencyTokenWithAgencyId = jwtProvider.generateAccessToken(
                agencyManager.getId(), agencyManager.getEmail(), "AGENCY", agency.getId());

        engineer = userRepository.save(User.builder()
                .email("engineer@agency.com").passwordHash("hashed")
                .name("테스트기사").phone("010-0000-0002")
                .role(Role.ENGINEER).agency(agency).build());

        customer = userRepository.save(User.builder()
                .email("customer@test.com").passwordHash("hashed")
                .name("테스트고객").phone("010-0000-0003")
                .role(Role.CUSTOMER).build());
        customerToken = jwtProvider.generateAccessToken(
                customer.getId(), customer.getEmail(), "CUSTOMER", null);

        otherEngineer = userRepository.save(User.builder()
                .email("other-engineer@agency.com").passwordHash("hashed")
                .name("다른기사").phone("010-0000-0004")
                .role(Role.ENGINEER).agency(otherAgency).build());

        ApplianceCategory rootCat = categoryRepository.save(ApplianceCategory.createRoot("에어컨", 1));
        category = categoryRepository.save(ApplianceCategory.createChild("에어컨 소분류", rootCat, 1));
        appliance = applianceRepository.save(Appliance.create(
                customer, category, "삼성", "에어컨 Q9000",
                null, LocalDate.of(2023, 1, 1), LocalDate.of(2026, 1, 1), RegisterMethod.MANUAL));
        symptom = symptomRepository.save(Symptom.builder()
                .category(category).symptomCode("COOLING_FAIL").symptomName("냉방 불량").build());
    }

    private void linkCustomerToAgency(User targetCustomer, Agencies targetAgency) {
        AsRequest req = asRequestRepository.save(AsRequest.builder()
                .customer(targetCustomer).appliance(appliance).symptom(symptom)
                .symptomDesc("냉방이 전혀 되지 않습니다")
                .visitRegion(region).visitAddressDetail("강남구 테헤란로 123")
                .scheduledDate(LocalDate.of(2026, 7, 1)).scheduledTime("10:00")
                .build());
        req.assignAgency(targetAgency);
        asRequestRepository.save(req);
    }

    private Notification saveNotification(User receiver) {
        Notification n = Notification.builder()
                .user(receiver).type("AS_STATUS")
                .title("A/S 상태가 변경되었습니다.")
                .body("김민수 고객의 A/S가 완료 처리되었습니다.")
                .channel("SSE").build();
        return notificationRepository.save(n);
    }

    private String body(Long notificationId) throws Exception {
        return objectMapper.writeValueAsString(new com.careflow.notification.dto.AgencyNoticeDetailRequest(notificationId));
    }

    @Test
    @DisplayName("성공: 소속 기사에게 발송된 알림을 단건 조회한다")
    void 소속기사알림_단건조회_200() throws Exception {
        Notification notification = saveNotification(engineer);

        mockMvc.perform(get("/api/agency/notices")
                        .header("Authorization", "Bearer " + agencyTokenWithAgencyId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(notification.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].notificationId").value(notification.getId()))
                .andExpect(jsonPath("$[0].type").value("AS_STATUS"))
                .andExpect(jsonPath("$[0].title").value("A/S 상태가 변경되었습니다."));
    }

    @Test
    @DisplayName("성공: 대행사로부터 A/S를 받은 고객에게 발송된 알림을 단건 조회한다")
    void 소속고객알림_단건조회_200() throws Exception {
        linkCustomerToAgency(customer, agency);
        Notification notification = saveNotification(customer);

        mockMvc.perform(get("/api/agency/notices")
                        .header("Authorization", "Bearer " + agencyTokenWithAgencyId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(notification.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].notificationId").value(notification.getId()));
    }

    @Test
    @DisplayName("실패: 존재하지 않는 notificationId → 404 Not Found")
    void 존재하지않는ID_404() throws Exception {
        mockMvc.perform(get("/api/agency/notices")
                        .header("Authorization", "Bearer " + agencyTokenWithAgencyId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(999999L)))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("실패: 다른 대행사 소속 알림 조회 → 401 Unauthorized")
    void 타대행사알림_401() throws Exception {
        Notification notification = saveNotification(otherEngineer);

        mockMvc.perform(get("/api/agency/notices")
                        .header("Authorization", "Bearer " + agencyTokenWithAgencyId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(notification.getId())))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("실패: notificationId 누락 → 400 Bad Request")
    void notificationId누락_400() throws Exception {
        mockMvc.perform(get("/api/agency/notices")
                        .header("Authorization", "Bearer " + agencyTokenWithAgencyId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("실패: CUSTOMER 권한 → 401 Unauthorized")
    void CUSTOMER권한_401() throws Exception {
        Notification notification = saveNotification(engineer);

        mockMvc.perform(get("/api/agency/notices")
                        .header("Authorization", "Bearer " + customerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(notification.getId())))
                .andExpect(status().isUnauthorized());
    }
}
