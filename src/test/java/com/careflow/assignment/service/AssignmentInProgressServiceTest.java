package com.careflow.assignment.service;

import com.careflow.agency.entity.Agencies;
import com.careflow.appliance.entity.Appliance;
import com.careflow.as_request.entity.AsRequest;
import com.careflow.as_status_log.entity.AsStatusLog;
import com.careflow.as_status_log.repository.AsStatusLogRepository;
import com.careflow.assignment.dto.AssignmentInProgressPageResponse;
import com.careflow.assignment.dto.AssignmentInProgressResponse;
import com.careflow.assignment.entity.AsAssignment;
import com.careflow.assignment.repository.AsAssignmentRepository;
import com.careflow.auth.security.CustomUserDetails;
import com.careflow.engineer.domain.entity.EngineerProfile;
import com.careflow.engineer.repository.EngineerProfileRepository;
import com.careflow.user.entity.User;
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

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("AssignmentInProgressService 단위 테스트 (Mock 기반)")
class AssignmentInProgressServiceTest {

    @InjectMocks
    private AssignmentInProgressService assignmentInProgressService;

    @Mock private AsAssignmentRepository   asAssignmentRepository;
    @Mock private AsStatusLogRepository    asStatusLogRepository;
    @Mock private EngineerProfileRepository engineerProfileRepository;

    private static final Long AGENCY_ID      = 10L;
    private static final Long REQUEST_ID     = 100L;
    private static final Long ASSIGNMENT_ID  = 1L;
    private static final Long ENGINEER_ID    = 20L;

    private CustomUserDetails agencyUser;
    private CustomUserDetails customerUser;

    @BeforeEach
    void setUp() {
        agencyUser   = mock(CustomUserDetails.class);
        customerUser = mock(CustomUserDetails.class);

        given(agencyUser.getRole()).willReturn("AGENCY");
        given(agencyUser.getAgencyId()).willReturn(AGENCY_ID);
        given(customerUser.getRole()).willReturn("CUSTOMER");
    }

    /**
     * 테스트용 AsAssignment mock — 공통 필드 세팅
     */
    private AsAssignment buildAssignmentMock() {
        AsAssignment a = mock(AsAssignment.class);
        Agencies agency    = mock(Agencies.class);
        User customer  = mock(User.class);
        User engineer  = mock(User.class);
        Appliance appliance = mock(Appliance.class);
        AsRequest req  = mock(AsRequest.class);

        given(agency.getId()).willReturn(AGENCY_ID);
        given(customer.getId()).willReturn(200L);
        given(customer.getName()).willReturn("테스트고객");
        given(customer.getPhone()).willReturn("010-1111-2222");

        given(engineer.getId()).willReturn(ENGINEER_ID);
        given(engineer.getName()).willReturn("테스트기사");
        given(engineer.getPhone()).willReturn("010-3333-4444");

        given(appliance.getBrand()).willReturn("삼성");
        given(appliance.getModelName()).willReturn("에어컨 Q9000");
        given(appliance.getSerialNumber()).willReturn("SN-001");

        given(req.getId()).willReturn(REQUEST_ID);
        given(req.getCustomer()).willReturn(customer);
        given(req.getAppliance()).willReturn(appliance);
        given(req.getScheduledDate()).willReturn(LocalDate.of(2026, 7, 1));
        given(req.getScheduledTime()).willReturn("10:00");
        given(req.getVisitAddressDetail()).willReturn("강남구 테헤란로 123");
        given(req.getUpdatedAt()).willReturn(LocalDateTime.of(2026, 7, 1, 10, 0));
        given(req.getCreatedAt()).willReturn(LocalDateTime.of(2026, 7, 1, 8, 0));

        given(a.getId()).willReturn(ASSIGNMENT_ID);
        given(a.getAgency()).willReturn(agency);
        given(a.getEngineer()).willReturn(engineer);
        given(a.getAsRequest()).willReturn(req);
        given(a.getStatus()).willReturn("ACCEPTED");
        given(a.getAssignMethod()).willReturn(com.careflow.common.enums.AssignType.MANUAL);
        given(a.getAcceptedAt()).willReturn(null);

        return a;
    }

    /** 기본 파라미터(모든 필터 null, page=0, size=10)로 서비스 호출 */
    private AssignmentInProgressPageResponse callDefault() throws Exception {
        return assignmentInProgressService.getInProgress(
                agencyUser, null, null, null, null, null, null, 0, 10);
    }

