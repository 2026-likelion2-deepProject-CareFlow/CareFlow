package com.careflow.agency.service;

import com.careflow.agency.dto.request.AgencyCustomerSearchRequest;
import com.careflow.agency.dto.response.AgencyCustomerApplianceResponse;
import com.careflow.agency.dto.response.AgencyCustomerAsRequestResponse;
import com.careflow.agency.dto.response.AgencyCustomerListResponse;
import com.careflow.appliance.entity.Appliance;
import com.careflow.appliance.entity.ApplianceCategory;
import com.careflow.appliance.repository.ApplianceRepository;
import com.careflow.as_request.entity.AsRequest;
import com.careflow.as_request.repository.AsRequestRepository;
import com.careflow.assignment.repository.AsAssignmentRepository;
import com.careflow.auth.security.CustomUserDetails;
import com.careflow.common.enums.ApplianceStatus;
import com.careflow.common.enums.AsStatus;
import com.careflow.common.enums.PaymentStatus;
import com.careflow.common.enums.PgProvider;
import com.careflow.common.enums.RegisterMethod;
import com.careflow.common.enums.Role;
import com.careflow.payment.entity.Payment;
import com.careflow.payment.repository.PaymentRepository;
import com.careflow.region.entity.Regions;
import com.careflow.symptom.entity.Symptom;
import com.careflow.user.entity.User;
import com.careflow.user.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("AgencyCustomerService 단위 테스트")
class AgencyCustomerServiceTest {

    @InjectMocks
    private AgencyCustomerService agencyCustomerService;

    @Mock private AsAssignmentRepository asAssignmentRepository;
    @Mock private UserRepository userRepository;
    @Mock private ApplianceRepository applianceRepository;
    @Mock private AsRequestRepository asRequestRepository;
    @Mock private PaymentRepository paymentRepository;

    private static final Long AGENCY_ID = 100L;
    private static final Pageable PAGEABLE = PageRequest.of(0, 10);

    private CustomUserDetails agencyUserDetails() {
        return new CustomUserDetails(1L, "agency@test.com", "pw", "AGENCY", AGENCY_ID);
    }

    private User buildUser(Long id, String name, String phone, String email,
                            String addressDetail, Regions region, String status) {
        User user = User.builder()
                .email(email).passwordHash("hashed").name(name).phone(phone)
                .role(Role.CUSTOMER).regionId(region).addressDetail(addressDetail).build();
        ReflectionTestUtils.setField(user, "id", id);
        ReflectionTestUtils.setField(user, "status", status);
        ReflectionTestUtils.setField(user, "createdAt", LocalDateTime.now());
        return user;
    }

    private Regions buildRegion(Integer id, String name) {
        Regions region = Regions.create(name, null, 2, 0);
        ReflectionTestUtils.setField(region, "id", id);
        return region;
    }

    @Nested
    @DisplayName("searchCustomers")
    class SearchCustomers {

        @Test
        @DisplayName("TC-1: 정상 조회 — COMPLETED 고객 2명, 필터 없음")
        void success_returnsCustomers() throws IllegalAccessException {
            List<Long> customerIds = List.of(1L, 2L);
            given(asAssignmentRepository.findDistinctCompletedCustomerIdsByAgencyId(AGENCY_ID))
                    .willReturn(customerIds);

            given(userRepository.countByIdInAndRole(customerIds, Role.CUSTOMER)).willReturn(2L);
            given(userRepository.countByIdInAndStatus(customerIds, "ACTIVE")).willReturn(1L);
            given(userRepository.countByIdInAndStatus(customerIds, "INACTIVE")).willReturn(1L);
            given(userRepository.countByIdInAndCreatedAtRange(eq(customerIds), any(), any())).willReturn(0L);

            Regions region = buildRegion(10, "강남구");
            User u1 = buildUser(1L, "김민수", "010-1111-1111", "a@test.com", "테헤란로 123", region, "ACTIVE");
            User u2 = buildUser(2L, "이영희", "010-2222-2222", "b@test.com", "역삼로 1", region, "INACTIVE");
            Page<User> page = new PageImpl<>(List.of(u1, u2), PAGEABLE, 2);

            given(userRepository.searchAgencyCustomers(eq(customerIds), isNull(), isNull(), isNull(), isNull(), eq(PAGEABLE)))
                    .willReturn(page);
            given(applianceRepository.countActiveByUserIds(List.of(1L, 2L)))
                    .willReturn(List.<Object[]>of(new Object[]{1L, 3L}));

            AgencyCustomerListResponse response =
                    agencyCustomerService.searchCustomers(agencyUserDetails(), PAGEABLE, null);

            assertThat(response.content()).hasSize(2);
            assertThat(response.stats().totalCount()).isEqualTo(2);
            assertThat(response.content().get(0).applianceCount()).isEqualTo(3);
            assertThat(response.content().get(1).applianceCount()).isEqualTo(0);
        }

