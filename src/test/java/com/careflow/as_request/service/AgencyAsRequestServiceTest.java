package com.careflow.as_request.service;

import com.careflow.agency.entity.Agencies;
import com.careflow.appliance.entity.Appliance;
import com.careflow.appliance.entity.ApplianceCategory;
import com.careflow.as_request.dto.AgencyAsRequestDetailResponse;
import com.careflow.as_request.dto.AgencyAsRequestListResponse;
import com.careflow.as_request.entity.AsRequest;
import com.careflow.as_request.repository.AsRequestRepository;
import com.careflow.auth.security.CustomUserDetails;
import com.careflow.common.enums.AsStatus;
import com.careflow.common.enums.AssignType;
import com.careflow.common.enums.Role;
import com.careflow.region.entity.Regions;
import com.careflow.symptom.entity.Symptom;
import com.careflow.user.entity.User;
import com.careflow.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.ArgumentMatchers.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("AgencyAsRequestService 단위 테스트")
class AgencyAsRequestServiceTest {

    @InjectMocks
    private AgencyAsRequestService agencyAsRequestService;

    @Mock private AsRequestRepository asRequestRepository;
    @Mock private UserRepository userRepository;

    // ── 픽스처 ──────────────────────────────────────────────────────
    private static final Long AGENCY_USER_ID = 1L;
    private static final Long AGENCY_ID      = 10L;
    private static final Long REQUEST_ID     = 100L;

    private CustomUserDetails agencyUserDetails;
    private User agencyUser;
    private Agencies agency;
    private AsRequest stubRequest;

    @BeforeEach
    void setUp() {
        agencyUserDetails = new CustomUserDetails(
                AGENCY_USER_ID, "agency@test.com", "pw", "AGENCY");

        agency = buildAgency(AGENCY_ID);
        agencyUser = buildUser(AGENCY_USER_ID, Role.AGENCY, agency);
        stubRequest = buildRequest(REQUEST_ID, AsStatus.ASSIGNED, agency);

        given(userRepository.findById(AGENCY_USER_ID)).willReturn(Optional.of(agencyUser));
    }

    // ─────────────────────────────────────────────
    //  getAsRequestsByAgency — 전체 목록 조회
    // ─────────────────────────────────────────────
    @Nested
    @DisplayName("getAsRequestsByAgency — 전체 목록 조회")
    class GetAsRequestsByAgency {

        @Test
        @DisplayName("성공: ASSIGNED 요청 1건 반환 — 200")
        void success_oneRequest() throws IllegalAccessException {
            given(asRequestRepository.findByAgencyIdExcludeStatus(AGENCY_ID, AsStatus.COMPLETED))
                    .willReturn(List.of(stubRequest));

            List<AgencyAsRequestListResponse> result =
                    agencyAsRequestService.getAsRequestsByAgency(agencyUserDetails);

            assertThat(result).hasSize(1);
            assertThat(result.get(0).requestId()).isEqualTo(REQUEST_ID);
            assertThat(result.get(0).status()).isEqualTo(AsStatus.ASSIGNED);
        }

        @Test
        @DisplayName("성공: 조회 결과 없음 — 빈 리스트 반환")
        void success_empty() throws IllegalAccessException {
            given(asRequestRepository.findByAgencyIdExcludeStatus(AGENCY_ID, AsStatus.COMPLETED))
                    .willReturn(List.of());

            List<AgencyAsRequestListResponse> result =
                    agencyAsRequestService.getAsRequestsByAgency(agencyUserDetails);

            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("실패: AGENCY 권한 없음 — IllegalAccessException")
        void fail_notAgencyRole() {
            CustomUserDetails customer = new CustomUserDetails(2L, "c@test.com", "pw", "CUSTOMER");

            assertThatThrownBy(() -> agencyAsRequestService.getAsRequestsByAgency(customer))
                    .isInstanceOf(IllegalAccessException.class)
                    .hasMessageContaining("대행사 관리자 권한이 없습니다");
        }

        @Test
        @DisplayName("실패: 소속 대행사 정보 없음 — IllegalStateException")
        void fail_noAgency() {
            User userWithoutAgency = buildUser(AGENCY_USER_ID, Role.AGENCY, null);
            given(userRepository.findById(AGENCY_USER_ID)).willReturn(Optional.of(userWithoutAgency));

            assertThatThrownBy(() -> agencyAsRequestService.getAsRequestsByAgency(agencyUserDetails))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("소속 대행사 정보가 없습니다");
        }
    }

    // ─────────────────────────────────────────────
    //  searchAsRequests — 필터링 조회
    // ─────────────────────────────────────────────
    @Nested
    @DisplayName("searchAsRequests — 필터링 조회")
    class SearchAsRequests {

