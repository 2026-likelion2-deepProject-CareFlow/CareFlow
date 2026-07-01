package com.careflow.engineer.service;

import com.careflow.as_status_log.repository.AsStatusLogRepository;
import com.careflow.assignment.repository.AsAssignmentRepository;
import com.careflow.engineer.domain.entity.EngineerProfile;
import com.careflow.engineer.domain.enums.SkillLevel;
import com.careflow.engineer.dto.EngineerDashboardResponse;
import com.careflow.engineer.repository.EngineerProfileRepository;
import com.careflow.notification.repository.NotificationRepository;
import com.careflow.settlement.repository.BankAccountRepository;
import com.careflow.settlement.repository.SettlementRepository;
import com.careflow.user.entity.User;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page; // 🌟 추가
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Collections;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
@DisplayName("EngineerDashboardService 단위 테스트")
class EngineerDashboardServiceTest {

    @InjectMocks private EngineerDashboardService engineerDashboardService;

    @Mock private AsAssignmentRepository asAssignmentRepository;
    @Mock private EngineerProfileRepository engineerProfileRepository;
    @Mock private SettlementRepository settlementRepository;
    @Mock private NotificationRepository notificationRepository;
    @Mock private AsStatusLogRepository asStatusLogRepository;
    @Mock private BankAccountRepository bankAccountRepository;

    @Test
    @DisplayName("성공: 대시보드 데이터를 정상적으로 조립하여 반환한다.")
    void getDashboardData_Success() {
        // Given
        Long engineerId = 1L;
        User user = User.builder().name("김기사").build();
        EngineerProfile profile = EngineerProfile.createInitial(user);
        ReflectionTestUtils.setField(profile, "skillLevel", SkillLevel.ADVANCED);

        given(engineerProfileRepository.findByUser_Id(engineerId)).willReturn(Optional.of(profile));
        given(asAssignmentRepository.findTodayAssignments(any(), any())).willReturn(Collections.emptyList());
        given(settlementRepository.sumExpectedEarningByEngineerIdAndDate(any(), any(), any())).willReturn(500000);

        // 🌟 수정: Collections.emptyList() 대신 Page.empty()를 사용!
        given(notificationRepository.findByUser_IdOrderByCreatedAtDesc(any(), any())).willReturn(Page.empty());

        // When
        EngineerDashboardResponse response = engineerDashboardService.getDashboardData(engineerId);

        // Then
        assertThat(response.getEngineerName()).isEqualTo("김기사");
        assertThat(response.getSkillLevel()).isEqualTo("ADVANCED");
        assertThat(response.getThisMonthExpectedEarning()).isEqualTo(500000);
        assertThat(response.getTodayExpectedCount()).isEqualTo(0);
        assertThat(response.getTodayCompletedCount()).isEqualTo(0);
    }
}