        @Test
        @DisplayName("TC-2: COMPLETED 고객 0명 — stats 전부 0, content 빈 리스트, 추가 Repository 호출 없음")
        void success_emptyWhenNoCompletedCustomer() throws IllegalAccessException {
            given(asAssignmentRepository.findDistinctCompletedCustomerIdsByAgencyId(AGENCY_ID))
                    .willReturn(List.of());

            AgencyCustomerListResponse response =
                    agencyCustomerService.searchCustomers(agencyUserDetails(), PAGEABLE, null);

            assertThat(response.content()).isEmpty();
            assertThat(response.stats().totalCount()).isZero();
            assertThat(response.stats().activeCount()).isZero();
            assertThat(response.stats().newThisMonthDiff()).isZero();
            verify(userRepository, never()).searchAgencyCustomers(any(), any(), any(), any(), any(), any());
            verify(applianceRepository, never()).countActiveByUserIds(any());
        }

        @Test
        @DisplayName("TC-3: keyword 검색 — Repository에 올바른 keyword 파라미터 전달")
        void success_keywordPassedToRepository() throws IllegalAccessException {
            List<Long> customerIds = List.of(1L);
            given(asAssignmentRepository.findDistinctCompletedCustomerIdsByAgencyId(AGENCY_ID))
                    .willReturn(customerIds);
            given(userRepository.countByIdInAndRole(any(), any())).willReturn(1L);
            given(userRepository.countByIdInAndStatus(any(), any())).willReturn(0L);
            given(userRepository.countByIdInAndCreatedAtRange(any(), any(), any())).willReturn(0L);
            given(userRepository.searchAgencyCustomers(any(), any(), any(), any(), any(), any()))
                    .willReturn(new PageImpl<>(List.of(), PAGEABLE, 0));

            AgencyCustomerSearchRequest request =
                    new AgencyCustomerSearchRequest("김민수", null, null, null, null, null);

            agencyCustomerService.searchCustomers(agencyUserDetails(), PAGEABLE, request);

            verify(userRepository).searchAgencyCustomers(
                    eq(customerIds), isNull(), eq("김민수"), isNull(), isNull(), eq(PAGEABLE));
        }

        @Test
        @DisplayName("TC-4: joinedFrom/joinedTo 정상 파싱 — 범위로 변환되어 전달")
        void success_joinedDateParsedToRange() throws IllegalAccessException {
            List<Long> customerIds = List.of(1L);
            given(asAssignmentRepository.findDistinctCompletedCustomerIdsByAgencyId(AGENCY_ID))
                    .willReturn(customerIds);
            given(userRepository.countByIdInAndRole(any(), any())).willReturn(1L);
            given(userRepository.countByIdInAndStatus(any(), any())).willReturn(0L);
            given(userRepository.countByIdInAndCreatedAtRange(any(), any(), any())).willReturn(0L);
            given(userRepository.searchAgencyCustomers(any(), any(), any(), any(), any(), any()))
                    .willReturn(new PageImpl<>(List.of(), PAGEABLE, 0));

            AgencyCustomerSearchRequest request =
                    new AgencyCustomerSearchRequest(null, null, null, null, "2024-01-01", "2024-12-31");

            agencyCustomerService.searchCustomers(agencyUserDetails(), PAGEABLE, request);

            verify(userRepository).searchAgencyCustomers(
                    eq(customerIds), isNull(), isNull(),
                    eq(LocalDateTime.of(2024, 1, 1, 0, 0)),
                    eq(LocalDateTime.of(2025, 1, 1, 0, 0)),
                    eq(PAGEABLE));
        }

