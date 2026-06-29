package com.careflow.as_status_log.service;

import com.careflow.as_status_log.dto.AsStatusLogListResponse;
import com.careflow.as_status_log.dto.AsStatusLogSummaryResponse;
import com.careflow.as_status_log.entity.AsStatusLog;
import com.careflow.as_status_log.repository.AsStatusLogRepository;
import com.careflow.auth.security.CustomUserDetails;
import com.careflow.common.enums.Role;
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

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("AgencyAsStatusLogService 단위 테스트")
class AgencyAsStatusLogServiceTest {

    @InjectMocks AgencyAsStatusLogService service;
    @Mock AsStatusLogRepository asStatusLogRepository;
    @Mock UserRepository userRepository;

    private CustomUserDetails agencyUserDetails;
    private CustomUserDetails adminUserDetails;
    private User agencyUser;

    private static final Long USER_ID   = 1L;
    private static final Long AGENCY_ID = 10L;

    @BeforeEach
    void setUp() {
        agencyUserDetails = mock(CustomUserDetails.class);
        when(agencyUserDetails.getRole()).thenReturn("AGENCY");
        when(agencyUserDetails.getUserId()).thenReturn(USER_ID);

        adminUserDetails = mock(CustomUserDetails.class);
        when(adminUserDetails.getRole()).thenReturn("ADMIN");

        // 소속 대행사가 있는 유저
        com.careflow.agency.entity.Agencies agency = mock(com.careflow.agency.entity.Agencies.class);
        when(agency.getId()).thenReturn(AGENCY_ID);

        agencyUser = mock(User.class);
        when(agencyUser.getAgency()).thenReturn(agency);
    }

    // ─────────────────────────────────────────────
    //  getStatusLogs
    // ─────────────────────────────────────────────
    @Nested
    @DisplayName("getStatusLogs — 이력 목록 조회")
    class GetStatusLogs {

        @Test
        @DisplayName("성공: 이력 2건 반환")
        void success_returnsTwoLogs() throws Exception {
            AsStatusLog log1 = mock(AsStatusLog.class);
            AsStatusLog log2 = mock(AsStatusLog.class);

            mockLogFields(log1, 1L, 101L, null, "WAITING", null, "기사A");
            mockLogFields(log2, 2L, 102L, "WAITING", "ENGINEER_DEPARTED", "출발", "기사B");

            when(userRepository.findById(USER_ID)).thenReturn(Optional.of(agencyUser));
            when(asStatusLogRepository.findAllByAgencyIdOrderByCreatedAtDesc(AGENCY_ID))
                    .thenReturn(List.of(log1, log2));

            AsStatusLogListResponse result = service.getStatusLogs(agencyUserDetails);

            assertThat(result.totalCount()).isEqualTo(2);
            assertThat(result.logs()).hasSize(2);
            assertThat(result.logs().get(0).toStatus()).isEqualTo("WAITING");
            assertThat(result.logs().get(1).toStatus()).isEqualTo("ENGINEER_DEPARTED");
        }

        @Test
        @DisplayName("성공: 이력 없을 때 totalCount=0, 빈 배열 반환")
        void success_noLogs_returnsEmpty() throws Exception {
            when(userRepository.findById(USER_ID)).thenReturn(Optional.of(agencyUser));
            when(asStatusLogRepository.findAllByAgencyIdOrderByCreatedAtDesc(AGENCY_ID))
                    .thenReturn(List.of());

            AsStatusLogListResponse result = service.getStatusLogs(agencyUserDetails);

            assertThat(result.totalCount()).isEqualTo(0);
            assertThat(result.logs()).isEmpty();
        }

        @Test
        @DisplayName("실패: AGENCY 권한 없으면 IllegalAccessException")
        void fail_notAgency_throwsIllegalAccess() {
            assertThatThrownBy(() -> service.getStatusLogs(adminUserDetails))
                    .isInstanceOf(IllegalAccessException.class)
                    .hasMessageContaining("대행사 관리자 권한이 없습니다.");
        }

        @Test
        @DisplayName("실패: 소속 대행사 없으면 IllegalStateException")
        void fail_noAgency_throwsIllegalState() {
            User userWithoutAgency = mock(User.class);
            when(userWithoutAgency.getAgency()).thenReturn(null);
            when(userRepository.findById(USER_ID)).thenReturn(Optional.of(userWithoutAgency));

            assertThatThrownBy(() -> service.getStatusLogs(agencyUserDetails))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("소속 대행사 정보가 없습니다.");
        }
    }

