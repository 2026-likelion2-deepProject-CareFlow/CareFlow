package com.careflow.assignment.service;

import com.careflow.agency.entity.Agencies;
import com.careflow.as_request.entity.AsRequest;
import com.careflow.assignment.dto.AssignmentCancelResponse;
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

import java.util.NoSuchElementException;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("AssignmentCancelService 단위 테스트 (Mock 기반)")
class AssignmentCancelServiceTest {

    @InjectMocks
    private AssignmentCancelService assignmentCancelService;

    @Mock
    private AsAssignmentRepository asAssignmentRepository;

    private static final Long ASSIGNMENT_ID = 1L;
    private static final Long AGENCY_ID     = 10L;
    private static final Long REQUEST_ID    = 100L;

    // 공통 mock 객체
    private CustomUserDetails agencyUser;
    private CustomUserDetails customerUser;
    private AsAssignment assignment;
    private Agencies agency;
    private AsRequest asRequest;

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
        given(assignment.getStatus()).willReturn("WAITING");
    }

    @Nested
    @DisplayName("성공 케이스")
    class Success {

        @Test
        @DisplayName("WAITING 상태 배정 취소 → cancel() · revertToAgencyReceived() 호출, 응답 검증")
        void cancel_waiting_success() throws Exception {
            given(asAssignmentRepository.findDetailById(ASSIGNMENT_ID))
                    .willReturn(Optional.of(assignment));

            AssignmentCancelResponse response =
                    assignmentCancelService.cancel(ASSIGNMENT_ID, agencyUser);

            verify(assignment).cancel();
            verify(asRequest).revertToAgencyReceived();
            assertThat(response.assignmentId()).isEqualTo(ASSIGNMENT_ID);
            assertThat(response.requestId()).isEqualTo(REQUEST_ID);
            assertThat(response.cancelledStatus()).isEqualTo("REJECTED");
            assertThat(response.message()).isNotBlank();
        }

        @Test
        @DisplayName("ACCEPTED 상태 배정도 취소 가능 → 정상 처리")
        void cancel_accepted_success() throws Exception {
            given(assignment.getStatus()).willReturn("ACCEPTED");
            given(asAssignmentRepository.findDetailById(ASSIGNMENT_ID))
                    .willReturn(Optional.of(assignment));

            AssignmentCancelResponse response =
                    assignmentCancelService.cancel(ASSIGNMENT_ID, agencyUser);

            verify(assignment).cancel();
            assertThat(response.cancelledStatus()).isEqualTo("REJECTED");
        }
    }

    @Nested
    @DisplayName("실패 케이스")
    class Failure {

        @Test
        @DisplayName("AGENCY가 아닌 role → IllegalAccessException")
        void cancel_notAgencyRole_throws() {
            assertThatThrownBy(() -> assignmentCancelService.cancel(ASSIGNMENT_ID, customerUser))
                    .isInstanceOf(IllegalAccessException.class);
            verifyNoInteractions(asAssignmentRepository);
        }

        @Test
        @DisplayName("존재하지 않는 assignmentId → NoSuchElementException")
        void cancel_notFound_throws() {
            given(asAssignmentRepository.findDetailById(ASSIGNMENT_ID))
                    .willReturn(Optional.empty());

            assertThatThrownBy(() -> assignmentCancelService.cancel(ASSIGNMENT_ID, agencyUser))
                    .isInstanceOf(NoSuchElementException.class);
        }

        @Test
        @DisplayName("다른 대행사 소속 배정 → IllegalAccessException")
        void cancel_otherAgency_throws() {
            Agencies otherAgency = mock(Agencies.class);
            given(otherAgency.getId()).willReturn(999L);
            given(assignment.getAgency()).willReturn(otherAgency);
            given(asAssignmentRepository.findDetailById(ASSIGNMENT_ID))
                    .willReturn(Optional.of(assignment));

            assertThatThrownBy(() -> assignmentCancelService.cancel(ASSIGNMENT_ID, agencyUser))
                    .isInstanceOf(IllegalAccessException.class);
        }

        @Test
        @DisplayName("이미 REJECTED 상태 → cancel() 에서 IllegalStateException")
        void cancel_alreadyRejected_throws() {
            given(asAssignmentRepository.findDetailById(ASSIGNMENT_ID))
                    .willReturn(Optional.of(assignment));
            doThrow(new IllegalStateException("이미 완료되거나 취소된 배정입니다."))
                    .when(assignment).cancel();

            assertThatThrownBy(() -> assignmentCancelService.cancel(ASSIGNMENT_ID, agencyUser))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("이미 완료되거나 취소된");
        }

        @Test
        @DisplayName("COMPLETED 상태 배정 → cancel() 에서 IllegalStateException")
        void cancel_completed_throws() {
            given(asAssignmentRepository.findDetailById(ASSIGNMENT_ID))
                    .willReturn(Optional.of(assignment));
            doThrow(new IllegalStateException("이미 완료되거나 취소된 배정입니다."))
                    .when(assignment).cancel();

            assertThatThrownBy(() -> assignmentCancelService.cancel(ASSIGNMENT_ID, agencyUser))
                    .isInstanceOf(IllegalStateException.class);
        }
    }
}