        @Test
        @DisplayName("TC-5: joinedFrom 잘못된 형식 — IllegalArgumentException")
        void fail_invalidJoinedFromFormat() {
            given(asAssignmentRepository.findDistinctCompletedCustomerIdsByAgencyId(AGENCY_ID))
                    .willReturn(List.of(1L));

            AgencyCustomerSearchRequest request =
                    new AgencyCustomerSearchRequest(null, null, null, null, "2024-13-99", null);

            assertThatThrownBy(() ->
                    agencyCustomerService.searchCustomers(agencyUserDetails(), PAGEABLE, request))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("가입일 형식이 올바르지 않습니다. (yyyy-MM-dd)");
        }

        @Test
        @DisplayName("TC-6: address 조합 — region + addressDetail 모두 있으면 \"지역명 상세주소\"")
        void success_addressCombinesRegionAndDetail() throws IllegalAccessException {
            List<Long> customerIds = List.of(1L);
            given(asAssignmentRepository.findDistinctCompletedCustomerIdsByAgencyId(AGENCY_ID))
                    .willReturn(customerIds);
            given(userRepository.countByIdInAndRole(any(), any())).willReturn(1L);
            given(userRepository.countByIdInAndStatus(any(), any())).willReturn(0L);
            given(userRepository.countByIdInAndCreatedAtRange(any(), any(), any())).willReturn(0L);

            Regions region = buildRegion(10, "서울특별시 강남구");
            User user = buildUser(1L, "김민수", "010-1111-1111", "a@test.com", "테헤란로 123", region, "ACTIVE");
            given(userRepository.searchAgencyCustomers(any(), any(), any(), any(), any(), any()))
                    .willReturn(new PageImpl<>(List.of(user), PAGEABLE, 1));
            given(applianceRepository.countActiveByUserIds(any())).willReturn(List.of());

            AgencyCustomerListResponse response =
                    agencyCustomerService.searchCustomers(agencyUserDetails(), PAGEABLE, null);

            assertThat(response.content().get(0).address()).isEqualTo("서울특별시 강남구 테헤란로 123");
        }

        @Test
        @DisplayName("TC-7: address 조합 — region이 null이면 addressDetail만 사용 (NPE 없음)")
        void success_addressWithoutRegion() throws IllegalAccessException {
            List<Long> customerIds = List.of(1L);
            given(asAssignmentRepository.findDistinctCompletedCustomerIdsByAgencyId(AGENCY_ID))
                    .willReturn(customerIds);
            given(userRepository.countByIdInAndRole(any(), any())).willReturn(1L);
            given(userRepository.countByIdInAndStatus(any(), any())).willReturn(0L);
            given(userRepository.countByIdInAndCreatedAtRange(any(), any(), any())).willReturn(0L);

            User user = buildUser(1L, "김민수", "010-1111-1111", "a@test.com", "테헤란로 123", null, "ACTIVE");
            given(userRepository.searchAgencyCustomers(any(), any(), any(), any(), any(), any()))
                    .willReturn(new PageImpl<>(List.of(user), PAGEABLE, 1));
            given(applianceRepository.countActiveByUserIds(any())).willReturn(List.of());

            AgencyCustomerListResponse response =
                    agencyCustomerService.searchCustomers(agencyUserDetails(), PAGEABLE, null);

            assertThat(response.content().get(0).address()).isEqualTo("테헤란로 123");
        }

