package com.careflow.assignment.service;

import com.careflow.agency.entity.Agencies;
import com.careflow.appliance.entity.Appliance;
import com.careflow.as_request.entity.AsRequest;
import com.careflow.as_status_log.entity.AsStatusLog;
import com.careflow.as_status_log.repository.AsStatusLogRepository;
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

        given(req.getId()).willReturn(REQUEST_ID);
        given(req.getCustomer()).willReturn(customer);
        given(req.getAppliance()).willReturn(appliance);
        given(req.getScheduledDate()).willReturn(LocalDate.of(2026, 7, 1));
        given(req.getScheduledTime()).willReturn("10:00");
        given(req.getVisitAddressDetail()).willReturn("강남구 테헤란로 123");
        given(req.getUpdatedAt()).willReturn(LocalDateTime.now());

        given(a.getId()).willReturn(ASSIGNMENT_ID);
        given(a.getAgency()).willReturn(agency);
        given(a.getEngineer()).willReturn(engineer);
        given(a.getAsRequest()).willReturn(req);
        given(a.getStatus()).willReturn("ACCEPTED");
        given(a.getAssignMethod()).willReturn(com.careflow.common.enums.AssignType.MANUAL);
        given(a.getAcceptedAt()).willReturn(null);

        return a;
    }

    @Nested
    @DisplayName("성공 케이스")
    class Success {

        @Test
        @DisplayName("ACCEPTED 배정 없음 → 빈 리스트 반환")
        void getInProgress_empty() throws Exception {
            given(asAssignmentRepository.findInProgressByAgencyId(AGENCY_ID))
                    .willReturn(List.of());

            List<AssignmentInProgressResponse> result =
                    assignmentInProgressService.getInProgress(agencyUser);

            assertThat(result).isEmpty();
            verifyNoInteractions(engineerProfileRepository, asStatusLogRepository);
        }

        @Test
        @DisplayName("ACCEPTED 배정 1건 + 프로필·로그 없음 → 기본 필드 매핑, stepTimes 모두 null")
        void getInProgress_oneAssignment_noProfile_noLogs() throws Exception {
            AsAssignment a = buildAssignmentMock();
            given(asAssignmentRepository.findInProgressByAgencyId(AGENCY_ID))
                    .willReturn(List.of(a));
            given(engineerProfileRepository.findByUser_IdIn(List.of(ENGINEER_ID)))
                    .willReturn(List.of());
            given(asStatusLogRepository.findAllByAgencyIdOrderByCreatedAtDesc(AGENCY_ID))
                    .willReturn(List.of());

            List<AssignmentInProgressResponse> result =
                    assignmentInProgressService.getInProgress(agencyUser);

            assertThat(result).hasSize(1);
            AssignmentInProgressResponse r = result.get(0);
            assertThat(r.assignmentId()).isEqualTo(ASSIGNMENT_ID);
            assertThat(r.requestId()).isEqualTo(REQUEST_ID);
            assertThat(r.customerName()).isEqualTo("테스트고객");
            assertThat(r.engineerName()).isEqualTo("테스트기사");
            assertThat(r.productName()).isEqualTo("삼성 에어컨 Q9000");
            assertThat(r.logs()).isEmpty();
            assertThat(r.latestLogStatus()).isNull();
            assertThat(r.stepTimes()).containsEntry("ACCEPTED", null)
                                     .containsEntry("ENGINEER_DEPARTED", null);
        }

        @Test
        @DisplayName("기사 프로필 존재 → engineerRating·engineerImg 매핑")
        void getInProgress_withProfile() throws Exception {
            AsAssignment a = buildAssignmentMock();
            EngineerProfile profile = mock(EngineerProfile.class);
            User engineerUser = mock(User.class);
            given(engineerUser.getId()).willReturn(ENGINEER_ID);
            given(profile.getUser()).willReturn(engineerUser);
            given(profile.getAvgRating()).willReturn(new BigDecimal("4.5"));
            given(profile.getProfileImageUrl()).willReturn("https://cdn.example.com/img.jpg");

            given(asAssignmentRepository.findInProgressByAgencyId(AGENCY_ID))
                    .willReturn(List.of(a));
            given(engineerProfileRepository.findByUser_IdIn(List.of(ENGINEER_ID)))
                    .willReturn(List.of(profile));
            given(asStatusLogRepository.findAllByAgencyIdOrderByCreatedAtDesc(AGENCY_ID))
                    .willReturn(List.of());

            List<AssignmentInProgressResponse> result =
                    assignmentInProgressService.getInProgress(agencyUser);

            AssignmentInProgressResponse r = result.get(0);
            assertThat(r.engineerRating()).isEqualTo(4.5);
            assertThat(r.engineerImg()).isEqualTo("https://cdn.example.com/img.jpg");
        }

        @Test
        @DisplayName("상태 로그 존재 → latestLogStatus·logs 매핑")
        void getInProgress_withLogs() throws Exception {
            AsAssignment a = buildAssignmentMock();
            AsStatusLog log = mock(AsStatusLog.class);
            AsRequest req = a.getAsRequest();
            given(log.getAsRequest()).willReturn(req);
            given(log.getToStatus()).willReturn("ENGINEER_DEPARTED");
            given(log.getMemo()).willReturn("출발");
            given(log.getCreatedAt()).willReturn(LocalDateTime.of(2026, 7, 1, 9, 30));

            given(asAssignmentRepository.findInProgressByAgencyId(AGENCY_ID))
                    .willReturn(List.of(a));
            given(engineerProfileRepository.findByUser_IdIn(List.of(ENGINEER_ID)))
                    .willReturn(List.of());
            given(asStatusLogRepository.findAllByAgencyIdOrderByCreatedAtDesc(AGENCY_ID))
                    .willReturn(List.of(log));

            List<AssignmentInProgressResponse> result =
                    assignmentInProgressService.getInProgress(agencyUser);

            AssignmentInProgressResponse r = result.get(0);
            assertThat(r.latestLogStatus()).isEqualTo("ENGINEER_DEPARTED");
            assertThat(r.logs()).hasSize(1);
            assertThat(r.logs().get(0).toStatus()).isEqualTo("ENGINEER_DEPARTED");
            assertThat(r.stepTimes()).containsEntry("ENGINEER_DEPARTED", "09:30");
        }
    }

    @Nested
    @DisplayName("실패 케이스")
    class Failure {

        @Test
        @DisplayName("AGENCY가 아닌 role → IllegalAccessException")
        void getInProgress_notAgencyRole_throws() {
            assertThatThrownBy(() -> assignmentInProgressService.getInProgress(customerUser))
                    .isInstanceOf(IllegalAccessException.class);
            verifyNoInteractions(asAssignmentRepository);
        }
    }
}
