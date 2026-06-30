package com.careflow.notification.scheduler;

import com.careflow.appliance.entity.Appliance;
import com.careflow.appliance.entity.ConsumableAlert;
import com.careflow.appliance.repository.ConsumableAlertRepository;
import com.careflow.notification.service.NotificationService;
import com.careflow.user.entity.User;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.quartz.JobExecutionContext;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("ConsumableAlertJob 단위 테스트 (Mock 기반)")
class ConsumableAlertJobTest {

    @InjectMocks
    private ConsumableAlertJob consumableAlertJob;

    @Mock private ConsumableAlertRepository consumableAlertRepository;
    @Mock private NotificationService notificationService;
    @Mock private JobExecutionContext context;

    @Test
    @DisplayName("성공: 알림 발송 후 다음 알림 주기가 자동으로 연장된다")
    void execute_Success_UpdatesNextAlertDate() throws Exception {
        // Given
        LocalDate today = LocalDate.now();

        User user = mock(User.class);
        Appliance appliance = mock(Appliance.class);
        given(appliance.getModelName()).willReturn("공기청정기");

        // 상태 변경(도메인 로직)을 검증해야 하므로 Alert 객체는 실제 인스턴스로 생성!
        ConsumableAlert alert = ConsumableAlert.builder()
                .appliance(appliance)
                .user(user)
                .consumableName("HEPA 필터")
                .cycleMonths(6) // 6개월 주기
                .nextAlertDate(today) // 알림 당일
                .build();

        ReflectionTestUtils.setField(alert, "lastChangedAt", today.minusMonths(6));

        given(consumableAlertRepository.findAlertsToNotify(today)).willReturn(List.of(alert));

        // When
        consumableAlertJob.execute(context);

        // Then
        // 1. 알림이 발송되었는지 검증
        verify(notificationService, times(1))
                .send(eq(user), eq("CONSUMABLE"), anyString(), anyString());

        // 2. ★ 핵심 검증: 다음 알림 예정일이 주기(6개월)만큼 정확히 증가했는지 확인!
        assertThat(alert.getNextAlertDate()).isEqualTo(today.plusMonths(6));
        assertThat(alert.getLastChangedAt()).isEqualTo(today);
    }
}