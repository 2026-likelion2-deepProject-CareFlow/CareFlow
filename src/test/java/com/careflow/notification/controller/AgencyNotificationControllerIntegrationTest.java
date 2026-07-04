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

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("local")
@Sql(scripts = "/assignment_cleanup.sql", executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
@DisplayName("AgencyNotificationController 통합 테스트 (H2)")
class AgencyNotificationControllerIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private JwtProvider jwtProvider;

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
    private User otherCustomer;
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

        otherCustomer = userRepository.save(User.builder()
                .email("other-customer@test.com").passwordHash("hashed")
                .name("다른고객").phone("010-0000-0005")
                .role(Role.CUSTOMER).build());

        ApplianceCategory rootCat = categoryRepository.save(ApplianceCategory.createRoot("에어컨", 1));
        category = categoryRepository.save(ApplianceCategory.createChild("에어컨 소분류", rootCat, 1));
        appliance = applianceRepository.save(Appliance.create(
                customer, category, "삼성", "에어컨 Q9000",
                null, LocalDate.of(2023, 1, 1), LocalDate.of(2026, 1, 1), RegisterMethod.MANUAL));
        symptom = symptomRepository.save(Symptom.builder()
                .category(category).symptomCode("COOLING_FAIL").symptomName("냉방 불량").build());
    }

    // 현재 대행사로부터 A/S를 받은 것으로 customer를 연결시키는 헬퍼
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

    // Notification.createdAt은 엔티티 필드 기본값(now)으로만 설정되고 별도 setter가 없어,
    // 테스트에서는 항상 "지금" 생성된 알림만 만들 수 있다.
    private Notification saveNotification(User receiver, boolean isRead) {
        return saveNotification(receiver, "AS_STATUS", isRead);
    }

    private Notification saveNotification(User receiver, String type, boolean isRead) {
        Notification n = Notification.builder()
                .user(receiver).type(type)
                .title("알림 제목").body("알림 본문").channel("SSE").build();
        if (isRead) {
            n.markAsRead();
        }
        return notificationRepository.save(n);
    }

    @Nested
    @DisplayName("GET /api/agency/notifications — 대행사 알림센터 목록 조회")
    class GetNotifications {

        @Test
        @DisplayName("성공: 소속 기사에게 발송된 알림이 목록에 포함된다")
        void 소속기사알림_목록에포함() throws Exception {
            saveNotification(engineer, false);

            mockMvc.perform(get("/api/agency/notifications")
                            .header("Authorization", "Bearer " + agencyTokenWithAgencyId))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content.length()").value(1))
                    .andExpect(jsonPath("$.content[0].type").value("AS_STATUS"))
                    .andExpect(jsonPath("$.stats.totalCount").value(1));
        }

        @Test
        @DisplayName("성공: 대행사로부터 A/S를 받은 고객에게 발송된 알림이 목록에 포함된다")
        void 소속고객알림_목록에포함() throws Exception {
            linkCustomerToAgency(customer, agency);
            saveNotification(customer, false);

            mockMvc.perform(get("/api/agency/notifications")
                            .header("Authorization", "Bearer " + agencyTokenWithAgencyId))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content.length()").value(1));
        }

        @Test
        @DisplayName("성공: 다른 대행사 소속 기사·고객 알림은 결과에서 제외된다")
        void 타대행사알림_제외() throws Exception {
            linkCustomerToAgency(otherCustomer, otherAgency);
            saveNotification(otherEngineer, false);
            saveNotification(otherCustomer, false);

            mockMvc.perform(get("/api/agency/notifications")
                            .header("Authorization", "Bearer " + agencyTokenWithAgencyId))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content.length()").value(0))
                    .andExpect(jsonPath("$.stats.totalCount").value(0));
        }

        @Test
        @DisplayName("성공: unreadCount는 is_read=false 건수만 DB 값과 일치한다")
        void unreadCount_DB값과일치() throws Exception {
            saveNotification(engineer, false);
            saveNotification(engineer, false);
            saveNotification(engineer, true);

            mockMvc.perform(get("/api/agency/notifications")
                            .header("Authorization", "Bearer " + agencyTokenWithAgencyId))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.stats.totalCount").value(3))
                    .andExpect(jsonPath("$.stats.unreadCount").value(2));
        }

        @Test
        @DisplayName("성공: todayCount는 오늘 생성된 알림 건수와 일치한다")
        void todayCount_DB값과일치() throws Exception {
            // Notification.createdAt은 엔티티 생성 시점(now)으로 고정되므로,
            // 본 테스트에서 저장하는 알림은 모두 "오늘" 생성된 건으로 todayCount에 집계되어야 한다.
            saveNotification(engineer, false);
            saveNotification(engineer, false);

            mockMvc.perform(get("/api/agency/notifications")
                            .header("Authorization", "Bearer " + agencyTokenWithAgencyId))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.stats.todayCount").value(2));
        }

        @Test
        @DisplayName("성공: 페이징이 DB 데이터 기준으로 정상 동작한다 (15건 중 10건씩)")
        void 페이징_DB에서_정상조회() throws Exception {
            for (int i = 0; i < 15; i++) {
                saveNotification(engineer, false);
            }

            mockMvc.perform(get("/api/agency/notifications")
                            .header("Authorization", "Bearer " + agencyTokenWithAgencyId))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content.length()").value(10))
                    .andExpect(jsonPath("$.totalElements").value(15))
                    .andExpect(jsonPath("$.totalPages").value(2))
                    .andExpect(jsonPath("$.currentPage").value(0))
                    .andExpect(jsonPath("$.size").value(10));
        }

        @Test
        @DisplayName("성공: 페이지 파라미터(page=1) 적용 시 다음 페이지 데이터를 반환한다")
        void 페이징_두번째페이지_조회() throws Exception {
            for (int i = 0; i < 15; i++) {
                saveNotification(engineer, false);
            }

            mockMvc.perform(get("/api/agency/notifications?page=1&size=10")
                            .header("Authorization", "Bearer " + agencyTokenWithAgencyId))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content.length()").value(5))
                    .andExpect(jsonPath("$.currentPage").value(1));
        }

        @Test
        @DisplayName("실패: CUSTOMER 권한 → 401 Unauthorized")
        void CUSTOMER권한_401() throws Exception {
            mockMvc.perform(get("/api/agency/notifications")
                            .header("Authorization", "Bearer " + customerToken))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("성공: 수신 대상 알림이 전혀 없어도 200 OK + 빈 배열을 반환한다")
        void 알림없음_200_빈배열() throws Exception {
            mockMvc.perform(get("/api/agency/notifications")
                            .header("Authorization", "Bearer " + agencyTokenWithAgencyId))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content.length()").value(0))
                    .andExpect(jsonPath("$.stats.totalCount").value(0))
                    .andExpect(jsonPath("$.stats.unreadCount").value(0))
                    .andExpect(jsonPath("$.stats.todayCount").value(0));
        }
    }

    @Nested
    @DisplayName("GET /api/agency/notifications?type= — type 필터 조회")
    class GetNotificationsByType {

        @Test
        @DisplayName("성공: type=AS_STATUS 필터링 시 해당 타입만 조회된다")
        void type_AS_STATUS_필터링_목록조회() throws Exception {
            saveNotification(engineer, "AS_STATUS", false);
            saveNotification(engineer, "AS_STATUS", false);
            saveNotification(engineer, "LMS", false);

            mockMvc.perform(get("/api/agency/notifications?type=AS_STATUS")
                            .header("Authorization", "Bearer " + agencyTokenWithAgencyId))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content.length()").value(2))
                    .andExpect(jsonPath("$.content[0].type").value("AS_STATUS"))
                    .andExpect(jsonPath("$.content[1].type").value("AS_STATUS"));
        }

        @Test
        @DisplayName("성공: type 미입력 시 전체 타입이 반환된다")
        void type_미입력시_전체타입_반환() throws Exception {
            saveNotification(engineer, "AS_STATUS", false);
            saveNotification(engineer, "LMS", false);
            saveNotification(engineer, "WARRANTY", false);

            mockMvc.perform(get("/api/agency/notifications")
                            .header("Authorization", "Bearer " + agencyTokenWithAgencyId))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content.length()").value(3));
        }

        @Test
        @DisplayName("성공: type 필터링되어도 stats.totalCount는 전체 건수를 유지한다")
        void type_필터링되어도_stats_totalCount는_전체건수() throws Exception {
            saveNotification(engineer, "AS_STATUS", false);
            saveNotification(engineer, "LMS", false);
            saveNotification(engineer, "LMS", false);

            mockMvc.perform(get("/api/agency/notifications?type=AS_STATUS")
                            .header("Authorization", "Bearer " + agencyTokenWithAgencyId))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content.length()").value(1))
                    .andExpect(jsonPath("$.totalElements").value(1))
                    .andExpect(jsonPath("$.stats.totalCount").value(3));
        }

        @Test
        @DisplayName("성공: type 필터와 페이징이 동시에 적용된다")
        void type_필터_페이징_동시적용() throws Exception {
            for (int i = 0; i < 15; i++) {
                saveNotification(engineer, "AS_STATUS", false);
            }
            for (int i = 0; i < 5; i++) {
                saveNotification(engineer, "LMS", false);
            }

            mockMvc.perform(get("/api/agency/notifications?type=AS_STATUS&size=10")
                            .header("Authorization", "Bearer " + agencyTokenWithAgencyId))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content.length()").value(10))
                    .andExpect(jsonPath("$.totalElements").value(15))
                    .andExpect(jsonPath("$.totalPages").value(2));
        }

        @Test
        @DisplayName("실패: 허용되지 않은 type 값 → 400 Bad Request")
        void 잘못된type값_400() throws Exception {
            mockMvc.perform(get("/api/agency/notifications?type=INVALID_TYPE")
                            .header("Authorization", "Bearer " + agencyTokenWithAgencyId))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("성공: 해당 type의 알림이 없으면 200 OK + 빈 배열을 반환한다")
        void 해당타입알림없음_200_빈배열() throws Exception {
            saveNotification(engineer, "AS_STATUS", false);

            mockMvc.perform(get("/api/agency/notifications?type=WARRANTY")
                            .header("Authorization", "Bearer " + agencyTokenWithAgencyId))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content.length()").value(0));
        }
    }

    @Nested
    @DisplayName("PATCH /api/agency/notifications/{notificationId}/read — 알림 읽음 처리")
    class MarkNotificationRead {

        @Test
        @DisplayName("성공: 소속 기사 알림 읽음 처리 → 204, DB에 is_read=true 반영")
        void 소속기사알림_읽음처리_204() throws Exception {
            Notification n = saveNotification(engineer, false);

            mockMvc.perform(patch("/api/agency/notifications/" + n.getId() + "/read")
                            .header("Authorization", "Bearer " + agencyTokenWithAgencyId))
                    .andExpect(status().isNoContent());

            Notification updated = notificationRepository.findById(n.getId()).orElseThrow();
            assertThat(updated.isRead()).isTrue();
        }

        @Test
        @DisplayName("성공: 소속 고객 알림 읽음 처리 → 204, DB에 is_read=true 반영")
        void 소속고객알림_읽음처리_204() throws Exception {
            linkCustomerToAgency(customer, agency);
            Notification n = saveNotification(customer, false);

            mockMvc.perform(patch("/api/agency/notifications/" + n.getId() + "/read")
                            .header("Authorization", "Bearer " + agencyTokenWithAgencyId))
                    .andExpect(status().isNoContent());

            Notification updated = notificationRepository.findById(n.getId()).orElseThrow();
            assertThat(updated.isRead()).isTrue();
        }

        @Test
        @DisplayName("성공: 읽음 처리 후 GET 재호출 시 unreadCount가 감소한다")
        void 읽음처리후_GET재호출시_unreadCount감소확인() throws Exception {
            Notification n1 = saveNotification(engineer, false);
            saveNotification(engineer, false);

            mockMvc.perform(get("/api/agency/notifications")
                            .header("Authorization", "Bearer " + agencyTokenWithAgencyId))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.stats.unreadCount").value(2));

            mockMvc.perform(patch("/api/agency/notifications/" + n1.getId() + "/read")
                            .header("Authorization", "Bearer " + agencyTokenWithAgencyId))
                    .andExpect(status().isNoContent());

            mockMvc.perform(get("/api/agency/notifications")
                            .header("Authorization", "Bearer " + agencyTokenWithAgencyId))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.stats.unreadCount").value(1));
        }

        @Test
        @DisplayName("성공: 이미 읽음 상태인 알림에 재차 호출해도 204(멱등)")
        void 이미읽음상태_재호출_204() throws Exception {
            Notification n = saveNotification(engineer, true);

            mockMvc.perform(patch("/api/agency/notifications/" + n.getId() + "/read")
                            .header("Authorization", "Bearer " + agencyTokenWithAgencyId))
                    .andExpect(status().isNoContent());
        }

        @Test
        @DisplayName("실패: 존재하지 않는 알림 → 404 Not Found")
        void 존재하지않는알림_404() throws Exception {
            mockMvc.perform(patch("/api/agency/notifications/999999/read")
                            .header("Authorization", "Bearer " + agencyTokenWithAgencyId))
                    .andExpect(status().isNotFound());
        }

        @Test
        @DisplayName("실패: 타 대행사 소속 알림 → 401 Unauthorized, DB 값 변경 없음")
        void 타대행사알림_401() throws Exception {
            Notification n = saveNotification(otherEngineer, false);

            mockMvc.perform(patch("/api/agency/notifications/" + n.getId() + "/read")
                            .header("Authorization", "Bearer " + agencyTokenWithAgencyId))
                    .andExpect(status().isUnauthorized());

            Notification unchanged = notificationRepository.findById(n.getId()).orElseThrow();
            assertThat(unchanged.isRead()).isFalse();
        }

        @Test
        @DisplayName("실패: CUSTOMER 권한 → 401 Unauthorized")
        void CUSTOMER권한_401() throws Exception {
            Notification n = saveNotification(engineer, false);

            mockMvc.perform(patch("/api/agency/notifications/" + n.getId() + "/read")
                            .header("Authorization", "Bearer " + customerToken))
                    .andExpect(status().isUnauthorized());
        }
    }

    @Nested
    @DisplayName("PATCH /api/agency/notifications/read-all — 알림 전체 읽음 처리")
    class MarkAllNotificationsRead {

        @Test
        @DisplayName("성공: 소속 기사·고객 알림 전체를 읽음 처리한다 → 204")
        void 소속기사고객알림_전체읽음처리_204() throws Exception {
            linkCustomerToAgency(customer, agency);
            Notification n1 = saveNotification(engineer, false);
            Notification n2 = saveNotification(engineer, false);
            Notification n3 = saveNotification(customer, false);

            mockMvc.perform(patch("/api/agency/notifications/read-all")
                            .header("Authorization", "Bearer " + agencyTokenWithAgencyId))
                    .andExpect(status().isNoContent());

            assertThat(notificationRepository.findById(n1.getId()).orElseThrow().isRead()).isTrue();
            assertThat(notificationRepository.findById(n2.getId()).orElseThrow().isRead()).isTrue();
            assertThat(notificationRepository.findById(n3.getId()).orElseThrow().isRead()).isTrue();
        }

        @Test
        @DisplayName("성공: 전체 읽음 처리 후 GET 재호출 시 unreadCount가 0이다")
        void 읽음처리후_GET재호출시_unreadCount0() throws Exception {
            saveNotification(engineer, false);
            saveNotification(engineer, false);
            saveNotification(engineer, false);

            mockMvc.perform(patch("/api/agency/notifications/read-all")
                            .header("Authorization", "Bearer " + agencyTokenWithAgencyId))
                    .andExpect(status().isNoContent());

            mockMvc.perform(get("/api/agency/notifications")
                            .header("Authorization", "Bearer " + agencyTokenWithAgencyId))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.stats.unreadCount").value(0));
        }

        @Test
        @DisplayName("성공: 타 대행사 소속 알림은 영향받지 않는다")
        void 타대행사알림_영향없음() throws Exception {
            Notification otherNotification = saveNotification(otherEngineer, false);
            saveNotification(engineer, false);

            mockMvc.perform(patch("/api/agency/notifications/read-all")
                            .header("Authorization", "Bearer " + agencyTokenWithAgencyId))
                    .andExpect(status().isNoContent());

            Notification unchanged = notificationRepository.findById(otherNotification.getId()).orElseThrow();
            assertThat(unchanged.isRead()).isFalse();
        }

        @Test
        @DisplayName("성공: 수신 대상 알림이 없어도 204(에러 없음)")
        void 대상없음_204() throws Exception {
            mockMvc.perform(patch("/api/agency/notifications/read-all")
                            .header("Authorization", "Bearer " + agencyTokenWithAgencyId))
                    .andExpect(status().isNoContent());
        }

        @Test
        @DisplayName("성공: 이미 전체 읽음 상태에서 재호출해도 204(멱등)")
        void 이미전체읽음상태_재호출_204() throws Exception {
            saveNotification(engineer, true);

            mockMvc.perform(patch("/api/agency/notifications/read-all")
                            .header("Authorization", "Bearer " + agencyTokenWithAgencyId))
                    .andExpect(status().isNoContent());
        }

        @Test
        @DisplayName("실패: CUSTOMER 권한 → 401 Unauthorized")
        void CUSTOMER권한_401() throws Exception {
            mockMvc.perform(patch("/api/agency/notifications/read-all")
                            .header("Authorization", "Bearer " + customerToken))
                    .andExpect(status().isUnauthorized());
        }
    }
}
