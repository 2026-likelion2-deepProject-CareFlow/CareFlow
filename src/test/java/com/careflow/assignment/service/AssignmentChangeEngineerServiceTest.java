package com.careflow.assignment.service;

import com.careflow.agency.entity.Agencies;
import com.careflow.appliance.entity.Appliance;
import com.careflow.as_request.entity.AsRequest;
import com.careflow.assignment.dto.AssignmentChangeEngineerRequest;
import com.careflow.assignment.dto.AssignmentChangeEngineerResponse;
import com.careflow.assignment.entity.AsAssignment;
import com.careflow.assignment.repository.AsAssignmentRepository;
import com.careflow.auth.security.CustomUserDetails;
import com.careflow.user.entity.User;
import com.careflow.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import com.careflow.notification.repository.NotificationRepository;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.LocalDateTime;
import java.util.NoSuchElementException;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("AssignmentChangeEngineerService 단위 테스트 (Mock 기반)")
class AssignmentChangeEngineerServiceTest {

    @InjectMocks
    private AssignmentChangeEngineerService assignmentChangeEngineerService;

    @Mock private AsAssignmentRepository asAssignmentRepository;
    @Mock private UserRepository userRepository;
    @Mock private NotificationRepository notificationRepository;

    private static final Long ASSIGNMENT_ID  = 1L;
    private static final Long AGENCY_ID      = 10L;
    private static final Long REQUEST_ID     = 100L;
    private static final Long OLD_ENGINEER_ID = 20L;
    private static final Long NEW_ENGINEER_ID = 21L;
    private static final Long NEW_ASSIGNMENT_ID = 2L;

    private CustomUserDetails agencyUser;
    private CustomUserDetails customerUser;
    private AsAssignment existing;
    private AsAssignment newAssignment;
    private Agencies agency;
    private AsRequest asRequest;
    private User newEngineer;
    private Appliance appliance;
    private User customer;
    private AssignmentChangeEngineerRequest changeRequest;

    @BeforeEach
    void setUp() {
        agencyUser   = mock(CustomUserDetails.class);
        customerUser = mock(CustomUserDetails.class);
        agency       = mock(Agencies.class);
        asRequest    = mock(AsRequest.class);
        existing     = mock(AsAssignment.class);
        newAssignment = mock(AsAssignment.class);
        newEngineer  = mock(User.class);
        appliance    = mock(Appliance.class);
        customer     = mock(User.class);

        changeRequest = new AssignmentChangeEngineerRequest(ASSIGNMENT_ID, NEW_ENGINEER_ID);

        given(agencyUser.getRole()).willReturn("AGENCY");
        given(agencyUser.getAgencyId()).willReturn(AGENCY_ID);
        given(customerUser.getRole()).willReturn("CUSTOMER");

        given(agency.getId()).willReturn(AGENCY_ID);
        given(asRequest.getId()).willReturn(REQUEST_ID);
        // [신규 (2)(3)] changeEngineer 에서 새 기사 배정 알림 문구 생성 시 참조하는 필드 스텁
        given(appliance.getBrand()).willReturn("삼성");
        given(appliance.getModelName()).willReturn("비스포크 냉장고");
        given(customer.getName()).willReturn("홍길동");
        given(asRequest.getAppliance()).willReturn(appliance);
        given(asRequest.getCustomer()).willReturn(customer);
        given(asRequest.getScheduledDate()).willReturn(java.time.LocalDate.of(2026, 7, 1));
        given(asRequest.getScheduledTime()).willReturn("10:00");

        given(existing.getId()).willReturn(ASSIGNMENT_ID);
        given(existing.getAgency()).willReturn(agency);
        given(existing.getAsRequest()).willReturn(asRequest);
        given(existing.getStatus()).willReturn("WAITING");

        given(newEngineer.getId()).willReturn(NEW_ENGINEER_ID);
        given(newEngineer.getName()).willReturn("박민수");

        given(newAssignment.getId()).willReturn(NEW_ASSIGNMENT_ID);
        given(newAssignment.getAsRequest()).willReturn(asRequest);
        given(newAssignment.getStatus()).willReturn("WAITING");
        given(newAssignment.getAssignedAt()).willReturn(LocalDateTime.now());
    }

    @Nested
    @DisplayName("성공 케이스")
    class Success {