        @Test
        @DisplayName("TC-8: applianceCount — 가전 보유/미보유 고객 혼합 시 Map 매핑 정확")
        void success_applianceCountMapping() throws IllegalAccessException {
            List<Long> customerIds = List.of(1L, 2L);
            given(asAssignmentRepository.findDistinctCompletedCustomerIdsByAgencyId(AGENCY_ID))
                    .willReturn(customerIds);
            given(userRepository.countByIdInAndRole(any(), any())).willReturn(2L);
            given(userRepository.countByIdInAndStatus(any(), any())).willReturn(0L);
            given(userRepository.countByIdInAndCreatedAtRange(any(), any(), any())).willReturn(0L);

            Regions region = buildRegion(10, "강남구");
            User u1 = buildUser(1L, "보유고객", "010-1111-1111", "a@test.com", "주소1", region, "ACTIVE");
            User u2 = buildUser(2L, "미보유고객", "010-2222-2222", "b@test.com", "주소2", region, "ACTIVE");
            given(userRepository.searchAgencyCustomers(any(), any(), any(), any(), any(), any()))
                    .willReturn(new PageImpl<>(List.of(u1, u2), PAGEABLE, 2));
            given(applianceRepository.countActiveByUserIds(List.of(1L, 2L)))
                    .willReturn(List.<Object[]>of(new Object[]{1L, 5L}));

            AgencyCustomerListResponse response =
                    agencyCustomerService.searchCustomers(agencyUserDetails(), PAGEABLE, null);

            Map<Long, Integer> countByUserId = response.content().stream()
                    .collect(java.util.stream.Collectors.toMap(
                            AgencyCustomerListResponse.CustomerSummary::userId,
                            AgencyCustomerListResponse.CustomerSummary::applianceCount));
            assertThat(countByUserId.get(1L)).isEqualTo(5);
            assertThat(countByUserId.get(2L)).isEqualTo(0);
        }

        @Test
        @DisplayName("TC-9: ENGINEER 역할로 호출 — IllegalAccessException")
        void fail_engineerRole_throwsIllegalAccess() {
            CustomUserDetails engineerDetails =
                    new CustomUserDetails(2L, "engineer@test.com", "pw", "ENGINEER", AGENCY_ID);

            assertThatThrownBy(() ->
                    agencyCustomerService.searchCustomers(engineerDetails, PAGEABLE, null))
                    .isInstanceOf(IllegalAccessException.class)
                    .hasMessage("대행사 관리자만 접근할 수 있습니다.");
        }
    }

    @Nested
    @DisplayName("getCustomerAppliances")
    class GetCustomerAppliances {

        private static final Long CUSTOMER_ID = 1L;

        private Appliance buildAppliance(Long id, String categoryName, String brand) {
            ApplianceCategory category = ApplianceCategory.createRoot(categoryName, 1);
            ReflectionTestUtils.setField(category, "categoryId", 5);
            User customer = User.builder()
                    .email("customer@test.com").passwordHash("hashed").name("고객").phone("010-0000-0000")
                    .role(Role.CUSTOMER).build();
            ReflectionTestUtils.setField(customer, "id", CUSTOMER_ID);

            Appliance appliance = Appliance.create(customer, category, brand, "모델명", "SN-001",
                    LocalDate.of(2022, 3, 15), LocalDate.of(2025, 3, 15), RegisterMethod.MANUAL);
            ReflectionTestUtils.setField(appliance, "id", id);
            ReflectionTestUtils.setField(appliance, "status", ApplianceStatus.NORMAL);
            ReflectionTestUtils.setField(appliance, "createdAt", LocalDateTime.now());
            return appliance;
        }

