package com.careflow.notification.service;

import com.careflow.as_request.repository.AsRequestRepository;
import com.careflow.auth.security.CustomUserDetails;
import com.careflow.common.enums.Role;
import com.careflow.notification.dto.AgencyNotificationResponse;
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
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.time.LocalDateTime;
import java.util.List;
import java.util.NoSuchElementException;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("AgencyNotificationService 단위 테스트 (Mock 기반)")
class AgencyNotificationServiceTest {

    @InjectMocks
    private AgencyNotificationService agencyNotificationService;

    @Mock private NotificationRepository notificationRepository;
    @Mock private UserRepository userRepository;
    @Mock private AsRequestRepository asRequestRepository;

    private static final Long AGENCY_ID = 10L;
    private static final Long ENGINEER_ID = 20L;
    private static final Long CUSTOMER_ID = 30L;

    private CustomUserDetails agencyUser;
    private CustomUserDetails customerUser;
    private Pageable pageable;

    @BeforeEach
    void setUp() {
        agencyUser   = mock(CustomUserDetails.class);
        customerUser = mock(CustomUserDetails.class);
        pageable     = PageRequest.of(0, 10);

        given(agencyUser.getRole()).willReturn("AGENCY");
        given(agencyUser.getAgencyId()).willReturn(AGENCY_ID);
        given(customerUser.getRole()).willReturn("CUSTOMER");
    }

    private Notification buildNotification(String type, boolean isRead, LocalDateTime createdAt) {
        Notification n = mock(Notification.class);
        given(n.getId()).willReturn(1L);
        given(n.getType()).willReturn(type);
        given(n.getTitle()).willReturn("배정 수락 알림");
        given(n.getBody()).willReturn("AS-001 | 김민지 고객");
        given(n.getChannel()).willReturn("SSE");
        given(n.getCreatedAt()).willReturn(createdAt);
        given(n.isRead()).willReturn(isRead);
        return n;
    }

    // markAsRead 테스트 전용 — 특정 수신자(userId)에게 발송된 것으로 stub된 알림 mock 생성
    private Notification buildNotificationOf(Long recipientUserId) {
        Notification n = mock(Notification.class);
        User recipient = mock(User.class);
        given(recipient.getId()).willReturn(recipientUserId);
        given(n.getUser()).willReturn(recipient);
        return n;
    }

    @Nested
    @DisplayName("성공 케이스")
    class Success {

        @Test
        @DisplayName("기사·고객 알림 모두 합쳐서 반환")
        void 정상조회_기사와고객알림_합쳐서반환() throws Exception {
            given(userRepository.findIdsByAgency_IdAndRole(AGENCY_ID, Role.ENGINEER))
                    .willReturn(List.of(ENGINEER_ID));
            given(asRequestRepository.findDistinctCustomerIdsByAgencyId(AGENCY_ID))
                    .willReturn(List.of(CUSTOMER_ID));

            Notification n1 = buildNotification("AS_STATUS", false, LocalDateTime.now());
            Notification n2 = buildNotification("LMS", true, LocalDateTime.now().minusDays(1));

            given(notificationRepository.findByUser_IdInAndTypeOrderByCreatedAtDesc(
                    List.of(ENGINEER_ID, CUSTOMER_ID), null, pageable))
                    .willReturn(new PageImpl<>(List.of(n1, n2), pageable, 2));
            given(notificationRepository.countByUser_IdIn(anyList())).willReturn(2L);
            given(notificationRepository.countByUser_IdInAndIsReadFalse(anyList())).willReturn(1L);
            given(notificationRepository.countByUser_IdInAndCreatedAtBetween(anyList(), any(), any())).willReturn(1L);

            AgencyNotificationResponse response = agencyNotificationService.getNotifications(agencyUser, pageable, null);

            assertThat(response.content()).hasSize(2);
            assertThat(response.stats().totalCount()).isEqualTo(2);
            assertThat(response.totalElements()).isEqualTo(2);
        }

