package com.careflow.assignment.service;

import com.careflow.agency.entity.Agencies;
import com.careflow.as_request.entity.AsRequest;
import com.careflow.assignment.dto.AssignmentRejectRequest;
import com.careflow.assignment.entity.AsAssignment;
import com.careflow.assignment.repository.AsAssignmentRepository;
import com.careflow.common.enums.AssignType;
import com.careflow.user.entity.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
@DisplayName("EngineerAssignmentService 단위 테스트")
class EngineerAssignmentServiceTest {

    @InjectMocks
    private EngineerAssignmentService engineerAssignmentService;

    @Mock
    private AsAssignmentRepository asAssignmentRepository;

    private User engineer;
    private AsRequest asRequest;
    private AsAssignment assignment;

    private static final Long ENGINEER_ID = 1L;
    private static final Long ASSIGNMENT_ID = 100L;

    @BeforeEach
    void setUp() {
        engineer = User.builder().name("김기사").build();
        ReflectionTestUtils.setField(engineer, "id", ENGINEER_ID);

        asRequest = AsRequest.builder().build(); // 내부적으로 PENDING 상태
        asRequest.processAssignment(Agencies.builder().build()); // ASSIGNED 상태로 세팅

        assignment = AsAssignment.create(asRequest, engineer, Agencies.builder().build(), AssignType.AUTO);
        ReflectionTestUtils.setField(assignment, "id", ASSIGNMENT_ID);
    }

    @Test
    @DisplayName("성공: 대기 중인 배정을 수락하면 ACCEPTED 상태로 변경된다.")
    void acceptAssignment_Success() {
        // Given
        given(asAssignmentRepository.findById(ASSIGNMENT_ID)).willReturn(Optional.of(assignment));

        // When
        engineerAssignmentService.acceptAssignment(ENGINEER_ID, ASSIGNMENT_ID);

        // Then
        assertThat(assignment.getStatus()).isEqualTo("ACCEPTED");
        assertThat(assignment.getAcceptedAt()).isNotNull();
    }

    @Test
    @DisplayName("성공: 대기 중인 배정을 거절하면 REJECTED 상태가 되고, 요청은 대행사 대기로 원복된다.")
    void rejectAssignment_Success() {
        // Given
        given(asAssignmentRepository.findById(ASSIGNMENT_ID)).willReturn(Optional.of(assignment));
        AssignmentRejectRequest request = new AssignmentRejectRequest("거리가 너무 멉니다.");

        // When
        engineerAssignmentService.rejectAssignment(ENGINEER_ID, ASSIGNMENT_ID, request);

        // Then
        assertThat(assignment.getStatus()).isEqualTo("REJECTED");
        assertThat(assignment.getRejectReason()).isEqualTo("거리가 너무 멉니다.");
        assertThat(asRequest.getStatus().name()).isEqualTo("AGENCY_RECEIVED"); // 원복 확인
    }

    @Test
    @DisplayName("실패: 본인에게 배정된 건이 아니면 예외가 발생한다.")
    void fail_NotOwnedAssignment() {
        // Given
        Long otherEngineerId = 999L;
        given(asAssignmentRepository.findById(ASSIGNMENT_ID)).willReturn(Optional.of(assignment));

        // When & Then
        assertThatThrownBy(() -> engineerAssignmentService.acceptAssignment(otherEngineerId, ASSIGNMENT_ID))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("본인에게 배정된 건만 처리할 수 있습니다.");
    }
}