        @Test
        @DisplayName("TC-1: 정상 조회 — 가전 2건, 필드 매핑(categoryName 포함) 검증")
        void success_returnsAppliances() throws IllegalAccessException {
            given(userRepository.existsById(CUSTOMER_ID)).willReturn(true);
            given(asAssignmentRepository.findDistinctCompletedCustomerIdsByAgencyId(AGENCY_ID))
                    .willReturn(List.of(CUSTOMER_ID));

            Appliance a1 = buildAppliance(1L, "에어컨", "삼성");
            Appliance a2 = buildAppliance(2L, "냉장고", "LG");
            given(applianceRepository.findByUserIdWithCategory(CUSTOMER_ID)).willReturn(List.of(a1, a2));

            List<AgencyCustomerApplianceResponse> result =
                    agencyCustomerService.getCustomerAppliances(agencyUserDetails(), CUSTOMER_ID);

            assertThat(result).hasSize(2);
            assertThat(result.get(0).categoryName()).isEqualTo("에어컨");
            assertThat(result.get(0).brand()).isEqualTo("삼성");
            assertThat(result.get(0).serialNumber()).isEqualTo("SN-001");
            assertThat(result.get(0).status()).isEqualTo("NORMAL");
            assertThat(result.get(0).registerMethod()).isEqualTo("MANUAL");
        }

        @Test
        @DisplayName("TC-2: 가전 없음 — 빈 리스트 반환")
        void success_emptyListWhenNoAppliance() throws IllegalAccessException {
            given(userRepository.existsById(CUSTOMER_ID)).willReturn(true);
            given(asAssignmentRepository.findDistinctCompletedCustomerIdsByAgencyId(AGENCY_ID))
                    .willReturn(List.of(CUSTOMER_ID));
            given(applianceRepository.findByUserIdWithCategory(CUSTOMER_ID)).willReturn(List.of());

            List<AgencyCustomerApplianceResponse> result =
                    agencyCustomerService.getCustomerAppliances(agencyUserDetails(), CUSTOMER_ID);

            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("TC-3: 존재하지 않는 userId — NoSuchElementException")
        void fail_userNotFound() {
            given(userRepository.existsById(999L)).willReturn(false);

            assertThatThrownBy(() ->
                    agencyCustomerService.getCustomerAppliances(agencyUserDetails(), 999L))
                    .isInstanceOf(NoSuchElementException.class)
                    .hasMessage("해당 고객 정보를 찾을 수 없습니다.");
        }

        @Test
        @DisplayName("TC-4: COMPLETED 서비스 이력 없는 고객 — IllegalAccessException")
        void fail_noCompletedServiceHistory() {
            given(userRepository.existsById(CUSTOMER_ID)).willReturn(true);
            given(asAssignmentRepository.findDistinctCompletedCustomerIdsByAgencyId(AGENCY_ID))
                    .willReturn(List.of(999L)); // CUSTOMER_ID 미포함

            assertThatThrownBy(() ->
                    agencyCustomerService.getCustomerAppliances(agencyUserDetails(), CUSTOMER_ID))
                    .isInstanceOf(IllegalAccessException.class)
                    .hasMessage("본인 대행사로부터 서비스를 받은 고객만 조회할 수 있습니다.");
        }

        @Test
        @DisplayName("TC-5: ENGINEER 역할로 호출 — IllegalAccessException")
        void fail_engineerRole_throwsIllegalAccess() {
            CustomUserDetails engineerDetails =
                    new CustomUserDetails(2L, "engineer@test.com", "pw", "ENGINEER", AGENCY_ID);

            assertThatThrownBy(() ->
                    agencyCustomerService.getCustomerAppliances(engineerDetails, CUSTOMER_ID))
                    .isInstanceOf(IllegalAccessException.class)
                    .hasMessage("대행사 관리자만 접근할 수 있습니다.");
        }
    }

    @Nested
    @DisplayName("getCustomerAsRequests")
    class GetCustomerAsRequests {

        private static final Long CUSTOMER_ID = 1L;