    @Nested
    @DisplayName("성공 케이스")
    class Success {

        @Test
        @DisplayName("ACCEPTED 배정 없음 → 빈 content, stats 모두 0 반환")
        void getInProgress_empty_returnsEmptyPage() throws Exception {
            given(asAssignmentRepository.findInProgressWithFilter(
                    eq(AGENCY_ID), isNull(), isNull(), isNull(), isNull()))
                    .willReturn(List.of());
            given(asAssignmentRepository.countCompletedByAgencyId(AGENCY_ID)).willReturn(0);

            AssignmentInProgressPageResponse result = callDefault();

            assertThat(result.content()).isEmpty();
            assertThat(result.totalElements()).isZero();
            assertThat(result.stats().totalCount()).isZero();
            assertThat(result.stats().completedCount()).isZero();
            verifyNoInteractions(engineerProfileRepository, asStatusLogRepository);
        }

        @Test
        @DisplayName("ACCEPTED 배정 1건 — 핵심 필드 매핑 및 createdAt 포함 검증")
        void getInProgress_oneAssignment_fieldMapping() throws Exception {
            AsAssignment a = buildAssignmentMock();
            given(asAssignmentRepository.findInProgressWithFilter(any(), any(), any(), any(), any()))
                    .willReturn(List.of(a));
            given(engineerProfileRepository.findByUser_IdIn(List.of(ENGINEER_ID)))
                    .willReturn(List.of());
            given(asStatusLogRepository.findByRequestIdsOrderByCreatedAtDesc(any()))
                    .willReturn(List.of());
            given(asAssignmentRepository.countCompletedByAgencyId(AGENCY_ID)).willReturn(0);

            AssignmentInProgressPageResponse result = callDefault();

            assertThat(result.content()).hasSize(1);
            AssignmentInProgressResponse r = result.content().get(0);
            assertThat(r.assignmentId()).isEqualTo(ASSIGNMENT_ID);
            assertThat(r.requestId()).isEqualTo(REQUEST_ID);
            assertThat(r.createdAt()).isEqualTo(LocalDateTime.of(2026, 7, 1, 8, 0));
            assertThat(r.customerName()).isEqualTo("테스트고객");
            assertThat(r.engineerName()).isEqualTo("테스트기사");
            assertThat(r.productName()).isEqualTo("삼성 에어컨 Q9000");
            assertThat(r.modelNo()).isEqualTo("SN-001");
            assertThat(r.logs()).isEmpty();
            assertThat(r.latestLogStatus()).isNull();
            assertThat(r.stepTimes()).containsEntry("ACCEPTED", null)
                                     .containsEntry("ENGINEER_DEPARTED", null);
        }

        @Test
        @DisplayName("기사 프로필 없음 → engineerRating·engineerImg null")
        void getInProgress_noProfile_nullRatingAndImg() throws Exception {
            AsAssignment a = buildAssignmentMock();
            given(asAssignmentRepository.findInProgressWithFilter(any(), any(), any(), any(), any()))
                    .willReturn(List.of(a));
            given(engineerProfileRepository.findByUser_IdIn(any())).willReturn(List.of());
            given(asStatusLogRepository.findByRequestIdsOrderByCreatedAtDesc(any())).willReturn(List.of());
            given(asAssignmentRepository.countCompletedByAgencyId(any())).willReturn(0);

            AssignmentInProgressPageResponse result = callDefault();
            AssignmentInProgressResponse r = result.content().get(0);

            assertThat(r.engineerRating()).isNull();
            assertThat(r.engineerImg()).isNull();
        }

        @Test
        @DisplayName("기사 프로필 있음 → engineerRating·engineerImg 매핑")
        void getInProgress_withProfile_ratingAndImgMapped() throws Exception {
            AsAssignment a = buildAssignmentMock();
            EngineerProfile profile = mock(EngineerProfile.class);
            User engineerUser = mock(User.class);
            given(engineerUser.getId()).willReturn(ENGINEER_ID);
            given(profile.getUser()).willReturn(engineerUser);
            given(profile.getAvgRating()).willReturn(new BigDecimal("4.5"));
            given(profile.getProfileImageUrl()).willReturn("https://cdn.example.com/img.jpg");

            given(asAssignmentRepository.findInProgressWithFilter(any(), any(), any(), any(), any()))
                    .willReturn(List.of(a));
            given(engineerProfileRepository.findByUser_IdIn(List.of(ENGINEER_ID)))
                    .willReturn(List.of(profile));
            given(asStatusLogRepository.findByRequestIdsOrderByCreatedAtDesc(any())).willReturn(List.of());
            given(asAssignmentRepository.countCompletedByAgencyId(any())).willReturn(0);

            AssignmentInProgressPageResponse result = callDefault();
            AssignmentInProgressResponse r = result.content().get(0);

            assertThat(r.engineerRating()).isEqualTo(4.5);
            assertThat(r.engineerImg()).isEqualTo("https://cdn.example.com/img.jpg");
        }

