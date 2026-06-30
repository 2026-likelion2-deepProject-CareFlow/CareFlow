package com.careflow.notification.service;

import com.careflow.as_request.repository.AsRequestRepository;
import com.careflow.auth.security.CustomUserDetails;
import com.careflow.common.enums.Role;
import com.careflow.notification.dto.AgencyNoticeDetailResponse;
import com.careflow.notification.entity.Notification;
import com.careflow.notification.repository.NotificationRepository;
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

import java.time.LocalDateTime;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("AgencyNoticeDetailService 단위 테스트 (Mock 기반)")
class AgencyNoticeDetailServiceTest {

    @InjectMocks
    private AgencyNoticeDetailService agencyNoticeDetailService;

    @Mock private NotificationRepository notificationRepository;
    @Mock private UserRepository userRepository;
    @Mock private AsRequestRepository asRequestRepository;

    private static final Long AGENCY_ID = 10L;
    private static final Long NOTIFICATION_ID = 1L;
    private static final Long ENGINEER_ID = 20L;

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

    private Notification buildNotification(Long receiverId) {
        Notification n = mock(Notification.class);
        User receiver = mock(User.class);
        given(receiver.getId()).willReturn(receiverId);

        given(n.getId()).willReturn(NOTIFICATION_ID);
        given(n.getUser()).willReturn(receiver);
        given(n.getType()).willReturn("AS_STATUS");
        given(n.getTitle()).willReturn("A/S 상태가 변경되었습니다.");
        given(n.getBody()).willReturn("김민수 고객의 A/S가 완료 처리되었습니다.");
        given(n.getChannel()).willReturn("SSE");
        given(n.getCreatedAt()).willReturn(LocalDateTime.of(2024, 6, 1, 9, 0));
        return n;
    }

    @Nested
    @DisplayName("성공 케이스")
    class Success {

        @Test
        @DisplayName("정상 조회: 단건 알림을 배열로 반환한다")
        void 정상조회_단건알림_배열로반환() throws Exception {
            Notification notification = buildNotification(ENGINEER_ID);
            given(notificationRepository.findById(NOTIFICATION_ID)).willReturn(Optional.of(notification));
            given(userRepository.findIdsByAgency_IdAndRole(AGENCY_ID, Role.ENGINEER))
                    .willReturn(List.of(ENGINEER_ID));
            given(asRequestRepository.findDistinctCustomerIdsByAgencyId(AGENCY_ID))
                    .willReturn(List.of());

            List<AgencyNoticeDetailResponse> response =
                    agencyNoticeDetailService.getNoticeDetail(NOTIFICATION_ID, agencyUser);

            assertThat(response).hasSize(1);
            AgencyNoticeDetailResponse item = response.get(0);
            assertThat(item.notificationId()).isEqualTo(NOTIFICATION_ID);
            assertThat(item.type()).isEqualTo("AS_STATUS");
            assertThat(item.title()).isEqualTo("A/S 상태가 변경되었습니다.");
        }
    }

    @Nested
    @DisplayName("실패 케이스")
    class Failure {

        @Test
        @DisplayName("존재하지 않는 notificationId → NoSuchElementException")
        void 존재하지않는notificationId_NoSuchElementException() {
            given(notificationRepository.findById(NOTIFICATION_ID)).willReturn(Optional.empty());

            assertThatThrownBy(() -> agencyNoticeDetailService.getNoticeDetail(NOTIFICATION_ID, agencyUser))
                    .isInstanceOf(NoSuchElementException.class);
        }

        @Test
        @DisplayName("타 대행사 알림 조회 시도 → IllegalAccessException")
        void 타대행사알림조회시도_IllegalAccessException() {
            Notification notification = buildNotification(999L); // 수신 대상 범위에 없는 user_id
            given(notificationRepository.findById(NOTIFICATION_ID)).willReturn(Optional.of(notification));
            given(userRepository.findIdsByAgency_IdAndRole(AGENCY_ID, Role.ENGINEER))
                    .willReturn(List.of(ENGINEER_ID));
            given(asRequestRepository.findDistinctCustomerIdsByAgencyId(AGENCY_ID))
                    .willReturn(List.of());

            assertThatThrownBy(() -> agencyNoticeDetailService.getNoticeDetail(NOTIFICATION_ID, agencyUser))
                    .isInstanceOf(IllegalAccessException.class);
        }

        @Test
        @DisplayName("AGENCY가 아닌 role → IllegalAccessException")
        void AGENCY아닌_role_예외발생() {
            assertThatThrownBy(() -> agencyNoticeDetailService.getNoticeDetail(NOTIFICATION_ID, customerUser))
                    .isInstanceOf(IllegalAccessException.class);
            verifyNoInteractions(notificationRepository, userRepository, asRequestRepository);
        }
    }
}