        @Test
        @DisplayName("수신 대상 없음 → repository 호출 없이 빈 결과 반환")
        void 수신대상없음_빈결과반환() throws Exception {
            given(userRepository.findIdsByAgency_IdAndRole(AGENCY_ID, Role.ENGINEER))
                    .willReturn(List.of());
            given(asRequestRepository.findDistinctCustomerIdsByAgencyId(AGENCY_ID))
                    .willReturn(List.of());

            AgencyNotificationResponse response = agencyNotificationService.getNotifications(agencyUser, pageable, null);

            assertThat(response.content()).isEmpty();
            assertThat(response.stats().totalCount()).isZero();
            assertThat(response.stats().unreadCount()).isZero();
            assertThat(response.stats().todayCount()).isZero();
            verifyNoInteractions(notificationRepository);
        }

        @Test
        @DisplayName("unreadCount는 is_read=false 건수만 집계")
        void unreadCount_isRead_false건수만집계() throws Exception {
            given(userRepository.findIdsByAgency_IdAndRole(AGENCY_ID, Role.ENGINEER))
                    .willReturn(List.of(ENGINEER_ID));
            given(asRequestRepository.findDistinctCustomerIdsByAgencyId(AGENCY_ID))
                    .willReturn(List.of());

            given(notificationRepository.findByUser_IdInAndTypeOrderByCreatedAtDesc(anyList(), isNull(), any()))
                    .willReturn(new PageImpl<>(List.of(), pageable, 0));
            given(notificationRepository.countByUser_IdIn(anyList())).willReturn(0L);
            given(notificationRepository.countByUser_IdInAndIsReadFalse(List.of(ENGINEER_ID))).willReturn(2L);
            given(notificationRepository.countByUser_IdInAndCreatedAtBetween(anyList(), any(), any())).willReturn(0L);

            AgencyNotificationResponse response = agencyNotificationService.getNotifications(agencyUser, pageable, null);

            assertThat(response.stats().unreadCount()).isEqualTo(2L);
        }

        @Test
        @DisplayName("todayCount는 오늘 생성된 건만 집계")
        void todayCount_오늘생성건만집계() throws Exception {
            given(userRepository.findIdsByAgency_IdAndRole(AGENCY_ID, Role.ENGINEER))
                    .willReturn(List.of(ENGINEER_ID));
            given(asRequestRepository.findDistinctCustomerIdsByAgencyId(AGENCY_ID))
                    .willReturn(List.of());

            given(notificationRepository.findByUser_IdInAndTypeOrderByCreatedAtDesc(anyList(), isNull(), any()))
                    .willReturn(new PageImpl<>(List.of(), pageable, 0));
            given(notificationRepository.countByUser_IdIn(anyList())).willReturn(0L);
            given(notificationRepository.countByUser_IdInAndIsReadFalse(anyList())).willReturn(0L);
            given(notificationRepository.countByUser_IdInAndCreatedAtBetween(anyList(), any(), any())).willReturn(1L);

            AgencyNotificationResponse response = agencyNotificationService.getNotifications(agencyUser, pageable, null);

            assertThat(response.stats().todayCount()).isEqualTo(1L);
        }

        @Test
        @DisplayName("페이징 요청 파라미터가 응답에 그대로 반영")
        void 페이징_요청파라미터_그대로반영() throws Exception {
            Pageable customPageable = PageRequest.of(2, 5);
            given(userRepository.findIdsByAgency_IdAndRole(AGENCY_ID, Role.ENGINEER))
                    .willReturn(List.of(ENGINEER_ID));
            given(asRequestRepository.findDistinctCustomerIdsByAgencyId(AGENCY_ID))
                    .willReturn(List.of());

            given(notificationRepository.findByUser_IdInAndTypeOrderByCreatedAtDesc(anyList(), isNull(), any()))
                    .willReturn(new PageImpl<>(List.of(), customPageable, 12));
            given(notificationRepository.countByUser_IdIn(anyList())).willReturn(12L);
            given(notificationRepository.countByUser_IdInAndIsReadFalse(anyList())).willReturn(0L);
            given(notificationRepository.countByUser_IdInAndCreatedAtBetween(anyList(), any(), any())).willReturn(0L);

            AgencyNotificationResponse response = agencyNotificationService.getNotifications(agencyUser, customPageable, null);

            assertThat(response.currentPage()).isEqualTo(2);
            assertThat(response.size()).isEqualTo(5);
            assertThat(response.totalElements()).isEqualTo(12);
            assertThat(response.totalPages()).isEqualTo(3);
        }