        @Test
        @DisplayName("로그 존재 → latestLogStatus·logs·stepTimes 매핑 정확")
        void getInProgress_withLogs_logFieldsMapped() throws Exception {
            AsAssignment a = buildAssignmentMock();
            AsStatusLog log = mock(AsStatusLog.class);
            AsRequest req = a.getAsRequest();
            given(log.getAsRequest()).willReturn(req);
            given(log.getToStatus()).willReturn("ENGINEER_DEPARTED");
            given(log.getMemo()).willReturn("출발");
            given(log.getCreatedAt()).willReturn(LocalDateTime.of(2026, 7, 1, 9, 30));

            given(asAssignmentRepository.findInProgressWithFilter(any(), any(), any(), any(), any()))
                    .willReturn(List.of(a));
            given(engineerProfileRepository.findByUser_IdIn(any())).willReturn(List.of());
            given(asStatusLogRepository.findByRequestIdsOrderByCreatedAtDesc(any()))
                    .willReturn(List.of(log));
            given(asAssignmentRepository.countCompletedByAgencyId(any())).willReturn(0);

            AssignmentInProgressPageResponse result = callDefault();
            AssignmentInProgressResponse r = result.content().get(0);

            assertThat(r.latestLogStatus()).isEqualTo("ENGINEER_DEPARTED");
            assertThat(r.logs()).hasSize(1);
            assertThat(r.logs().get(0).toStatus()).isEqualTo("ENGINEER_DEPARTED");
            assertThat(r.stepTimes()).containsEntry("ENGINEER_DEPARTED", "09:30");
            assertThat(r.stepTimes()).containsEntry("IN_PROGRESS", null);
        }

        @Test
        @DisplayName("latestLogStatus 필터 → 해당 상태만 content 반환")
        void getInProgress_latestLogStatusFilter_applied() throws Exception {
            // 배정 2건 세팅: 첫 번째는 ENGINEER_DEPARTED 로그, 두 번째는 로그 없음
            AsAssignment a1 = buildAssignmentMock();
            AsAssignment a2 = mock(AsAssignment.class);
            AsRequest req2 = mock(AsRequest.class);
            User customer2 = mock(User.class);
            User engineer2 = mock(User.class);
            Appliance appliance2 = mock(Appliance.class);

            given(customer2.getId()).willReturn(201L);
            given(customer2.getName()).willReturn("다른고객");
            given(customer2.getPhone()).willReturn("010-2222-3333");
            given(engineer2.getId()).willReturn(21L);
            given(engineer2.getName()).willReturn("다른기사");
            given(engineer2.getPhone()).willReturn("010-4444-5555");
            given(appliance2.getBrand()).willReturn("LG");
            given(appliance2.getModelName()).willReturn("냉장고");
            given(appliance2.getSerialNumber()).willReturn(null);
            given(req2.getId()).willReturn(101L);
            given(req2.getCustomer()).willReturn(customer2);
            given(req2.getAppliance()).willReturn(appliance2);
            given(req2.getScheduledDate()).willReturn(LocalDate.of(2026, 7, 1));
            given(req2.getScheduledTime()).willReturn("14:00");
            given(req2.getVisitAddressDetail()).willReturn("서초구 123");
            given(req2.getUpdatedAt()).willReturn(LocalDateTime.now());
            given(req2.getCreatedAt()).willReturn(LocalDateTime.now());
            given(a2.getId()).willReturn(2L);
            given(a2.getEngineer()).willReturn(engineer2);
            given(a2.getAsRequest()).willReturn(req2);
            given(a2.getStatus()).willReturn("ACCEPTED");
            given(a2.getAssignMethod()).willReturn(com.careflow.common.enums.AssignType.MANUAL);
            given(a2.getAcceptedAt()).willReturn(null);

            AsStatusLog log = mock(AsStatusLog.class);
            AsRequest req1 = a1.getAsRequest();
            given(log.getAsRequest()).willReturn(req1);
            given(log.getToStatus()).willReturn("ENGINEER_DEPARTED");
            given(log.getMemo()).willReturn("출발");
            given(log.getCreatedAt()).willReturn(LocalDateTime.of(2026, 7, 1, 9, 0));

            given(asAssignmentRepository.findInProgressWithFilter(any(), any(), any(), any(), any()))
                    .willReturn(List.of(a1, a2));
            given(engineerProfileRepository.findByUser_IdIn(any())).willReturn(List.of());
            given(asStatusLogRepository.findByRequestIdsOrderByCreatedAtDesc(any()))
                    .willReturn(List.of(log));
            given(asAssignmentRepository.countCompletedByAgencyId(any())).willReturn(0);

            AssignmentInProgressPageResponse result = assignmentInProgressService.getInProgress(
                    agencyUser, null, null, "ENGINEER_DEPARTED", null, null, null, 0, 10);

            // stats는 필터 전 전체 2건 기준
            assertThat(result.stats().totalCount()).isEqualTo(2);
            // content는 ENGINEER_DEPARTED만 필터된 1건
            assertThat(result.content()).hasSize(1);
            assertThat(result.content().get(0).latestLogStatus()).isEqualTo("ENGINEER_DEPARTED");
        }