        private AsRequest buildAsRequest(Long id, AsStatus status, String symptomDesc) {
            User customer = User.builder()
                    .email("customer@test.com").passwordHash("hashed").name("고객").phone("010-0000-0000")
                    .role(Role.CUSTOMER).build();
            ReflectionTestUtils.setField(customer, "id", CUSTOMER_ID);

            ApplianceCategory category = ApplianceCategory.createRoot("에어컨", 1);
            Appliance appliance = Appliance.create(customer, category, "삼성", "바람의나라 AF17",
                    "SN-001", null, null, RegisterMethod.MANUAL);

            Symptom symptom = Symptom.builder()
                    .category(category).symptomCode("COOLING_FAIL").symptomName("냉방 불량").build();

            Regions region = Regions.create("강남구", null, 2, 0);

            AsRequest request = AsRequest.builder()
                    .customer(customer).appliance(appliance).symptom(symptom)
                    .symptomDesc(symptomDesc).visitRegion(region).visitAddressDetail("테헤란로 123")
                    .scheduledDate(LocalDate.of(2024, 6, 20)).scheduledTime("14:00").build();
            ReflectionTestUtils.setField(request, "id", id);
            ReflectionTestUtils.setField(request, "status", status);
            ReflectionTestUtils.setField(request, "createdAt", LocalDateTime.of(2024, 6, 18, 10, 0));
            return request;
        }

        @Test
        @DisplayName("TC-1: 정상 조회 — A/S 이력 2건, 필드 매핑(visitAddress 조합 포함) 검증")
        void success_returnsAsRequests() throws IllegalAccessException {
            given(userRepository.existsById(CUSTOMER_ID)).willReturn(true);
            given(asAssignmentRepository.findDistinctCompletedCustomerIdsByAgencyId(AGENCY_ID))
                    .willReturn(List.of(CUSTOMER_ID));

            AsRequest r1 = buildAsRequest(1L, AsStatus.COMPLETED, "에어컨이 작동은 되는데 냉방이 안돼요");
            AsRequest r2 = buildAsRequest(2L, AsStatus.CANCELLED, null);
            given(asRequestRepository.findByCustomerIdAndAgencyId(CUSTOMER_ID, AGENCY_ID))
                    .willReturn(List.of(r1, r2));

            List<AgencyCustomerAsRequestResponse> result =
                    agencyCustomerService.getCustomerAsRequests(agencyUserDetails(), CUSTOMER_ID);

            assertThat(result).hasSize(2);
            assertThat(result.get(0).requestId()).isEqualTo(1L);
            assertThat(result.get(0).applianceBrand()).isEqualTo("삼성");
            assertThat(result.get(0).applianceModelName()).isEqualTo("바람의나라 AF17");
            assertThat(result.get(0).symptomName()).isEqualTo("냉방 불량");
            assertThat(result.get(0).visitAddress()).isEqualTo("강남구 테헤란로 123");
            assertThat(result.get(0).status()).isEqualTo("COMPLETED");
            assertThat(result.get(1).status()).isEqualTo("CANCELLED");
        }