        @Test
        @DisplayName("type 필터 적용 시 repository에 해당 type만 전달되어 그 타입만 조회된다")
        void type필터_적용시_해당타입만조회() throws Exception {
            given(userRepository.findIdsByAgency_IdAndRole(AGENCY_ID, Role.ENGINEER))
                    .willReturn(List.of(ENGINEER_ID));
            given(asRequestRepository.findDistinctCustomerIdsByAgencyId(AGENCY_ID))
                    .willReturn(List.of());

            Notification lmsNotification = buildNotification("LMS", false, LocalDateTime.now());
            given(notificationRepository.findByUser_IdInAndTypeOrderByCreatedAtDesc(
                    List.of(ENGINEER_ID), "LMS", pageable))
                    .willReturn(new PageImpl<>(List.of(lmsNotification), pageable, 1));
            given(notificationRepository.countByUser_IdIn(anyList())).willReturn(5L);
            given(notificationRepository.countByUser_IdInAndIsReadFalse(anyList())).willReturn(0L);
            given(notificationRepository.countByUser_IdInAndCreatedAtBetween(anyList(), any(), any())).willReturn(0L);

            AgencyNotificationResponse response = agencyNotificationService.getNotifications(agencyUser, pageable, "LMS");

            assertThat(response.content()).hasSize(1);
            assertThat(response.content().get(0).type()).isEqualTo("LMS");
            verify(notificationRepository).findByUser_IdInAndTypeOrderByCreatedAtDesc(List.of(ENGINEER_ID), "LMS", pageable);
        }

        @Test
        @DisplayName("type 미입력 시 전체 조회 — 기존 API와 동일하게 동작")
        void type미입력시_전체조회_기존과동일() throws Exception {
            given(userRepository.findIdsByAgency_IdAndRole(AGENCY_ID, Role.ENGINEER))
                    .willReturn(List.of(ENGINEER_ID));
            given(asRequestRepository.findDistinctCustomerIdsByAgencyId(AGENCY_ID))
                    .willReturn(List.of());

            given(notificationRepository.findByUser_IdInAndTypeOrderByCreatedAtDesc(anyList(), isNull(), any()))
                    .willReturn(new PageImpl<>(List.of(), pageable, 0));
            given(notificationRepository.countByUser_IdIn(anyList())).willReturn(0L);
            given(notificationRepository.countByUser_IdInAndIsReadFalse(anyList())).willReturn(0L);
            given(notificationRepository.countByUser_IdInAndCreatedAtBetween(anyList(), any(), any())).willReturn(0L);

            agencyNotificationService.getNotifications(agencyUser, pageable, null);

            verify(notificationRepository).findByUser_IdInAndTypeOrderByCreatedAtDesc(List.of(ENGINEER_ID), null, pageable);
        }

        @Test
        @DisplayName("stats는 type 필터와 무관하게 항상 전체 범위로 집계된다")
        void stats는_type필터와무관하게_전체범위유지() throws Exception {
            given(userRepository.findIdsByAgency_IdAndRole(AGENCY_ID, Role.ENGINEER))
                    .willReturn(List.of(ENGINEER_ID));
            given(asRequestRepository.findDistinctCustomerIdsByAgencyId(AGENCY_ID))
                    .willReturn(List.of());

            given(notificationRepository.findByUser_IdInAndTypeOrderByCreatedAtDesc(anyList(), eq("LMS"), any()))
                    .willReturn(new PageImpl<>(List.of(), pageable, 1));
            given(notificationRepository.countByUser_IdIn(List.of(ENGINEER_ID))).willReturn(5L);
            given(notificationRepository.countByUser_IdInAndIsReadFalse(anyList())).willReturn(0L);
            given(notificationRepository.countByUser_IdInAndCreatedAtBetween(anyList(), any(), any())).willReturn(0L);

            AgencyNotificationResponse response = agencyNotificationService.getNotifications(agencyUser, pageable, "LMS");

            // stats.totalCount는 type 필터된 totalElements(1)이 아니라 전체 범위 집계(5)를 사용해야 한다
            assertThat(response.stats().totalCount()).isEqualTo(5L);
            assertThat(response.totalElements()).isEqualTo(1L);
        }
    }

