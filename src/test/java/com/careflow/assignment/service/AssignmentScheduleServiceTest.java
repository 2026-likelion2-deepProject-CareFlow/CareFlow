package com.careflow.assignment.service;

import com.careflow.agency.entity.Agencies;
import com.careflow.as_request.entity.AsRequest;
import com.careflow.assignment.dto.AssignmentScheduleRequest;
import com.careflow.assignment.dto.AssignmentScheduleResponse;
import com.careflow.assignment.entity.AsAssignment;
import com.careflow.assignment.repository.AsAssignmentRepository;
import com.careflow.auth.security.CustomUserDetails;
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
import java.util.NoSuchElementException;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("AssignmentScheduleService 단위 테스트 (Mock 기반)")
class AssignmentScheduleServiceTest {

    @InjectMocks
    private AssignmentScheduleService assignmentScheduleService;

    @Mock
    private AsAssignmentRepository asAssignmentRepository;

    private static final Long ASSIGNMENT_ID = 1L;
    private static final Long AGENCY_ID     = 10L;
    private static final Long REQUEST_ID    = 100L;

    private CustomUserDetails agencyUser;
    private CustomUserDetails customerUser;
    private AsAssignment assignment;
    private Agencies agency;
    private AsRequest asRequest;

    private final AssignmentScheduleRequest scheduleRequest =
            new AssignmentScheduleRequest(LocalDate.of(2026, 8, 1), "14:00");

    @BeforeEach
    void setUp() {
        agencyUser   = mock(CustomUserDetails.class);
        customerUser = mock(CustomUserDetails.class);
        agency       = mock(Agencies.class);
        asRequest    = mock(AsRequest.class);
        assignment   = mock(AsAssignment.class);

        given(agencyUser.getRole()).willReturn("AGENCY");
        given(agencyUser.getAgencyId()).willReturn(AGENCY_ID);
        given(customerUser.getRole()).willReturn("CUSTOMER");

        given(agency.getId()).willReturn(AGENCY_ID);
        given(asRequest.getId()).willReturn(REQUEST_ID);
        given(assignment.getId()).willReturn(ASSIGNMENT_ID);
        given(assignment.getAgency()).willReturn(agency);
        given(assignment.getAsRequest()).willReturn(asRequest);
        given(asAssignmentRepository.findDetailById(ASSIGNMENT_ID))
                .willReturn(Optional.of(assignment));
    }

    @Nested
    @DisplayName("성공 케이스")
    class Success {

        @Test
        @DisplayName("WAITING 상태 → updateSchedule() 호출, 응답 날짜·시간 검증")
        void schedule_waiting_success() throws Exception {
            given(assignment.getStatus()).willReturn("WAITING");

            AssignmentScheduleResponse response = assignmentScheduleService
                    .updateSchedule(ASSIGNMENT_ID, scheduleRequest, agencyUser);

            verify(asRequest).updateSchedule(LocalDate.of(2026, 8, 1), "14:00");
            assertThat(response.assignmentId()).isEqualTo(ASSIGNMENT_ID);
            assertThat(response.requestId()).isEqualTo(REQUEST_ID);
            assertThat(response.scheduledDate()).isEqualTo(LocalDate.of(2026, 8, 1));
            assertThat(response.scheduledTime()).isEqualTo("14:00");
        }

        @Test
        @DisplayName("ACCEPTED 상태도 일정 변경 가능 → 정상 처리")
        void schedule_accepted_success() throws Exception {
            given(assignment.getStatus()).willReturn("ACCEPTED");

            AssignmentScheduleResponse response = assignmentScheduleService
                    .updateSchedule(ASSIGNMENT_ID, scheduleRequest, agencyUser);

            verify(asRequest).updateSchedule(any(), any());
            assertThat(response.scheduledDate()).isEqualTo(LocalDate.of(2026, 8, 1));
        }
    }

    @Nested
    @DisplayName("실패 케이스")
    class Failure {

        @Test
        @DisplayName("AGENCY가 아닌 role → IllegalAccessException")
        void schedule_notAgencyRole_throws() {
            assertThatThrownBy(() -> assignmentScheduleService
                    .updateSchedule(ASSIGNMENT_ID, scheduleRequest, customerUser))
                    .isInstanceOf(IllegalAccessException.class);
            verifyNoInteractions(asAssignmentRepository);
        }

        @Test
        @DisplayName("존재하지 않는 assignmentId → NoSuchElementException")
        void schedule_notFound_throws() {
            given(asAssignmentRepository.findDetailById(ASSIGNMENT_ID))
                    .willReturn(Optional.empty());

            assertThatThrownBy(() -> assignmentScheduleService
                    .updateSchedule(ASSIGNMENT_ID, scheduleRequest, agencyUser))
                    .isInstanceOf(NoSuchElementException.class);
        }

        @Test
        @DisplayName("다른 대행사 소속 배정 → IllegalAccessException")
        void schedule_otherAgency_throws() {
            Agencies otherAgency = mock(Agencies.class);
            given(otherAgency.getId()).willReturn(999L);
            given(assignment.getAgency()).willReturn(otherAgency);
            given(assignment.getStatus()).willReturn("WAITING");

            assertThatThrownBy(() -> assignmentScheduleService
                    .updateSchedule(ASSIGNMENT_ID, scheduleRequest, agencyUser))
                    .isInstanceOf(IllegalAccessException.class);
        }

        @Test
        @DisplayName("REJECTED 상태 → IllegalStateException")
        void schedule_rejected_throws() {
            given(assignment.getStatus()).willReturn("REJECTED");

            assertThatThrownBy(() -> assignmentScheduleService
                    .updateSchedule(ASSIGNMENT_ID, scheduleRequest, agencyUser))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("완료되거나 취소된");
        }

        @Test
        @DisplayName("COMPLETED 상태 → IllegalStateException")
        void schedule_completed_throws() {
            given(assignment.getStatus()).willReturn("COMPLETED");

            assertThatThrownBy(() -> assignmentScheduleService
                    .updateSchedule(ASSIGNMENT_ID, scheduleRequest, agencyUser))
                    .isInstanceOf(IllegalStateException.class);
        }
    }
}