        @Test
        @DisplayName("TC-2: A/S 이력 없음 — 빈 리스트 반환")
        void success_emptyListWhenNoHistory() throws IllegalAccessException {
            given(userRepository.existsById(CUSTOMER_ID)).willReturn(true);
            given(asAssignmentRepository.findDistinctCompletedCustomerIdsByAgencyId(AGENCY_ID))
                    .willReturn(List.of(CUSTOMER_ID));
            given(asRequestRepository.findByCustomerIdAndAgencyId(CUSTOMER_ID, AGENCY_ID))
                    .willReturn(List.of());

            List<AgencyCustomerAsRequestResponse> result =
                    agencyCustomerService.getCustomerAsRequests(agencyUserDetails(), CUSTOMER_ID);

            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("TC-3: 존재하지 않는 userId — NoSuchElementException")
        void fail_userNotFound() {
            given(userRepository.existsById(999L)).willReturn(false);

            assertThatThrownBy(() ->
                    agencyCustomerService.getCustomerAsRequests(agencyUserDetails(), 999L))
                    .isInstanceOf(NoSuchElementException.class)
                    .hasMessage("해당 고객 정보를 찾을 수 없습니다.");
        }

        @Test
        @DisplayName("TC-4: COMPLETED 서비스 이력 없는 고객 — IllegalAccessException")
        void fail_noCompletedServiceHistory() {
            given(userRepository.existsById(CUSTOMER_ID)).willReturn(true);
            given(asAssignmentRepository.findDistinctCompletedCustomerIdsByAgencyId(AGENCY_ID))
                    .willReturn(List.of(999L)); // CUSTOMER_ID 미포함

            assertThatThrownBy(() ->
                    agencyCustomerService.getCustomerAsRequests(agencyUserDetails(), CUSTOMER_ID))
                    .isInstanceOf(IllegalAccessException.class)
                    .hasMessage("본인 대행사로부터 서비스를 받은 고객만 조회할 수 있습니다.");
        }

        @Test
        @DisplayName("TC-5: ENGINEER 역할로 호출 — IllegalAccessException")
        void fail_engineerRole_throwsIllegalAccess() {
            CustomUserDetails engineerDetails =
                    new CustomUserDetails(2L, "engineer@test.com", "pw", "ENGINEER", AGENCY_ID);

            assertThatThrownBy(() ->
                    agencyCustomerService.getCustomerAsRequests(engineerDetails, CUSTOMER_ID))
                    .isInstanceOf(IllegalAccessException.class)
                    .hasMessage("대행사 관리자만 접근할 수 있습니다.");
        }
    }

    @Nested
    @DisplayName("getCustomerPayments")
    class GetCustomerPayments {

        private static final Long CUSTOMER_ID = 1L;

        private Payment buildPayment(Long id, Long requestId, PaymentStatus status,
                                      String brand, String modelName) {
            User customer = User.builder()
                    .email("customer@test.com").passwordHash("hashed").name("고객").phone("010-0000-0000")
                    .role(Role.CUSTOMER).build();
            ReflectionTestUtils.setField(customer, "id", CUSTOMER_ID);

            ApplianceCategory category = ApplianceCategory.createRoot("에어컨", 1);
            Appliance appliance = Appliance.create(customer, category, brand, modelName,
                    "SN-001", null, null, RegisterMethod.MANUAL);

            Symptom symptom = Symptom.builder()
                    .category(category).symptomCode("COOLING_FAIL").symptomName("냉방 불량").build();
            Regions region = Regions.create("강남구", null, 2, 0);

            AsRequest asRequest = AsRequest.builder()
                    .customer(customer).appliance(appliance).symptom(symptom)
                    .visitRegion(region).visitAddressDetail("테헤란로 123")
                    .scheduledDate(LocalDate.of(2024, 6, 20)).scheduledTime("14:00").build();
            ReflectionTestUtils.setField(asRequest, "id", requestId);

            Payment payment = Payment.create(asRequest, customer, 85000);
            ReflectionTestUtils.setField(payment, "id", id);
            ReflectionTestUtils.setField(payment, "pgProvider", PgProvider.KAKAO);
            ReflectionTestUtils.setField(payment, "status", status);
            ReflectionTestUtils.setField(payment, "paidAt", LocalDateTime.of(2024, 6, 20, 15, 30));
            ReflectionTestUtils.setField(payment, "createdAt", LocalDateTime.of(2024, 6, 18, 10, 0));
            return payment;
        }