    @Nested
    @DisplayName("실패 케이스")
    class Failure {

        @Test
        @DisplayName("AGENCY가 아닌 role → IllegalAccessException")
        void AGENCY아닌_role_예외발생() {
            assertThatThrownBy(() -> agencyNotificationService.getNotifications(customerUser, pageable, null))
                    .isInstanceOf(IllegalAccessException.class);
            verifyNoInteractions(userRepository, asRequestRepository, notificationRepository);
        }

        @Test
        @DisplayName("잘못된 type 값 → IllegalArgumentException, repository 호출 없음")
        void 잘못된type값_IllegalArgumentException() {
            assertThatThrownBy(() -> agencyNotificationService.getNotifications(agencyUser, pageable, "INVALID"))
                    .isInstanceOf(IllegalArgumentException.class);
            verifyNoInteractions(userRepository, asRequestRepository, notificationRepository);
        }
    }

    @Nested
    @DisplayName("markAsRead — 알림 읽음 처리")
    class MarkAsRead {

        @Test
        @DisplayName("성공: 소속 기사 알림 읽음 처리")
        void 정상_소속기사알림_읽음처리() throws Exception {
            given(userRepository.findIdsByAgency_IdAndRole(AGENCY_ID, Role.ENGINEER))
                    .willReturn(List.of(ENGINEER_ID));
            given(asRequestRepository.findDistinctCustomerIdsByAgencyId(AGENCY_ID))
                    .willReturn(List.of());

            Notification notification = buildNotificationOf(ENGINEER_ID);
            given(notificationRepository.findById(1L)).willReturn(java.util.Optional.of(notification));

            agencyNotificationService.markAsRead(agencyUser, 1L);

            verify(notification).markAsRead();
        }

        @Test
        @DisplayName("성공: 소속 고객 알림 읽음 처리")
        void 정상_소속고객알림_읽음처리() throws Exception {
            given(userRepository.findIdsByAgency_IdAndRole(AGENCY_ID, Role.ENGINEER))
                    .willReturn(List.of());
            given(asRequestRepository.findDistinctCustomerIdsByAgencyId(AGENCY_ID))
                    .willReturn(List.of(CUSTOMER_ID));

            Notification notification = buildNotificationOf(CUSTOMER_ID);
            given(notificationRepository.findById(1L)).willReturn(java.util.Optional.of(notification));

            agencyNotificationService.markAsRead(agencyUser, 1L);

            verify(notification).markAsRead();
        }

        @Test
        @DisplayName("성공: 이미 읽음 상태여도 재호출 시 정상 처리(멱등)")
        void 이미읽음상태_재호출해도_정상처리() throws Exception {
            given(userRepository.findIdsByAgency_IdAndRole(AGENCY_ID, Role.ENGINEER))
                    .willReturn(List.of(ENGINEER_ID));
            given(asRequestRepository.findDistinctCustomerIdsByAgencyId(AGENCY_ID))
                    .willReturn(List.of());

            Notification notification = buildNotificationOf(ENGINEER_ID);
            given(notificationRepository.findById(1L)).willReturn(java.util.Optional.of(notification));

            assertThatCode(() -> agencyNotificationService.markAsRead(agencyUser, 1L))
                    .doesNotThrowAnyException();
            verify(notification).markAsRead();
        }

        @Test
        @DisplayName("실패: 존재하지 않는 알림 → NoSuchElementException, markAsRead 미호출")
        void 존재하지않는알림_NoSuchElementException() {
            given(notificationRepository.findById(999L)).willReturn(java.util.Optional.empty());

            assertThatThrownBy(() -> agencyNotificationService.markAsRead(agencyUser, 999L))
                    .isInstanceOf(NoSuchElementException.class);
            verifyNoInteractions(userRepository, asRequestRepository);
        }