        @Test
        @DisplayName("keyword 필터 → 고객명 부분 일치만 반환")
        void getInProgress_keywordFilter_customerNameMatch() throws Exception {
            AsAssignment a = buildAssignmentMock(); // 고객명: "테스트고객"
            given(asAssignmentRepository.findInProgressWithFilter(any(), any(), any(), any(), any()))
                    .willReturn(List.of(a));
            given(engineerProfileRepository.findByUser_IdIn(any())).willReturn(List.of());
            given(asStatusLogRepository.findByRequestIdsOrderByCreatedAtDesc(any())).willReturn(List.of());
            given(asAssignmentRepository.countCompletedByAgencyId(any())).willReturn(0);

            // 일치하는 keyword
            AssignmentInProgressPageResponse matched = assignmentInProgressService.getInProgress(
                    agencyUser, null, null, null, null, null, "테스트", 0, 10);
            assertThat(matched.content()).hasSize(1);

            // 일치하지 않는 keyword
            AssignmentInProgressPageResponse noMatch = assignmentInProgressService.getInProgress(
                    agencyUser, null, null, null, null, null, "없는고객", 0, 10);
            assertThat(noMatch.content()).isEmpty();
        }

        @Test
        @DisplayName("페이지네이션 — size=2, page=1, 총 3건 → content 1건·totalElements=3")
        void getInProgress_pagination_page1() throws Exception {
            // 배정 3건 생성 (buildAssignmentMock 재사용 불가하므로 직접 세팅)
            List<AsAssignment> assignments = buildThreeAssignmentMocks();

            given(asAssignmentRepository.findInProgressWithFilter(any(), any(), any(), any(), any()))
                    .willReturn(assignments);
            given(engineerProfileRepository.findByUser_IdIn(any())).willReturn(List.of());
            given(asStatusLogRepository.findByRequestIdsOrderByCreatedAtDesc(any())).willReturn(List.of());
            given(asAssignmentRepository.countCompletedByAgencyId(any())).willReturn(0);

            AssignmentInProgressPageResponse result = assignmentInProgressService.getInProgress(
                    agencyUser, null, null, null, null, null, null, 1, 2);

            assertThat(result.totalElements()).isEqualTo(3);
            assertThat(result.totalPages()).isEqualTo(2);
            assertThat(result.currentPage()).isEqualTo(1);
            assertThat(result.content()).hasSize(1);
        }