        private static final LocalDate FILTER_DATE = LocalDate.of(2026, 7, 1);

        @Test
        @DisplayName("성공: 날짜 필터만 적용 — 결과 1건 반환")
        void success_dateOnly() throws IllegalAccessException {
            given(asRequestRepository.searchByAgencyFilter(AGENCY_ID, AsStatus.COMPLETED, FILTER_DATE, null))
                    .willReturn(List.of(stubRequest));

            List<AgencyAsRequestListResponse> result =
                    agencyAsRequestService.searchAsRequests(agencyUserDetails, FILTER_DATE, null);

            assertThat(result).hasSize(1);
        }

        @Test
        @DisplayName("성공: 상태 필터만 적용 (ASSIGNED) — 결과 1건 반환")
        void success_statusOnly() throws IllegalAccessException {
            given(asRequestRepository.searchByAgencyFilter(AGENCY_ID, AsStatus.COMPLETED, null, AsStatus.ASSIGNED))
                    .willReturn(List.of(stubRequest));

            List<AgencyAsRequestListResponse> result =
                    agencyAsRequestService.searchAsRequests(agencyUserDetails, null, "ASSIGNED");

            assertThat(result).hasSize(1);
            assertThat(result.get(0).status()).isEqualTo(AsStatus.ASSIGNED);
        }

        @Test
        @DisplayName("성공: 필터 미입력 — 전체 조회 (날짜/상태 모두 null)")
        void success_noFilter() throws IllegalAccessException {
            given(asRequestRepository.searchByAgencyFilter(AGENCY_ID, AsStatus.COMPLETED, null, null))
                    .willReturn(List.of(stubRequest));

            List<AgencyAsRequestListResponse> result =
                    agencyAsRequestService.searchAsRequests(agencyUserDetails, null, null);

            assertThat(result).hasSize(1);
        }

        @Test
        @DisplayName("특수: status=COMPLETED 필터 입력 — DB 조회 없이 빈 리스트 즉시 반환")
        void completedFilter_returnsEmpty() throws IllegalAccessException {
            List<AgencyAsRequestListResponse> result =
                    agencyAsRequestService.searchAsRequests(agencyUserDetails, null, "COMPLETED");

            assertThat(result).isEmpty();
            // DB 조회가 발생하면 안 됨
            org.mockito.Mockito.verifyNoInteractions(asRequestRepository);
        }

