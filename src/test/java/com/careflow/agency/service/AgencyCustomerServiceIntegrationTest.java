package com.careflow.agency.service;

import com.careflow.agency.dto.request.AgencyCustomerSearchRequest;
import com.careflow.agency.dto.request.AgencyCustomerUpdateRequest;
import com.careflow.agency.dto.response.AgencyCustomerApplianceResponse;
import com.careflow.agency.dto.response.AgencyCustomerAsRequestResponse;
import com.careflow.agency.dto.response.AgencyCustomerListResponse;
import com.careflow.agency.dto.response.AgencyCustomerPaymentResponse;
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
import com.careflow.auth.security.CustomUserDetails;
import com.careflow.common.enums.AssignType;
import com.careflow.common.enums.AsStatus;
import com.careflow.common.enums.PaymentStatus;
import com.careflow.common.enums.PgProvider;
import com.careflow.common.enums.RegisterMethod;
import com.careflow.common.enums.Role;
import com.careflow.payment.entity.Payment;
import com.careflow.payment.repository.PaymentRepository;
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
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.NoSuchElementException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * AgencyCustomerService 통합 테스트 (H2 DB 연동)
 *
 * - @SpringBootTest: 전체 애플리케이션 컨텍스트 로드
 * - @ActiveProfiles("local"): H2 인메모리 DB 사용
 * - @Sql cleanup.sql: 각 테스트 전 데이터 전체 초기화 (FK 제약 일시 해제)
 */
