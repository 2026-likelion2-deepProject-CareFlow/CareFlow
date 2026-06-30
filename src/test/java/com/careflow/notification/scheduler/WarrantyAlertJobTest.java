package com.careflow.notification.scheduler;

import com.careflow.appliance.entity.Appliance;
import com.careflow.appliance.repository.ApplianceRepository;
import com.careflow.notification.service.NotificationService;
import com.careflow.user.entity.User;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.quartz.JobExecutionContext;

import java.time.LocalDate;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("WarrantyAlertJob 단위 테스트 (Mock 기반)")
class WarrantyAlertJobTest {

    @InjectMocks
    private WarrantyAlertJob warrantyAlertJob;

    @Mock private ApplianceRepository applianceRepository;
    @Mock private NotificationService notificationService;
    @Mock private JobExecutionContext context;

    @Test
    @DisplayName("성공: 30일 뒤 만료 가전이 있으면 알림을 발송한다")
    void execute_Success() throws Exception {
        // Given
        LocalDate targetDate = LocalDate.now().plusDays(30);

        Appliance appliance1 = mock(Appliance.class);
        User user1 = mock(User.class);
        given(appliance1.getUser()).willReturn(user1);
        given(appliance1.getBrand()).willReturn("삼성");
        given(appliance1.getModelName()).willReturn("TV");

        Appliance appliance2 = mock(Appliance.class);
        User user2 = mock(User.class);
        given(appliance2.getUser()).willReturn(user2);
        given(appliance2.getBrand()).willReturn("LG");
        given(appliance2.getModelName()).willReturn("에어컨");

        given(applianceRepository.findByWarrantyEndDateWithUser(targetDate))
                .willReturn(List.of(appliance1, appliance2));

        // When
        warrantyAlertJob.execute(context);

        // Then
        // 2건의 가전이 있으므로, send 메서드가 정확히 2번 호출되어야 함
        verify(notificationService, times(2)).send(any(User.class), eq("WARRANTY"), anyString(), anyString());
    }

    @Test
    @DisplayName("성공: 발송 대상이 없으면 send 메서드를 호출하지 않고 종료한다")
    void execute_EmptyList() throws Exception {
        // Given
        given(applianceRepository.findByWarrantyEndDateWithUser(any(LocalDate.class)))
                .willReturn(List.of());

        // When
        warrantyAlertJob.execute(context);

        // Then
        verify(notificationService, never()).send(any(), any(), any(), any());
    }
}