    // ─────────────────────────────────────────────
    //  getStatusSummary
    // ─────────────────────────────────────────────
    @Nested
    @DisplayName("getStatusSummary — 상태별 집계")
    class GetStatusSummary {

        @Test
        @DisplayName("성공: 5개 상태 모두 집계")
        void success_allStatusCounted() throws Exception {
            when(userRepository.findById(USER_ID)).thenReturn(Optional.of(agencyUser));
            when(asStatusLogRepository.countGroupByToStatusForAgency(AGENCY_ID))
                    .thenReturn(List.of(
                            new Object[]{"WAITING", 5L},
                            new Object[]{"ENGINEER_DEPARTED", 4L},
                            new Object[]{"ENGINEER_ARRIVED", 3L},
                            new Object[]{"IN_PROGRESS", 2L},
                            new Object[]{"COMPLETED", 10L}
                    ));

            AsStatusLogSummaryResponse result = service.getStatusSummary(agencyUserDetails);

            assertThat(result.waitingCount()).isEqualTo(5L);
            assertThat(result.engineerDepartedCount()).isEqualTo(4L);
            assertThat(result.engineerArrivedCount()).isEqualTo(3L);
            assertThat(result.inProgressCount()).isEqualTo(2L);
            assertThat(result.completedCount()).isEqualTo(10L);
        }

        @Test
        @DisplayName("성공: 일부 상태 이력 없으면 해당 count = 0")
        void success_missingStatusFillsZero() throws Exception {
            when(userRepository.findById(USER_ID)).thenReturn(Optional.of(agencyUser));
            // ENGINEER_ARRIVED, IN_PROGRESS 이력 없음
            when(asStatusLogRepository.countGroupByToStatusForAgency(AGENCY_ID))
                    .thenReturn(List.of(
                            new Object[]{"WAITING", 3L},
                            new Object[]{"COMPLETED", 7L}
                    ));

            AsStatusLogSummaryResponse result = service.getStatusSummary(agencyUserDetails);

            assertThat(result.waitingCount()).isEqualTo(3L);
            assertThat(result.engineerDepartedCount()).isEqualTo(0L);
            assertThat(result.engineerArrivedCount()).isEqualTo(0L);
            assertThat(result.inProgressCount()).isEqualTo(0L);
            assertThat(result.completedCount()).isEqualTo(7L);
        }

        @Test
        @DisplayName("성공: 이력 전혀 없으면 모두 0")
        void success_noLogs_allZero() throws Exception {
            when(userRepository.findById(USER_ID)).thenReturn(Optional.of(agencyUser));
            when(asStatusLogRepository.countGroupByToStatusForAgency(AGENCY_ID))
                    .thenReturn(List.of());

            AsStatusLogSummaryResponse result = service.getStatusSummary(agencyUserDetails);

            assertThat(result.waitingCount()).isEqualTo(0L);
            assertThat(result.engineerDepartedCount()).isEqualTo(0L);
            assertThat(result.engineerArrivedCount()).isEqualTo(0L);
            assertThat(result.inProgressCount()).isEqualTo(0L);
            assertThat(result.completedCount()).isEqualTo(0L);
        }

        @Test
        @DisplayName("실패: AGENCY 권한 없으면 IllegalAccessException")
        void fail_notAgency_throwsIllegalAccess() {
            assertThatThrownBy(() -> service.getStatusSummary(adminUserDetails))
                    .isInstanceOf(IllegalAccessException.class)
                    .hasMessageContaining("대행사 관리자 권한이 없습니다.");
        }
    }

    // ─────────────────────────────────────────────
    //  헬퍼
    // ─────────────────────────────────────────────
    private void mockLogFields(AsStatusLog log, Long logId, Long requestId,
                                String fromStatus, String toStatus, String memo,
                                String changedByName) {
        com.careflow.as_request.entity.AsRequest req = mock(com.careflow.as_request.entity.AsRequest.class);
        when(req.getId()).thenReturn(requestId);

        User changedBy = mock(User.class);
        when(changedBy.getName()).thenReturn(changedByName);

        when(log.getId()).thenReturn(logId);
        when(log.getAsRequest()).thenReturn(req);
        when(log.getChangedBy()).thenReturn(changedBy);
        when(log.getFromStatus()).thenReturn(fromStatus);
        when(log.getToStatus()).thenReturn(toStatus);
        when(log.getMemo()).thenReturn(memo);
        when(log.getCreatedAt()).thenReturn(null);
    }
}