        @Test
        @DisplayName("실패: 타 대행사 소속 알림 → IllegalAccessException, markAsRead 미호출")
        void 타대행사알림_IllegalAccessException() {
            given(userRepository.findIdsByAgency_IdAndRole(AGENCY_ID, Role.ENGINEER))
                    .willReturn(List.of(ENGINEER_ID));
            given(asRequestRepository.findDistinctCustomerIdsByAgencyId(AGENCY_ID))
                    .willReturn(List.of(CUSTOMER_ID));

            Notification notification = buildNotificationOf(999L); // 수신 대상 범위 밖의 user_id
            given(notificationRepository.findById(1L)).willReturn(java.util.Optional.of(notification));

            assertThatThrownBy(() -> agencyNotificationService.markAsRead(agencyUser, 1L))
                    .isInstanceOf(IllegalAccessException.class);
            verify(notification, never()).markAsRead();
        }

        @Test
        @DisplayName("실패: AGENCY가 아닌 role → IllegalAccessException, repository 호출 없음")
        void AGENCY아닌_role_예외발생() {
            assertThatThrownBy(() -> agencyNotificationService.markAsRead(customerUser, 1L))
                    .isInstanceOf(IllegalAccessException.class);
            verifyNoInteractions(notificationRepository, userRepository, asRequestRepository);
        }
    }

    @Nested
    @DisplayName("markAllAsRead — 알림 전체 읽음 처리")
    class MarkAllAsRead {

        @Test
        @DisplayName("성공: 수신 대상 전체를 벌크 읽음 처리한다")
        void 정상_수신대상전체_벌크읽음처리() throws Exception {
            given(userRepository.findIdsByAgency_IdAndRole(AGENCY_ID, Role.ENGINEER))
                    .willReturn(List.of(ENGINEER_ID));
            given(asRequestRepository.findDistinctCustomerIdsByAgencyId(AGENCY_ID))
                    .willReturn(List.of(CUSTOMER_ID));
            given(notificationRepository.markAllAsReadByUserIds(List.of(ENGINEER_ID, CUSTOMER_ID)))
                    .willReturn(3);

            agencyNotificationService.markAllAsRead(agencyUser);

            verify(notificationRepository).markAllAsReadByUserIds(List.of(ENGINEER_ID, CUSTOMER_ID));
        }

        @Test
        @DisplayName("성공: 수신 대상이 없으면 쿼리 호출 없이 정상 종료한다")
        void 수신대상없음_쿼리호출없이_정상종료() throws Exception {
            given(userRepository.findIdsByAgency_IdAndRole(AGENCY_ID, Role.ENGINEER))
                    .willReturn(List.of());
            given(asRequestRepository.findDistinctCustomerIdsByAgencyId(AGENCY_ID))
                    .willReturn(List.of());

            assertThatCode(() -> agencyNotificationService.markAllAsRead(agencyUser))
                    .doesNotThrowAnyException();
            verify(notificationRepository, never()).markAllAsReadByUserIds(anyList());
        }

        @Test
        @DisplayName("성공: 이미 전체 읽음 상태여도(0건 갱신) 재호출 시 정상 처리")
        void 대상이미모두읽음_재호출해도_정상처리() throws Exception {
            given(userRepository.findIdsByAgency_IdAndRole(AGENCY_ID, Role.ENGINEER))
                    .willReturn(List.of(ENGINEER_ID));
            given(asRequestRepository.findDistinctCustomerIdsByAgencyId(AGENCY_ID))
                    .willReturn(List.of());
            given(notificationRepository.markAllAsReadByUserIds(anyList())).willReturn(0);

            assertThatCode(() -> agencyNotificationService.markAllAsRead(agencyUser))
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("실패: AGENCY가 아닌 role → IllegalAccessException, repository 호출 없음")
        void AGENCY아닌_role_예외발생() {
            assertThatThrownBy(() -> agencyNotificationService.markAllAsRead(customerUser))
                    .isInstanceOf(IllegalAccessException.class);
            verifyNoInteractions(notificationRepository, userRepository, asRequestRepository);
        }
    }
}