        @Test
        @DisplayName("WAITING 배정 기사 변경 → 기존 cancel(), 새 배정 저장, 응답 검증")
        void changeEngineer_success() throws Exception {
            given(asAssignmentRepository.findById(ASSIGNMENT_ID)).willReturn(Optional.of(existing));
            given(userRepository.findById(NEW_ENGINEER_ID)).willReturn(Optional.of(newEngineer));
            given(asAssignmentRepository.save(any(AsAssignment.class))).willReturn(newAssignment);

            AssignmentChangeEngineerResponse response =
                    assignmentChangeEngineerService.changeEngineer(changeRequest, agencyUser);

            verify(existing).cancel();
            verify(asAssignmentRepository).save(any(AsAssignment.class));
            assertThat(response.newAssignmentId()).isEqualTo(NEW_ASSIGNMENT_ID);
            assertThat(response.requestId()).isEqualTo(REQUEST_ID);
            assertThat(response.newEngineerId()).isEqualTo(NEW_ENGINEER_ID);
            assertThat(response.newEngineerName()).isEqualTo("박민수");
            assertThat(response.assignmentStatus()).isEqualTo("WAITING");
            assertThat(response.assignedAt()).isNotNull();
        }

        @Test
        @DisplayName("REJECTED 배정 기사 변경 → cancel() 재호출 없이 새 배정만 생성 (재배차 알림 수동 배정)")
        void changeEngineer_rejectedStatus_success() throws Exception {
            given(existing.getStatus()).willReturn("REJECTED");
            given(asAssignmentRepository.findById(ASSIGNMENT_ID)).willReturn(Optional.of(existing));
            given(userRepository.findById(NEW_ENGINEER_ID)).willReturn(Optional.of(newEngineer));
            given(asAssignmentRepository.save(any(AsAssignment.class))).willReturn(newAssignment);

            AssignmentChangeEngineerResponse response =
                    assignmentChangeEngineerService.changeEngineer(changeRequest, agencyUser);

            // 이미 REJECTED인 배정은 cancel()을 다시 호출하면 안 됨 (AsAssignment.cancel()이 재호출 시 예외를 던지기 때문)
            verify(existing, never()).cancel();
            verify(asAssignmentRepository).save(any(AsAssignment.class));
            assertThat(response.newAssignmentId()).isEqualTo(NEW_ASSIGNMENT_ID);
            assertThat(response.assignmentStatus()).isEqualTo("WAITING");
        }
    }

    @Nested
    @DisplayName("실패 케이스")
    class Failure {

        @Test
        @DisplayName("AGENCY가 아닌 role → IllegalAccessException")
        void changeEngineer_notAgencyRole_throws() {
            assertThatThrownBy(() ->
                    assignmentChangeEngineerService.changeEngineer(changeRequest, customerUser))
                    .isInstanceOf(IllegalAccessException.class);
            verifyNoInteractions(asAssignmentRepository);
        }

        @Test
        @DisplayName("존재하지 않는 assignmentId → NoSuchElementException")
        void changeEngineer_notFound_throws() {
            given(asAssignmentRepository.findById(ASSIGNMENT_ID)).willReturn(Optional.empty());

            assertThatThrownBy(() ->
                    assignmentChangeEngineerService.changeEngineer(changeRequest, agencyUser))
                    .isInstanceOf(NoSuchElementException.class);
        }

        @Test
        @DisplayName("다른 대행사 소속 배정 → IllegalAccessException")
        void changeEngineer_otherAgency_throws() {
            Agencies other = mock(Agencies.class);
            given(other.getId()).willReturn(999L);
            given(existing.getAgency()).willReturn(other);
            given(asAssignmentRepository.findById(ASSIGNMENT_ID)).willReturn(Optional.of(existing));

            assertThatThrownBy(() ->
                    assignmentChangeEngineerService.changeEngineer(changeRequest, agencyUser))
                    .isInstanceOf(IllegalAccessException.class);
        }

        @Test
        @DisplayName("ACCEPTED 상태 배정은 기사 변경 불가 → IllegalStateException")
        void changeEngineer_acceptedStatus_throws() {
            given(existing.getStatus()).willReturn("ACCEPTED");
            given(asAssignmentRepository.findById(ASSIGNMENT_ID)).willReturn(Optional.of(existing));

            assertThatThrownBy(() ->
                    assignmentChangeEngineerService.changeEngineer(changeRequest, agencyUser))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("수락 대기");
        }

        @Test
        @DisplayName("존재하지 않는 newEngineerId → NoSuchElementException")
        void changeEngineer_engineerNotFound_throws() {
            given(asAssignmentRepository.findById(ASSIGNMENT_ID)).willReturn(Optional.of(existing));
            given(userRepository.findById(NEW_ENGINEER_ID)).willReturn(Optional.empty());

            assertThatThrownBy(() ->
                    assignmentChangeEngineerService.changeEngineer(changeRequest, agencyUser))
                    .isInstanceOf(NoSuchElementException.class)
                    .hasMessageContaining("수리 기사");
        }
    }
}