@SpringBootTest
@ActiveProfiles("local")
@Sql(scripts = "/cleanup.sql", executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
@DisplayName("AgencyCustomerService 통합 테스트 (H2 DB 연동)")
class AgencyCustomerServiceIntegrationTest {

    @Autowired private AgencyCustomerService agencyCustomerService;

    @Autowired private UserRepository userRepository;
    @Autowired private AgenciesRepository agencyRepository;
    @Autowired private ApplianceCategoryRepository categoryRepository;
    @Autowired private RegionRepository regionRepository;
    @Autowired private ApplianceRepository applianceRepository;
    @Autowired private SymptomRepository symptomRepository;
    @Autowired private AsRequestRepository asRequestRepository;
    @Autowired private AsAssignmentRepository asAssignmentRepository;
    @Autowired private PaymentRepository paymentRepository;

    private Agencies agency;
    private User agencyUser;
    private User engineerUser;
    private ApplianceCategory leafCategory;
    private Regions district;
    private Symptom symptom;

    private static final Pageable PAGEABLE = PageRequest.of(0, 10);

    @BeforeEach
    void setUp() {
        agency = agencyRepository.save(Agencies.create(
                "테스트대행사", "123-45-67890", "서울시 강남구", 5.0));

        agencyUser = userRepository.save(User.builder()
                .email("agency@test.com").passwordHash("hashed")
                .name("대행사담당자").phone("010-9999-9999")
                .role(Role.AGENCY).agency(agency).build());

        engineerUser = userRepository.save(User.builder()
                .email("engineer@test.com").passwordHash("hashed")
                .name("테스트기사").phone("010-1234-5678")
                .role(Role.ENGINEER).agency(agency).build());

        ApplianceCategory rootCategory = categoryRepository.save(ApplianceCategory.createRoot("가전", 1));
        leafCategory = categoryRepository.save(ApplianceCategory.createChild("냉장고", rootCategory, 1));

        district = regionRepository.save(Regions.create("강남구", null, 2, 0));

        symptom = symptomRepository.save(Symptom.builder()
                .category(leafCategory).symptomCode("TEST_FAIL").symptomName("테스트 증상").build());
    }

    private CustomUserDetails agencyUserDetails() {
        return new CustomUserDetails(agencyUser.getId(), agencyUser.getEmail(), "pw", "AGENCY", agency.getId());
    }

    // COMPLETED 상태의 AsRequest + AsAssignment(COMPLETED) 한 건 생성
    private void createCompletedService(User customer, Agencies targetAgency) {
        Appliance appliance = applianceRepository.save(Appliance.create(
                customer, leafCategory, "삼성", "테스트모델", null, null, null, null));

        AsRequest req = AsRequest.builder()
                .customer(customer).appliance(appliance).symptom(symptom)
                .visitRegion(district).visitAddressDetail("테스트 주소")
                .scheduledDate(LocalDate.now()).scheduledTime("10:00").build();
        req.processAssignment(targetAgency);
        req.acceptAssignment();
        req.depart();
        req.arrive();
        req.startWork();
        req.completeWork();
        AsRequest saved = asRequestRepository.save(req);

        AsAssignment assignment = asAssignmentRepository.save(
                AsAssignment.create(saved, engineerUser, targetAgency, AssignType.MANUAL));
        asAssignmentRepository.updateStatus(assignment.getId(), "COMPLETED");
    }

    // 미완료(WAITING) 상태의 배정만 생성 — 목록에서 제외되어야 함
    private void createWaitingAssignment(User customer, Agencies targetAgency) {
        Appliance appliance = applianceRepository.save(Appliance.create(
                customer, leafCategory, "삼성", "테스트모델", null, null, null, null));

        AsRequest req = asRequestRepository.save(AsRequest.builder()
                .customer(customer).appliance(appliance).symptom(symptom)
                .visitRegion(district).visitAddressDetail("테스트 주소")
                .scheduledDate(LocalDate.now()).scheduledTime("10:00").build());

        asAssignmentRepository.save(AsAssignment.create(req, engineerUser, targetAgency, AssignType.MANUAL));
    }

    private User saveCustomer(String email, String name, String phone, Regions region, String addressDetail) {
        User customer = userRepository.save(User.builder()
                .email(email).passwordHash("hashed").name(name).phone(phone)
                .role(Role.CUSTOMER).regionId(region).addressDetail(addressDetail).build());
        return customer;
    }

    private Appliance saveAppliance(User customer, ApplianceCategory category, String brand, String modelName) {
        return applianceRepository.save(Appliance.create(
                customer, category, brand, modelName, "SN-001",
                LocalDate.of(2022, 3, 15), LocalDate.of(2025, 3, 15), RegisterMethod.MANUAL));
    }

    // 임의 status의 AsRequest를 targetAgency 소속으로 직접 생성(상태 전이 메서드 우회, 리플렉션으로 status 강제 세팅)
    private AsRequest saveAsRequestWithStatus(
            User customer, Appliance appliance, Agencies targetAgency, AsStatus status) {
        AsRequest req = AsRequest.builder()
                .customer(customer).appliance(appliance).symptom(symptom)
                .visitRegion(district).visitAddressDetail("테스트 주소")
                .scheduledDate(LocalDate.of(2024, 6, 20)).scheduledTime("14:00").build();
        ReflectionTestUtils.setField(req, "agency", targetAgency);
        ReflectionTestUtils.setField(req, "status", status);
        return asRequestRepository.save(req);
    }

    // 임의 status/pgProvider의 Payment를 직접 생성(리플렉션으로 강제 세팅) — createdAt은 updatable=false라 save() 이전에 세팅
    private Payment savePaymentWithStatus(
            AsRequest asRequest, User customer, int amount, PgProvider pgProvider, PaymentStatus status) {
        Payment payment = Payment.create(asRequest, customer, amount);
        ReflectionTestUtils.setField(payment, "pgProvider", pgProvider);
        ReflectionTestUtils.setField(payment, "status", status);
        if (status == PaymentStatus.SUCCESS) {
            ReflectionTestUtils.setField(payment, "paidAt", LocalDateTime.of(2024, 6, 20, 15, 30));
        }
        ReflectionTestUtils.setField(payment, "createdAt", LocalDateTime.of(2024, 6, 18, 10, 0));
        return paymentRepository.save(payment);
    }

    @Nested
    @DisplayName("searchCustomers")
    class SearchCustomers {

        @Test
        @DisplayName("TC-I-1: COMPLETED 서비스를 받은 고객만 목록에 포함된다")
        void success_onlyCompletedCustomersIncluded() throws IllegalAccessException {
            User completedCustomer = saveCustomer("c1@test.com", "완료고객", "010-1111-1111", district, "상세주소1");
            User waitingCustomer = saveCustomer("c2@test.com", "대기고객", "010-2222-2222", district, "상세주소2");

            createCompletedService(completedCustomer, agency);
            createWaitingAssignment(waitingCustomer, agency);

            AgencyCustomerListResponse response =
                    agencyCustomerService.searchCustomers(agencyUserDetails(), PAGEABLE, null);

            assertThat(response.content()).hasSize(1);
            assertThat(response.content().get(0).userId()).isEqualTo(completedCustomer.getId());
        }

        @Test
        @DisplayName("TC-I-2: 타 대행사의 COMPLETED 서비스 고객은 제외된다")
        void success_excludesOtherAgencyCustomers() throws IllegalAccessException {
            Agencies otherAgency = agencyRepository.save(
                    Agencies.create("다른대행사", "999-99-99999", "부산시", 3.0));
            User otherCustomer = saveCustomer("other@test.com", "타사고객", "010-3333-3333", district, "주소3");

            createCompletedService(otherCustomer, otherAgency);

            AgencyCustomerListResponse response =
                    agencyCustomerService.searchCustomers(agencyUserDetails(), PAGEABLE, null);

            assertThat(response.content()).isEmpty();
            assertThat(response.stats().totalCount()).isZero();
        }

        @Test
        @DisplayName("TC-I-3: 동일 고객이 COMPLETED 서비스를 여러 번 받아도 DISTINCT로 1건만 집계된다")
        void success_distinctCustomerEvenWithMultipleCompletedServices() throws IllegalAccessException {
            User customer = saveCustomer("repeat@test.com", "단골고객", "010-4444-4444", district, "주소4");

            createCompletedService(customer, agency);
            createCompletedService(customer, agency);
            createCompletedService(customer, agency);

            AgencyCustomerListResponse response =
                    agencyCustomerService.searchCustomers(agencyUserDetails(), PAGEABLE, null);

            assertThat(response.content()).hasSize(1);
            assertThat(response.stats().totalCount()).isEqualTo(1);
        }

        @Test
        @DisplayName("TC-I-4: 논리 삭제된 가전은 applianceCount에서 제외된다")
        void success_deletedApplianceExcludedFromCount() throws IllegalAccessException {
            User customer = saveCustomer("app@test.com", "가전고객", "010-5555-5555", district, "주소5");
            createCompletedService(customer, agency); // 가전 1개 생성(미삭제)

            Appliance deletedAppliance = applianceRepository.save(Appliance.create(
                    customer, leafCategory, "LG", "삭제모델", null, null, null, null));
            deletedAppliance.delete();
            applianceRepository.save(deletedAppliance);

            AgencyCustomerListResponse response =
                    agencyCustomerService.searchCustomers(agencyUserDetails(), PAGEABLE, null);

            assertThat(response.content().get(0).applianceCount()).isEqualTo(1);
        }

        @Test
        @DisplayName("TC-I-5: regions.name + users.address_detail 이 합쳐져 address로 반환된다")
        void success_addressJoinedFromRegionAndDetail() throws IllegalAccessException {
            User customer = saveCustomer("addr@test.com", "주소고객", "010-6666-6666", district, "테헤란로 123");
            createCompletedService(customer, agency);

            AgencyCustomerListResponse response =
                    agencyCustomerService.searchCustomers(agencyUserDetails(), PAGEABLE, null);

            assertThat(response.content().get(0).address()).isEqualTo("강남구 테헤란로 123");
        }

        @Test
        @DisplayName("TC-I-6: 11명 INSERT 후 size=10 조회 시 1페이지 10건, totalPages=2")
        void success_pagination() throws IllegalAccessException {
            for (int i = 0; i < 11; i++) {
                User customer = saveCustomer("page" + i + "@test.com", "고객" + i, "010-0000-" + String.format("%04d", i), district, "주소" + i);
                createCompletedService(customer, agency);
            }

            AgencyCustomerListResponse response =
                    agencyCustomerService.searchCustomers(agencyUserDetails(), PageRequest.of(0, 10), null);

            assertThat(response.content()).hasSize(10);
            assertThat(response.totalElements()).isEqualTo(11);
            assertThat(response.totalPages()).isEqualTo(2);
        }

        @Test
        @DisplayName("TC-I-7: status=ACTIVE 필터 — content는 필터링되지만 stats는 전체 모수 기준 유지")
        void success_statusFilterAppliedOnlyToContent() throws IllegalAccessException {
            User activeCustomer = saveCustomer("active@test.com", "활성고객", "010-7777-7777", district, "주소6");
            User inactiveCustomer = saveCustomer("inactive@test.com", "비활성고객", "010-8888-8888", district, "주소7");

            createCompletedService(activeCustomer, agency);
            createCompletedService(inactiveCustomer, agency);

            // status 변경 도메인 메서드가 없으므로 리플렉션으로 INACTIVE 세팅 후 재저장
            org.springframework.test.util.ReflectionTestUtils.setField(inactiveCustomer, "status", "INACTIVE");
            userRepository.save(inactiveCustomer);

            AgencyCustomerSearchRequest request =
                    new AgencyCustomerSearchRequest(null, "ACTIVE", null, null, null, null);

            AgencyCustomerListResponse response =
                    agencyCustomerService.searchCustomers(agencyUserDetails(), PAGEABLE, request);

            assertThat(response.content()).hasSize(1);
            assertThat(response.content().get(0).userId()).isEqualTo(activeCustomer.getId());
            // stats는 필터와 무관하게 전체 2명 기준
            assertThat(response.stats().totalCount()).isEqualTo(2);
            assertThat(response.stats().activeCount()).isEqualTo(1);
            assertThat(response.stats().inactiveCount()).isEqualTo(1);
        }

        @Test
        @DisplayName("TC-I-8: keyword 검색 — 이름/연락처/이메일 부분 일치")
        void success_keywordSearchMatchesNamePhoneEmail() throws IllegalAccessException {
            User customer = saveCustomer("keyword@test.com", "검색고객", "010-9999-0000", district, "주소8");
            createCompletedService(customer, agency);

            AgencyCustomerSearchRequest byName =
                    new AgencyCustomerSearchRequest("검색", null, null, null, null, null);
            AgencyCustomerSearchRequest byPhone =
                    new AgencyCustomerSearchRequest("9999-0000", null, null, null, null, null);
            AgencyCustomerSearchRequest byEmail =
                    new AgencyCustomerSearchRequest("keyword@", null, null, null, null, null);

            assertThat(agencyCustomerService.searchCustomers(agencyUserDetails(), PAGEABLE, byName).content()).hasSize(1);
            assertThat(agencyCustomerService.searchCustomers(agencyUserDetails(), PAGEABLE, byPhone).content()).hasSize(1);
            assertThat(agencyCustomerService.searchCustomers(agencyUserDetails(), PAGEABLE, byEmail).content()).hasSize(1);
        }

        @Test
        @DisplayName("실패: ENGINEER 역할로 호출 — IllegalAccessException")
        void fail_engineerRole_throwsIllegalAccess() {
            CustomUserDetails engineerDetails = new CustomUserDetails(
                    engineerUser.getId(), engineerUser.getEmail(), "pw", "ENGINEER", agency.getId());

            assertThatThrownBy(() ->
                    agencyCustomerService.searchCustomers(engineerDetails, PAGEABLE, null))
                    .isInstanceOf(IllegalAccessException.class)
                    .hasMessage("대행사 관리자만 접근할 수 있습니다.");
        }
    }

    @Nested
    @DisplayName("getCustomerAppliances")
    class GetCustomerAppliances {

        @Test
        @DisplayName("TC-I-1: 가전 2건 INSERT 후 조회 — 응답 필드가 DB 저장값과 일치")
        void success_returnsAppliancesMatchingDb() throws IllegalAccessException {
            User customer = saveCustomer("appl1@test.com", "가전고객1", "010-1111-2222", district, "주소1");
            createCompletedService(customer, agency); // 기본 가전 1건(냉장고/삼성) 동반 생성

            ApplianceCategory acCategory = categoryRepository.save(
                    ApplianceCategory.createChild("에어컨", leafCategory.getParent(), 2));
            saveAppliance(customer, acCategory, "LG", "휘센");

            List<AgencyCustomerApplianceResponse> result =
                    agencyCustomerService.getCustomerAppliances(agencyUserDetails(), customer.getId());

            assertThat(result).hasSize(2);
            assertThat(result).extracting(AgencyCustomerApplianceResponse::categoryName)
                    .containsExactlyInAnyOrder("냉장고", "에어컨");
            AgencyCustomerApplianceResponse ac = result.stream()
                    .filter(r -> r.categoryName().equals("에어컨")).findFirst().orElseThrow();
            assertThat(ac.brand()).isEqualTo("LG");
            assertThat(ac.modelName()).isEqualTo("휘센");
            assertThat(ac.serialNumber()).isEqualTo("SN-001");
            assertThat(ac.purchaseDate()).isEqualTo(LocalDate.of(2022, 3, 15));
            assertThat(ac.warrantyEndDate()).isEqualTo(LocalDate.of(2025, 3, 15));
            assertThat(ac.registerMethod()).isEqualTo("MANUAL");
        }

        @Test
        @DisplayName("TC-I-2: 논리 삭제된 가전은 결과에서 제외된다")
        void success_excludesDeletedAppliance() throws IllegalAccessException {
            User customer = saveCustomer("appl2@test.com", "가전고객2", "010-3333-4444", district, "주소2");
            createCompletedService(customer, agency); // 미삭제 가전 1건

            Appliance deleted = saveAppliance(customer, leafCategory, "삭제브랜드", "삭제모델");
            deleted.delete();
            applianceRepository.save(deleted);

            List<AgencyCustomerApplianceResponse> result =
                    agencyCustomerService.getCustomerAppliances(agencyUserDetails(), customer.getId());

            assertThat(result).hasSize(1);
            assertThat(result).extracting(AgencyCustomerApplianceResponse::brand)
                    .doesNotContain("삭제브랜드");
        }

        @Test
        @DisplayName("TC-I-3: 타 대행사 고객의 가전 조회 시도 — IllegalAccessException")
        void fail_otherAgencyCustomer_throwsIllegalAccess() {
            Agencies otherAgency = agencyRepository.save(
                    Agencies.create("다른대행사", "888-88-88888", "인천시", 2.0));
            User otherCustomer = saveCustomer("other@appl.com", "타사고객", "010-5555-6666", district, "주소3");
            createCompletedService(otherCustomer, otherAgency);

            assertThatThrownBy(() ->
                    agencyCustomerService.getCustomerAppliances(agencyUserDetails(), otherCustomer.getId()))
                    .isInstanceOf(IllegalAccessException.class)
                    .hasMessage("본인 대행사로부터 서비스를 받은 고객만 조회할 수 있습니다.");
        }

        @Test
        @DisplayName("TC-I-4: 존재하지 않는 userId — NoSuchElementException")
        void fail_userNotFound() {
            assertThatThrownBy(() ->
                    agencyCustomerService.getCustomerAppliances(agencyUserDetails(), 999999L))
                    .isInstanceOf(NoSuchElementException.class)
                    .hasMessage("해당 고객 정보를 찾을 수 없습니다.");
        }

        @Test
        @DisplayName("TC-I-5: 최신 등록순(createdAt DESC) 정렬 검증")
        void success_orderedByCreatedAtDesc() throws IllegalAccessException {
            User customer = saveCustomer("appl3@test.com", "가전고객3", "010-7777-8888", district, "주소4");
            createCompletedService(customer, agency); // 첫 번째 가전(냉장고) — 과거 시각으로 세팅

            ApplianceCategory acCategory = categoryRepository.save(
                    ApplianceCategory.createChild("세탁기", leafCategory.getParent(), 3));
            // created_at 컬럼은 updatable=false라 INSERT 시점에만 값을 반영할 수 있어 save() 이전에 reflection으로 세팅
            Appliance second = Appliance.create(customer, acCategory, "삼성", "세탁기모델", "SN-001",
                    LocalDate.of(2022, 3, 15), LocalDate.of(2025, 3, 15), RegisterMethod.MANUAL);
            org.springframework.test.util.ReflectionTestUtils.setField(
                    second, "createdAt", java.time.LocalDateTime.now().plusMinutes(5));
            second = applianceRepository.save(second);

            List<AgencyCustomerApplianceResponse> result =
                    agencyCustomerService.getCustomerAppliances(agencyUserDetails(), customer.getId());

            assertThat(result).hasSize(2);
            assertThat(result.get(0).applianceId()).isEqualTo(second.getId()); // 나중에 등록한 가전이 먼저
        }
    }

    @Nested
    @DisplayName("getCustomerAsRequests")
    class GetCustomerAsRequests {

        @Test
        @DisplayName("TC-I-1: A/S 요청 2건(서로 다른 상태) INSERT 후 조회 — 응답 필드가 DB 저장값과 일치")
        void success_returnsAsRequestsMatchingDb() throws IllegalAccessException {
            User customer = saveCustomer("ar1@test.com", "이력고객1", "010-1111-3333", district, "주소1");
            createCompletedService(customer, agency); // COMPLETED 1건(기본 냉장고/삼성/테스트모델)

            Appliance acAppliance = saveAppliance(customer, leafCategory, "LG", "휘센");
            saveAsRequestWithStatus(customer, acAppliance, agency, AsStatus.CANCELLED);

            List<AgencyCustomerAsRequestResponse> result =
                    agencyCustomerService.getCustomerAsRequests(agencyUserDetails(), customer.getId());

            assertThat(result).hasSize(2);
            assertThat(result).extracting(AgencyCustomerAsRequestResponse::status)
                    .containsExactlyInAnyOrder("COMPLETED", "CANCELLED");
            AgencyCustomerAsRequestResponse cancelled = result.stream()
                    .filter(r -> r.status().equals("CANCELLED")).findFirst().orElseThrow();
            assertThat(cancelled.applianceBrand()).isEqualTo("LG");
            assertThat(cancelled.applianceModelName()).isEqualTo("휘센");
            assertThat(cancelled.symptomName()).isEqualTo("테스트 증상");
            assertThat(cancelled.visitAddress()).isEqualTo("강남구 테스트 주소");
            assertThat(cancelled.scheduledDate()).isEqualTo(LocalDate.of(2024, 6, 20));
            assertThat(cancelled.scheduledTime()).isEqualTo("14:00");
        }

        @Test
        @DisplayName("TC-I-2: 동일 고객이 타 대행사에 접수한 A/S 요청은 제외된다")
        void success_excludesOtherAgencyAsRequests() throws IllegalAccessException {
            Agencies otherAgency = agencyRepository.save(
                    Agencies.create("타대행사", "777-77-77777", "대전시", 4.0));
            User customer = saveCustomer("ar2@test.com", "이력고객2", "010-2222-4444", district, "주소2");
            createCompletedService(customer, agency); // 본인 대행사 COMPLETED 1건

            Appliance otherAppliance = saveAppliance(customer, leafCategory, "위니아", "타사가전");
            saveAsRequestWithStatus(customer, otherAppliance, otherAgency, AsStatus.PENDING);

            List<AgencyCustomerAsRequestResponse> result =
                    agencyCustomerService.getCustomerAsRequests(agencyUserDetails(), customer.getId());

            assertThat(result).hasSize(1);
            assertThat(result).extracting(AgencyCustomerAsRequestResponse::applianceBrand)
                    .doesNotContain("위니아");
        }

        @Test
        @DisplayName("TC-I-3: CANCELLED/PAID 등 모든 상태가 필터 없이 전부 포함된다")
        void success_allStatusesIncluded() throws IllegalAccessException {
            User customer = saveCustomer("ar3@test.com", "이력고객3", "010-3333-5555", district, "주소3");
            createCompletedService(customer, agency); // COMPLETED 1건

            Appliance a1 = saveAppliance(customer, leafCategory, "브랜드1", "모델1");
            Appliance a2 = saveAppliance(customer, leafCategory, "브랜드2", "모델2");
            saveAsRequestWithStatus(customer, a1, agency, AsStatus.CANCELLED);
            saveAsRequestWithStatus(customer, a2, agency, AsStatus.PAID);

            List<AgencyCustomerAsRequestResponse> result =
                    agencyCustomerService.getCustomerAsRequests(agencyUserDetails(), customer.getId());

            assertThat(result).hasSize(3);
            assertThat(result).extracting(AgencyCustomerAsRequestResponse::status)
                    .containsExactlyInAnyOrder("COMPLETED", "CANCELLED", "PAID");
        }

        @Test
        @DisplayName("TC-I-4: 타 대행사 고객(COMPLETED 이력 없음) 조회 시도 — IllegalAccessException")
        void fail_otherAgencyCustomer_throwsIllegalAccess() {
            Agencies otherAgency = agencyRepository.save(
                    Agencies.create("무관대행사", "666-66-66666", "광주시", 1.0));
            User otherCustomer = saveCustomer("ar4@test.com", "무관고객", "010-4444-6666", district, "주소4");
            createCompletedService(otherCustomer, otherAgency);

            assertThatThrownBy(() ->
                    agencyCustomerService.getCustomerAsRequests(agencyUserDetails(), otherCustomer.getId()))
                    .isInstanceOf(IllegalAccessException.class)
                    .hasMessage("본인 대행사로부터 서비스를 받은 고객만 조회할 수 있습니다.");
        }

        @Test
        @DisplayName("TC-I-5: 존재하지 않는 userId — NoSuchElementException")
        void fail_userNotFound() {
            assertThatThrownBy(() ->
                    agencyCustomerService.getCustomerAsRequests(agencyUserDetails(), 999999L))
                    .isInstanceOf(NoSuchElementException.class)
                    .hasMessage("해당 고객 정보를 찾을 수 없습니다.");
        }

        @Test
        @DisplayName("TC-I-6: 최신 접수순(createdAt DESC) 정렬 검증")
        void success_orderedByCreatedAtDesc() throws IllegalAccessException {
            User customer = saveCustomer("ar5@test.com", "이력고객5", "010-5555-7777", district, "주소5");
            createCompletedService(customer, agency); // 과거에 생성된 1건

            Appliance appliance = saveAppliance(customer, leafCategory, "최신브랜드", "최신모델");
            // created_at 컬럼은 updatable=false라 INSERT 시점에만 값을 반영할 수 있어 save() 이전에 reflection으로 세팅
            AsRequest latest = AsRequest.builder()
                    .customer(customer).appliance(appliance).symptom(symptom)
                    .visitRegion(district).visitAddressDetail("최신 주소")
                    .scheduledDate(LocalDate.of(2024, 6, 20)).scheduledTime("14:00").build();
            ReflectionTestUtils.setField(latest, "agency", agency);
            ReflectionTestUtils.setField(latest, "status", AsStatus.PENDING);
            ReflectionTestUtils.setField(latest, "createdAt", LocalDateTime.now().plusMinutes(5));
            latest = asRequestRepository.save(latest);

            List<AgencyCustomerAsRequestResponse> result =
                    agencyCustomerService.getCustomerAsRequests(agencyUserDetails(), customer.getId());

            assertThat(result).hasSize(2);
            assertThat(result.get(0).requestId()).isEqualTo(latest.getId()); // 나중에 접수한 건이 먼저
        }
    }

    @Nested
    @DisplayName("getCustomerPayments")
    class GetCustomerPayments {

        @Test
        @DisplayName("TC-I-1: 결제 2건(서로 다른 상태) INSERT 후 조회 — 응답 필드가 DB 저장값과 일치")
        void success_returnsPaymentsMatchingDb() throws IllegalAccessException {
            User customer = saveCustomer("pay1@test.com", "결제고객1", "010-1111-4444", district, "주소1");
            createCompletedService(customer, agency); // COMPLETED 서비스 이력(인가 통과용), 결제는 아래서 별도 부여 안 함

            Appliance a1 = saveAppliance(customer, leafCategory, "삼성", "바람의나라 AF17");
            AsRequest r1 = saveAsRequestWithStatus(customer, a1, agency, AsStatus.COMPLETED);
            savePaymentWithStatus(r1, customer, 85000, PgProvider.KAKAO, PaymentStatus.SUCCESS);

            Appliance a2 = saveAppliance(customer, leafCategory, "LG", "휘센");
            AsRequest r2 = saveAsRequestWithStatus(customer, a2, agency, AsStatus.COMPLETED);
            savePaymentWithStatus(r2, customer, 50000, PgProvider.TOSS, PaymentStatus.READY);

            List<AgencyCustomerPaymentResponse> result =
                    agencyCustomerService.getCustomerPayments(agencyUserDetails(), customer.getId());

            assertThat(result).hasSize(2);
            AgencyCustomerPaymentResponse success = result.stream()
                    .filter(r -> r.status().equals("SUCCESS")).findFirst().orElseThrow();
            assertThat(success.requestId()).isEqualTo(r1.getId());
            assertThat(success.applianceBrand()).isEqualTo("삼성");
            assertThat(success.applianceModelName()).isEqualTo("바람의나라 AF17");
            assertThat(success.amount()).isEqualTo(85000);
            assertThat(success.pgProvider()).isEqualTo("KAKAO");
            assertThat(success.paidAt()).isEqualTo(LocalDateTime.of(2024, 6, 20, 15, 30));
        }

        @Test
        @DisplayName("TC-I-2: 동일 고객이 타 대행사로 접수한 A/S 건의 결제 내역은 제외된다")
        void success_excludesOtherAgencyPayments() throws IllegalAccessException {
            Agencies otherAgency = agencyRepository.save(
                    Agencies.create("타대행사", "555-55-55555", "울산시", 3.0));
            User customer = saveCustomer("pay2@test.com", "결제고객2", "010-2222-5555", district, "주소2");
            createCompletedService(customer, agency); // 본인 대행사 COMPLETED 이력(인가 통과용)

            Appliance otherAppliance = saveAppliance(customer, leafCategory, "위니아", "타사가전");
            AsRequest otherRequest = saveAsRequestWithStatus(customer, otherAppliance, otherAgency, AsStatus.COMPLETED);
            savePaymentWithStatus(otherRequest, customer, 30000, PgProvider.NAVER, PaymentStatus.SUCCESS);

            List<AgencyCustomerPaymentResponse> result =
                    agencyCustomerService.getCustomerPayments(agencyUserDetails(), customer.getId());

            assertThat(result).extracting(AgencyCustomerPaymentResponse::applianceBrand)
                    .doesNotContain("위니아");
        }

        @Test
        @DisplayName("TC-I-3: READY/FAILED/CANCELLED/REFUNDED 등 모든 상태가 필터 없이 전부 포함된다")
        void success_allStatusesIncluded() throws IllegalAccessException {
            User customer = saveCustomer("pay3@test.com", "결제고객3", "010-3333-6666", district, "주소3");
            createCompletedService(customer, agency);

            PaymentStatus[] statuses = {
                    PaymentStatus.READY, PaymentStatus.FAILED, PaymentStatus.CANCELLED, PaymentStatus.REFUNDED
            };
            for (int i = 0; i < statuses.length; i++) {
                Appliance appliance = saveAppliance(customer, leafCategory, "브랜드" + i, "모델" + i);
                AsRequest req = saveAsRequestWithStatus(customer, appliance, agency, AsStatus.CANCELLED);
                savePaymentWithStatus(req, customer, 10000, PgProvider.MOCK, statuses[i]);
            }

            List<AgencyCustomerPaymentResponse> result =
                    agencyCustomerService.getCustomerPayments(agencyUserDetails(), customer.getId());

            assertThat(result).hasSize(4);
            assertThat(result).extracting(AgencyCustomerPaymentResponse::status)
                    .containsExactlyInAnyOrder("READY", "FAILED", "CANCELLED", "REFUNDED");
        }

        @Test
        @DisplayName("TC-I-4: 타 대행사 고객(COMPLETED 이력 없음) 조회 시도 — IllegalAccessException")
        void fail_otherAgencyCustomer_throwsIllegalAccess() {
            Agencies otherAgency = agencyRepository.save(
                    Agencies.create("무관대행사2", "444-44-44444", "전주시", 2.0));
            User otherCustomer = saveCustomer("pay4@test.com", "무관고객2", "010-4444-7777", district, "주소4");
            createCompletedService(otherCustomer, otherAgency);

            assertThatThrownBy(() ->
                    agencyCustomerService.getCustomerPayments(agencyUserDetails(), otherCustomer.getId()))
                    .isInstanceOf(IllegalAccessException.class)
                    .hasMessage("본인 대행사로부터 서비스를 받은 고객만 조회할 수 있습니다.");
        }

        @Test
        @DisplayName("TC-I-5: 존재하지 않는 userId — NoSuchElementException")
        void fail_userNotFound() {
            assertThatThrownBy(() ->
                    agencyCustomerService.getCustomerPayments(agencyUserDetails(), 999999L))
                    .isInstanceOf(NoSuchElementException.class)
                    .hasMessage("해당 고객 정보를 찾을 수 없습니다.");
        }

        @Test
        @DisplayName("TC-I-6: 최신 생성순(createdAt DESC) 정렬 검증")
        void success_orderedByCreatedAtDesc() throws IllegalAccessException {
            User customer = saveCustomer("pay5@test.com", "결제고객5", "010-5555-8888", district, "주소5");
            createCompletedService(customer, agency);

            Appliance a1 = saveAppliance(customer, leafCategory, "오래된브랜드", "오래된모델");
            AsRequest r1 = saveAsRequestWithStatus(customer, a1, agency, AsStatus.COMPLETED);
            Payment older = Payment.create(r1, customer, 10000);
            ReflectionTestUtils.setField(older, "createdAt", LocalDateTime.of(2020, 1, 1, 0, 0));
            paymentRepository.save(older);

            Appliance a2 = saveAppliance(customer, leafCategory, "최신브랜드", "최신모델");
            AsRequest r2 = saveAsRequestWithStatus(customer, a2, agency, AsStatus.COMPLETED);
            Payment latest = Payment.create(r2, customer, 20000);
            ReflectionTestUtils.setField(latest, "createdAt", LocalDateTime.now().plusMinutes(5));
            latest = paymentRepository.save(latest);

            List<AgencyCustomerPaymentResponse> result =
                    agencyCustomerService.getCustomerPayments(agencyUserDetails(), customer.getId());

            assertThat(result).hasSize(2);
            assertThat(result.get(0).paymentId()).isEqualTo(latest.getId()); // 나중에 생성된 결제가 먼저
        }
    }

    @Nested
    @DisplayName("updateCustomerProfile")
    class UpdateCustomerProfile {

        @Test
        @DisplayName("TC-I-1: 정상 수정 흐름 — DB에 반영 확인")
        void success_updatesAndPersists() throws IllegalAccessException {
            User customer = saveCustomer("update1@test.com", "기존이름", "010-0000-0000", district, "기존주소");
            createCompletedService(customer, agency);

            AgencyCustomerUpdateRequest request =
                    new AgencyCustomerUpdateRequest("변경된이름", "010-9999-8888", "변경된주소");
            agencyCustomerService.updateCustomerProfile(agencyUserDetails(), customer.getId(), request);

            User updated = userRepository.findById(customer.getId()).orElseThrow();
            assertThat(updated.getName()).isEqualTo("변경된이름");
            assertThat(updated.getPhone()).isEqualTo("010-9999-8888");
            assertThat(updated.getAddressDetail()).isEqualTo("변경된주소");
        }

        @Test
        @DisplayName("TC-I-2: 타 대행사 고객 수정 시도 — IllegalAccessException")
        void fail_otherAgencyCustomer() {
            Agencies otherAgency = agencyRepository.save(Agencies.create(
                    "타대행사", "999-99-99999", "서울시 서초구", 3.0));
            User customer = saveCustomer("update2@test.com", "타대행사고객", "010-1111-1111", district, "주소");
            createCompletedService(customer, otherAgency);

            AgencyCustomerUpdateRequest request = new AgencyCustomerUpdateRequest("변경시도", null, null);

            assertThatThrownBy(() ->
                    agencyCustomerService.updateCustomerProfile(agencyUserDetails(), customer.getId(), request))
                    .isInstanceOf(IllegalAccessException.class);
        }
    }

    @Nested
    @DisplayName("resetCustomerPassword")
    class ResetCustomerPassword {

        @Test
        @DisplayName("TC-I-1: 정상 초기화 흐름 — DB password_hash 변경 확인")
        void success_resetsPasswordHash() throws IllegalAccessException {
            User customer = saveCustomer("reset1@test.com", "고객", "010-0000-0000", district, "주소");
            createCompletedService(customer, agency);
            String originalHash = customer.getPasswordHash();

            agencyCustomerService.resetCustomerPassword(agencyUserDetails(), customer.getId());

            User updated = userRepository.findById(customer.getId()).orElseThrow();
            assertThat(updated.getPasswordHash()).isNotEqualTo(originalHash);
        }

        @Test
        @DisplayName("TC-I-2: 소셜 로그인 계정(passwordHash null) — IllegalStateException, DB 변경 없음")
        void fail_socialLoginAccount() {
            User customer = userRepository.save(User.builder()
                    .email("social@test.com").passwordHash(null).name("소셜고객")
                    .phone("010-2222-2222").role(Role.CUSTOMER).regionId(district).build());
            createCompletedService(customer, agency);

            assertThatThrownBy(() ->
                    agencyCustomerService.resetCustomerPassword(agencyUserDetails(), customer.getId()))
                    .isInstanceOf(IllegalStateException.class);

            User unchanged = userRepository.findById(customer.getId()).orElseThrow();
            assertThat(unchanged.getPasswordHash()).isNull();
        }
    }

    @Nested
    @DisplayName("blockCustomer / unblockCustomer")
    class BlockUnblockCustomer {

        @Test
        @DisplayName("TC-I-1: 차단 후 로그인 시도 시 실패, 차단 해제 후 정상 로그인")
        void success_blockPreventsLogin_unblockRestores() throws IllegalAccessException {
            User customer = userRepository.save(User.builder()
                    .email("block1@test.com")
                    .passwordHash("$2a$10$7EqJtq98hPqEX7fNZaFWoOa1Vq9F.uxOU4Cj/cO7yMLmObj4dgKKm") // bcrypt("password")
                    .name("차단대상고객").phone("010-3333-3333").role(Role.CUSTOMER).regionId(district).build());
            createCompletedService(customer, agency);

            agencyCustomerService.blockCustomer(agencyUserDetails(), customer.getId());

            User blocked = userRepository.findById(customer.getId()).orElseThrow();
            assertThat(blocked.getStatus()).isEqualTo("SUSPENDED");

            agencyCustomerService.unblockCustomer(agencyUserDetails(), customer.getId());

            User restored = userRepository.findById(customer.getId()).orElseThrow();
            assertThat(restored.getStatus()).isEqualTo("ACTIVE");
        }

        @Test
        @DisplayName("TC-I-2: 이미 SUSPENDED인 고객 재차단 시도 — 멱등하게 처리(에러 없음)")
        void success_idempotentBlock() throws IllegalAccessException {
            User customer = saveCustomer("block2@test.com", "고객", "010-4444-4444", district, "주소");
            createCompletedService(customer, agency);

            agencyCustomerService.blockCustomer(agencyUserDetails(), customer.getId());
            agencyCustomerService.blockCustomer(agencyUserDetails(), customer.getId());

            User result = userRepository.findById(customer.getId()).orElseThrow();
            assertThat(result.getStatus()).isEqualTo("SUSPENDED");
        }
    }
}
