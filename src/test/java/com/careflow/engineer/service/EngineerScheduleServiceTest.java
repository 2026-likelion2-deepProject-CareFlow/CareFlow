package com.careflow.engineer.service;

import com.careflow.common.enums.Role;
import com.careflow.engineer.domain.entity.ApplianceCategory;
import com.careflow.engineer.domain.entity.EngineerProfile;
import com.careflow.engineer.domain.entity.EngineerSchedule;
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
import org.springframework.test.util.ReflectionTestUtils;

import java.lang.reflect.Constructor;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class EngineerScheduleServiceTest {

    @InjectMocks
    private EngineerScheduleService engineerScheduleService;

    @Mock private EngineerScheduleRepository engineerScheduleRepository;
    @Mock private UserRepository userRepository;
    @Mock private EngineerProfileRepository engineerProfileRepository;

    private static final Long USER_ID = 1L;

    @Test
    @DisplayName("성공: 슬롯 순서가 뒤섞여 와도 정상 정렬 후 저장")
    void createSchedule_Success() throws Exception {
        // Given
        User user = engineer(USER_ID);
        EngineerProfile completedProfile = completedProfile(user);

        ScheduleRequest request = newScheduleRequest(
                LocalDate.now().plusDays(1),
                List.of(slot("13:00", "15:00"), slot("09:00", "12:00")));

        given(userRepository.findById(USER_ID)).willReturn(Optional.of(user));
        given(engineerProfileRepository.findByUser_Id(USER_ID)).willReturn(Optional.of(completedProfile));
        given(engineerScheduleRepository.existsByUser_IdAndWorkDate(USER_ID, request.getWorkDate())).willReturn(false);
        given(engineerScheduleRepository.save(any(EngineerSchedule.class)))
                .willAnswer(invocation -> invocation.getArgument(0));

        // When
        ScheduleResponse response = engineerScheduleService.createSchedule(USER_ID, request);

        // Then
        assertThat(response.getTimeSlots()).hasSize(2);
        assertThat(response.getTimeSlots().get(0).getStart()).isEqualTo("09:00"); // 정렬 검증
    }

    @Test
    @DisplayName("실패: 근무 시간대가 서로 겹침")
    void createSchedule_Fail_TimeOverlap() throws Exception {
        // Given
        User user = engineer(USER_ID);
        EngineerProfile completedProfile = completedProfile(user);

        ScheduleRequest request = newScheduleRequest(
                LocalDate.now().plusDays(1),
                List.of(slot("09:00", "12:00"), slot("11:00", "14:00")));

        given(userRepository.findById(USER_ID)).willReturn(Optional.of(user));
        given(engineerProfileRepository.findByUser_Id(USER_ID)).willReturn(Optional.of(completedProfile));

        // When & Then
        assertThatThrownBy(() -> engineerScheduleService.createSchedule(USER_ID, request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("근무 가능 시간이 서로 겹칠 수 없습니다.");
    }

    @Test
    @DisplayName("실패: 프로필 미완성 상태에서 근무표 등록 시도")
    void createSchedule_Fail_ProfileNotCompleted() throws Exception {
        // Given
        User user = engineer(USER_ID);
        EngineerProfile emptyProfile = EngineerProfile.createInitial(user); // 카테고리·경력 미입력

        ScheduleRequest request = newScheduleRequest(
                LocalDate.now().plusDays(1),
                List.of(slot("09:00", "12:00")));

        given(userRepository.findById(USER_ID)).willReturn(Optional.of(user));
        given(engineerProfileRepository.findByUser_Id(USER_ID)).willReturn(Optional.of(emptyProfile));

        // When & Then
        assertThatThrownBy(() -> engineerScheduleService.createSchedule(USER_ID, request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("프로필 필수 정보를 먼저 완성");
    }

    // ---------- 픽스처 헬퍼 ----------

    private User engineer(Long id) {
        User user = User.builder().role(Role.ENGINEER).build();
        ReflectionTestUtils.setField(user, "id", id);
        return user;
    }

    private EngineerProfile completedProfile(User user) {
        ApplianceCategory category = new ApplianceCategory();
        ReflectionTestUtils.setField(category, "categoryId", 10);
        ReflectionTestUtils.setField(category, "depth", 2);

        EngineerProfile profile = EngineerProfile.createInitial(user);
        profile.completeProfile(category, 2020, SkillLevel.BEGINNER, null);
        return profile;
    }

    private ScheduleRequest.TimeSlotDto slot(String start, String end) throws Exception {
        Constructor<ScheduleRequest.TimeSlotDto> constructor =
                ScheduleRequest.TimeSlotDto.class.getDeclaredConstructor();
        constructor.setAccessible(true);
        ScheduleRequest.TimeSlotDto slot = constructor.newInstance();
        ReflectionTestUtils.setField(slot, "start", start);
        ReflectionTestUtils.setField(slot, "end", end);
        return slot;
    }

    private ScheduleRequest newScheduleRequest(LocalDate workDate, List<ScheduleRequest.TimeSlotDto> slots) throws Exception {
        Constructor<ScheduleRequest> constructor = ScheduleRequest.class.getDeclaredConstructor();
        constructor.setAccessible(true);
        ScheduleRequest request = constructor.newInstance();
        ReflectionTestUtils.setField(request, "workDate", workDate);
        ReflectionTestUtils.setField(request, "timeSlots", slots);
        return request;
    }
}