        @Test
        @DisplayName("stats — movingCount·inProgressCount 집계 정확")
        void getInProgress_stats_movingAndInProgress() throws Exception {
            // 배정 3건: ENGINEER_DEPARTED 1건, IN_PROGRESS 1건, 로그 없음 1건
            AsAssignment a1 = buildAssignmentMock();
            AsAssignment a2 = buildNamedAssignmentMock(2L, 102L, 22L);
            AsAssignment a3 = buildNamedAssignmentMock(3L, 103L, 23L);

            AsRequest req1 = a1.getAsRequest();
            AsRequest req2 = a2.getAsRequest();

            AsStatusLog log1 = buildLog(req1, "ENGINEER_DEPARTED", LocalDateTime.of(2026, 7, 1, 9, 0));
            AsStatusLog log2 = buildLog(req2, "IN_PROGRESS", LocalDateTime.of(2026, 7, 1, 10, 0));

            given(asAssignmentRepository.findInProgressWithFilter(any(), any(), any(), any(), any()))
                    .willReturn(List.of(a1, a2, a3));
            given(engineerProfileRepository.findByUser_IdIn(any())).willReturn(List.of());
            given(asStatusLogRepository.findByRequestIdsOrderByCreatedAtDesc(any()))
                    .willReturn(List.of(log1, log2));
            given(asAssignmentRepository.countCompletedByAgencyId(AGENCY_ID)).willReturn(5);

            AssignmentInProgressPageResponse result = callDefault();

            assertThat(result.stats().totalCount()).isEqualTo(3);
            assertThat(result.stats().movingCount()).isEqualTo(1);
            assertThat(result.stats().inProgressCount()).isEqualTo(1);
            assertThat(result.stats().completedCount()).isEqualTo(5);
        }
    }

    @Nested
    @DisplayName("실패 케이스")
    class Failure {

        @Test
        @DisplayName("AGENCY가 아닌 role → IllegalAccessException")
        void getInProgress_notAgencyRole_throws() {
            assertThatThrownBy(() -> assignmentInProgressService.getInProgress(
                    customerUser, null, null, null, null, null, null, 0, 10))
                    .isInstanceOf(IllegalAccessException.class);
            verifyNoInteractions(asAssignmentRepository);
        }
    }

    // ── 헬퍼 ────────────────────────────────────────────

    private List<AsAssignment> buildThreeAssignmentMocks() {
        return List.of(
                buildNamedAssignmentMock(1L, REQUEST_ID, ENGINEER_ID),
                buildNamedAssignmentMock(2L, 102L, 22L),
                buildNamedAssignmentMock(3L, 103L, 23L)
        );
    }

    private AsAssignment buildNamedAssignmentMock(Long assignId, Long reqId, Long engId) {
        AsAssignment a = mock(AsAssignment.class);
        User customer = mock(User.class);
        User engineer = mock(User.class);
        Appliance appliance = mock(Appliance.class);
        AsRequest req = mock(AsRequest.class);

        given(customer.getId()).willReturn(200L + engId);
        given(customer.getName()).willReturn("고객" + reqId);
        given(customer.getPhone()).willReturn("010-0000-0000");
        given(engineer.getId()).willReturn(engId);
        given(engineer.getName()).willReturn("기사" + engId);
        given(engineer.getPhone()).willReturn("010-1111-1111");
        given(appliance.getBrand()).willReturn("삼성");
        given(appliance.getModelName()).willReturn("TV");
        given(appliance.getSerialNumber()).willReturn(null);
        given(req.getId()).willReturn(reqId);
        given(req.getCustomer()).willReturn(customer);
        given(req.getAppliance()).willReturn(appliance);
        given(req.getScheduledDate()).willReturn(LocalDate.of(2026, 7, 1));
        given(req.getScheduledTime()).willReturn("10:00");
        given(req.getVisitAddressDetail()).willReturn("주소");
        given(req.getUpdatedAt()).willReturn(LocalDateTime.now());
        given(req.getCreatedAt()).willReturn(LocalDateTime.now());
        given(a.getId()).willReturn(assignId);
        given(a.getEngineer()).willReturn(engineer);
        given(a.getAsRequest()).willReturn(req);
        given(a.getStatus()).willReturn("ACCEPTED");
        given(a.getAssignMethod()).willReturn(com.careflow.common.enums.AssignType.MANUAL);
        given(a.getAcceptedAt()).willReturn(null);

        return a;
    }

    private AsStatusLog buildLog(AsRequest req, String toStatus, LocalDateTime createdAt) {
        AsStatusLog log = mock(AsStatusLog.class);
        given(log.getAsRequest()).willReturn(req);
        given(log.getToStatus()).willReturn(toStatus);
        given(log.getMemo()).willReturn(toStatus + " 메모");
        given(log.getCreatedAt()).willReturn(createdAt);
        return log;
    }
}