        @Test
        @DisplayName("TC-1: 정상 조회 — 결제 내역 2건, 필드 매핑(applianceBrand/Model 포함) 검증")
        void success_returnsPayments() throws IllegalAccessException {
            given(userRepository.existsById(CUSTOMER_ID)).willReturn(true);
            given(asAssignmentRepository.findDistinctCompletedCustomerIdsByAgencyId(AGENCY_ID))
                    .willReturn(List.of(CUSTOMER_ID));

            Payment p1 = buildPayment(1L, 1L, PaymentStatus.SUCCESS, "삼성", "바람의나라 AF17");
            Payment p2 = buildPayment(2L, 2L, PaymentStatus.READY, "LG", "휘센");
            given(paymentRepository.findByCustomerIdAndAgencyId(CUSTOMER_ID, AGENCY_ID))
                    .willReturn(List.of(p1, p2));

            List<com.careflow.agency.dto.response.AgencyCustomerPaymentResponse> result =
                    agencyCustomerService.getCustomerPayments(agencyUserDetails(), CUSTOMER_ID);

            assertThat(result).hasSize(2);
            assertThat(result.get(0).paymentId()).isEqualTo(1L);
            assertThat(result.get(0).requestId()).isEqualTo(1L);
            assertThat(result.get(0).applianceBrand()).isEqualTo("삼성");
            assertThat(result.get(0).applianceModelName()).isEqualTo("바람의나라 AF17");
            assertThat(result.get(0).amount()).isEqualTo(85000);
            assertThat(result.get(0).pgProvider()).isEqualTo("KAKAO");
            assertThat(result.get(0).status()).isEqualTo("SUCCESS");
            assertThat(result.get(1).status()).isEqualTo("READY");
        }

        @Test
        @DisplayName("TC-2: 결제 내역 없음 — 빈 리스트 반환")
        void success_emptyListWhenNoPayments() throws IllegalAccessException {
            given(userRepository.existsById(CUSTOMER_ID)).willReturn(true);
            given(asAssignmentRepository.findDistinctCompletedCustomerIdsByAgencyId(AGENCY_ID))
                    .willReturn(List.of(CUSTOMER_ID));
            given(paymentRepository.findByCustomerIdAndAgencyId(CUSTOMER_ID, AGENCY_ID))
                    .willReturn(List.of());

            List<com.careflow.agency.dto.response.AgencyCustomerPaymentResponse> result =
                    agencyCustomerService.getCustomerPayments(agencyUserDetails(), CUSTOMER_ID);

            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("TC-3: 존재하지 않는 userId — NoSuchElementException")
        void fail_userNotFound() {
            given(userRepository.existsById(999L)).willReturn(false);

            assertThatThrownBy(() ->
                    agencyCustomerService.getCustomerPayments(agencyUserDetails(), 999L))
                    .isInstanceOf(NoSuchElementException.class)
                    .hasMessage("해당 고객 정보를 찾을 수 없습니다.");
        }

        @Test
        @DisplayName("TC-4: COMPLETED 서비스 이력 없는 고객 — IllegalAccessException")
        void fail_noCompletedServiceHistory() {
            given(userRepository.existsById(CUSTOMER_ID)).willReturn(true);
            given(asAssignmentRepository.findDistinctCompletedCustomerIdsByAgencyId(AGENCY_ID))
                    .willReturn(List.of(999L)); // CUSTOMER_ID 미포함

            assertThatThrownBy(() ->
                    agencyCustomerService.getCustomerPayments(agencyUserDetails(), CUSTOMER_ID))
                    .isInstanceOf(IllegalAccessException.class)
                    .hasMessage("본인 대행사로부터 서비스를 받은 고객만 조회할 수 있습니다.");
        }

        @Test
        @DisplayName("TC-5: ENGINEER 역할로 호출 — IllegalAccessException")
        void fail_engineerRole_throwsIllegalAccess() {
            CustomUserDetails engineerDetails =
                    new CustomUserDetails(2L, "engineer@test.com", "pw", "ENGINEER", AGENCY_ID);

            assertThatThrownBy(() ->
                    agencyCustomerService.getCustomerPayments(engineerDetails, CUSTOMER_ID))
                    .isInstanceOf(IllegalAccessException.class)
                    .hasMessage("대행사 관리자만 접근할 수 있습니다.");
        }
    }
}