        @Test
        @DisplayName("실패: 유효하지 않은 status 문자열 — IllegalArgumentException")
        void invalidStatus_400() {
            assertThatThrownBy(() ->
                    agencyAsRequestService.searchAsRequests(agencyUserDetails, null, "INVALID_STATUS"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("유효하지 않은 상태 값입니다");
        }
    }

    // ─────────────────────────────────────────────
    //  getAsRequestDetail — 단건 상세 조회
    // ─────────────────────────────────────────────
    @Nested
    @DisplayName("getAsRequestDetail — 단건 상세 조회")
    class GetAsRequestDetail {

        @Test
        @DisplayName("성공: 본인 소속 대행사 요청 조회 — 상세 정보 반환")
        void success() throws IllegalAccessException {
            given(asRequestRepository.findDetailById(REQUEST_ID))
                    .willReturn(Optional.of(stubRequest));

            AgencyAsRequestDetailResponse response =
                    agencyAsRequestService.getAsRequestDetail(agencyUserDetails, REQUEST_ID);

            assertThat(response.requestId()).isEqualTo(REQUEST_ID);
            assertThat(response.status()).isEqualTo(AsStatus.ASSIGNED);
        }

        @Test
        @DisplayName("실패: 존재하지 않는 requestId — NoSuchElementException")
        void notFound() {
            given(asRequestRepository.findDetailById(REQUEST_ID)).willReturn(Optional.empty());

            assertThatThrownBy(() ->
                    agencyAsRequestService.getAsRequestDetail(agencyUserDetails, REQUEST_ID))
                    .isInstanceOf(NoSuchElementException.class)
                    .hasMessageContaining("해당 A/S 요청을 찾을 수 없습니다");
        }

        @Test
        @DisplayName("실패: 타 대행사 요청 접근 — IllegalAccessException")
        void wrongAgency() {
            // 다른 대행사 소속 요청
            Agencies otherAgency = buildAgency(99L);
            AsRequest otherReq = buildRequest(REQUEST_ID, AsStatus.ASSIGNED, otherAgency);
            given(asRequestRepository.findDetailById(REQUEST_ID)).willReturn(Optional.of(otherReq));

            assertThatThrownBy(() ->
                    agencyAsRequestService.getAsRequestDetail(agencyUserDetails, REQUEST_ID))
                    .isInstanceOf(IllegalAccessException.class)
                    .hasMessageContaining("본인 소속 대행사의 A/S 요청만 조회할 수 있습니다");
        }

        @Test
        @DisplayName("실패: 대행사 미배정 요청 접근 — IllegalAccessException")
        void agencyNotAssigned() {
            // agency가 null인 요청 (PENDING 상태 — 아직 대행사 미배정)
            AsRequest pendingReq = buildRequest(REQUEST_ID, AsStatus.PENDING, null);
            given(asRequestRepository.findDetailById(REQUEST_ID)).willReturn(Optional.of(pendingReq));

            assertThatThrownBy(() ->
                    agencyAsRequestService.getAsRequestDetail(agencyUserDetails, REQUEST_ID))
                    .isInstanceOf(IllegalAccessException.class);
        }
    }

    // ── 픽스처 헬퍼 ──────────────────────────────────────────────────

    private Agencies buildAgency(Long id) {
        Agencies a = Agencies.builder()
                .agencyName("테스트대행사").businessNumber("BIZ-" + id)
                .agencyAddress("서울").agencyFeeRate(5.0)
                .approvalStatus(com.careflow.common.enums.AgencyStatus.APPROVED).build();
        setId(a, "id", id);
        return a;
    }

    private User buildUser(Long id, Role role, Agencies agency) {
        User u = User.builder()
                .email(role.name().toLowerCase() + id + "@test.com")
                .passwordHash("hashed").name("테스트유저").phone("010-0000-0000")
                .role(role).agency(agency).build();
        setId(u, "id", id);
        return u;
    }

    private AsRequest buildRequest(Long id, AsStatus status, Agencies agency) {
        // 연관 객체 stub
        User customer = buildUser(200L, Role.CUSTOMER, null);
        ApplianceCategory rootCat = ApplianceCategory.createRoot("에어컨", 1);
        ApplianceCategory cat = ApplianceCategory.createChild("에어컨 소분류", rootCat, 1);

        Symptom symptom = Symptom.builder().category(cat)
                .symptomCode("COOLING_FAIL").symptomName("냉방 불량").build();
        setId(symptom, "id", 1L);

        Appliance appliance = Appliance.create(customer, cat, "삼성", "Q9000",
                "SN001", null, null, com.careflow.common.enums.RegisterMethod.MANUAL);
        setId(appliance, "id", 1L);

        Regions region = Regions.create("강남구", null, 1, 0);
        setId(region, "id", 1);

        AsRequest r = AsRequest.builder()
                .customer(customer).appliance(appliance).symptom(symptom)
                .visitRegion(region).visitAddressDetail("테헤란로 123")
                .scheduledDate(LocalDate.of(2026, 7, 1)).scheduledTime("10:00").build();
        setId(r, "id", id);

        if (agency != null) {
            r.processAssignment(agency); // AGENCY_RECEIVED -> ASSIGNED

            if (status == AsStatus.ACCEPTED || status == AsStatus.ENGINEER_DEPARTED ||
                    status == AsStatus.ENGINEER_ARRIVED || status == AsStatus.IN_PROGRESS ||
                    status == AsStatus.COMPLETED) {
                r.acceptAssignment();
            }
            if (status == AsStatus.ENGINEER_DEPARTED || status == AsStatus.ENGINEER_ARRIVED ||
                    status == AsStatus.IN_PROGRESS || status == AsStatus.COMPLETED) {
                r.depart();   // 💡 추가: 기사 출발
            }
            if (status == AsStatus.ENGINEER_ARRIVED || status == AsStatus.IN_PROGRESS ||
                    status == AsStatus.COMPLETED) {
                r.arrive();   // 💡 추가: 기사 도착
            }
            if (status == AsStatus.IN_PROGRESS || status == AsStatus.COMPLETED) {
                r.startWork(); // 💡 이제 에러 없이 작업 시작 가능!
            }
            if (status == AsStatus.COMPLETED) {
                r.completeWork();
            }
        } else {
            setId(r, "status", status); // agency가 없는 경우(PENDING 등) 리플렉션으로 강제 주입
        }

        return r;
    }

    /** 리플렉션으로 private final 필드에 테스트용 ID 주입 */
    private void setId(Object target, String fieldName, Object value) {
        try {
            java.lang.reflect.Field f = findField(target.getClass(), fieldName);
            f.setAccessible(true);
            f.set(target, value);
        } catch (Exception e) {
            throw new RuntimeException("ID 주입 실패: " + fieldName, e);
        }
    }

    private java.lang.reflect.Field findField(Class<?> clazz, String name) {
        Class<?> c = clazz;
        while (c != null) {
            try { return c.getDeclaredField(name); } catch (NoSuchFieldException ignored) {}
            c = c.getSuperclass();
        }
        throw new RuntimeException("필드를 찾을 수 없습니다: " + name);
    }
}
