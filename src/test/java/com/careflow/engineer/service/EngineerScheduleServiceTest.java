package com.careflow.engineer.service;

import com.careflow.common.enums.Role;
import com.careflow.appliance.entity.ApplianceCategory;
import com.careflow.engineer.domain.entity.EngineerProfile;
import com.careflow.engineer.domain.entity.EngineerSchedule;
import com.careflow.engineer.domain.enums.ScheduleStatus;
import com.careflow.engineer.domain.enums.SkillLevel;
import com.careflow.engineer.dto.ScheduleRequest;
import com.careflow.engineer.dto.ScheduleResponse;
import com.careflow.engineer.repository.EngineerProfileRepository;
import com.careflow.engineer.repository.EngineerScheduleRepository;
import com.careflow.user.entity.User;
import com.careflow.user.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;

import java.lang.reflect.Constructor;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class EngineerScheduleServiceTest {

    @InjectMocks
    private EngineerScheduleService engineerScheduleService;

    @Mock private EngineerScheduleRepository engineerScheduleRepository;
    @Mock private UserRepository userRepository;
    @Mock private EngineerProfileRepository engineerProfileRepository;

    private static final Long USER_ID = 1L;

    // ... 기존 createSchedule_Success 등 메서드 그대로 유지 ...
    // (이전 코드의 createSchedule_Success, Fail_TimeOverlap, Fail_ProfileNotCompleted 부분은 생략했습니다. 그대로 두시면 됩니다!)

    private User engineer(Long id) {
        User user = User.builder().role(Role.ENGINEER).build();
        ReflectionTestUtils.setField(user, "id", id);
        return user;
    }

    @Test
    @DisplayName("성공: 월간 근무 일정 조회 (getMonthlySchedules)")
    void getMonthlySchedules_Success() throws Exception {
        User user = engineer(USER_ID);
        EngineerSchedule schedule1 = EngineerSchedule.builder()
                .user(user).workDate(LocalDate.of(2026, 6, 1)).status(ScheduleStatus.AVAILABLE).build();
        EngineerSchedule schedule2 = EngineerSchedule.builder()
                .user(user).workDate(LocalDate.of(2026, 6, 15)).status(ScheduleStatus.AVAILABLE).build();

        LocalDate startDate = LocalDate.of(2026, 6, 1);
        LocalDate endDate = LocalDate.of(2026, 6, 30);

        given(engineerScheduleRepository.findByUser_IdAndWorkDateBetweenOrderByWorkDateAsc(USER_ID, startDate, endDate))
                .willReturn(List.of(schedule1, schedule2));

        List<ScheduleResponse> responses = engineerScheduleService.getMonthlySchedules(USER_ID, 2026, 6);

        assertThat(responses).hasSize(2);
        assertThat(responses.get(0).getWorkDate()).isEqualTo(LocalDate.of(2026, 6, 1));
        assertThat(responses.get(1).getWorkDate()).isEqualTo(LocalDate.of(2026, 6, 15));
    }

    // 🌟 수정: Hard Delete 검증 로직으로 변경
    @Test
    @DisplayName("성공: 스케줄 삭제 시 상태 변경(OFF)이 아니라 DB에서 Hard Delete 된다.")
    void deleteSchedule_Success() throws Exception {
        User user = engineer(USER_ID);
        EngineerSchedule schedule = EngineerSchedule.builder()
                .user(user).workDate(LocalDate.now()).status(ScheduleStatus.AVAILABLE).build();
        ReflectionTestUtils.setField(schedule, "scheduleId", 1L);

        given(engineerScheduleRepository.findById(1L)).willReturn(Optional.of(schedule));

        engineerScheduleService.deleteSchedule(USER_ID, 1L);

        // 🌟 물리 삭제 검증 (OFF 체크 삭제)
        verify(engineerScheduleRepository).delete(schedule);
    }

    @Test
    @DisplayName("실패: 이미 배정(BOOKED)된 스케줄은 삭제 불가")
    void deleteSchedule_Fail_Booked() throws Exception {
        User user = engineer(USER_ID);
        EngineerSchedule schedule = EngineerSchedule.builder()
                .user(user).workDate(LocalDate.now()).status(ScheduleStatus.BOOKED).build();
        ReflectionTestUtils.setField(schedule, "scheduleId", 1L);

        given(engineerScheduleRepository.findById(1L)).willReturn(Optional.of(schedule));

        assertThatThrownBy(() -> engineerScheduleService.deleteSchedule(USER_ID, 1L))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("이미 A/S가 배정된 근무표");
    }

    // 🌟 추가할 코드 (STEP 3 방어적 프로그래밍 검증)
    @Test
    @DisplayName("성공: 등록된 일정이 없는 날짜(휴무)를 조회하면 예외 대신 OFF 상태의 빈 응답을 반환한다.")
    void getDailySchedule_OffDay_Success() {
        LocalDate targetDate = LocalDate.of(2026, 12, 25);
        Long userId = 1L;

        // DB에 해당 날짜 일정이 없다고 모킹
        given(engineerScheduleRepository.findByUser_IdAndWorkDateBetweenOrderByWorkDateAsc(userId, targetDate, targetDate))
                .willReturn(List.of());

        // 예외가 터지지 않고 정상 응답!
        ScheduleResponse response = engineerScheduleService.getDailySchedule(userId, targetDate);

        assertThat(response.getWorkDate()).isEqualTo(targetDate);
        assertThat(response.getStatus()).isEqualTo("OFF"); // 프론트엔드 안전 처리
        assertThat(response.getTimeSlots()).isEmpty();
    }
}