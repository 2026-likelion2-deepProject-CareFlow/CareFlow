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

    @Mock
    private EngineerScheduleRepository engineerScheduleRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private EngineerProfileRepository engineerProfileRepository;

    @Test
    @DisplayName("스케줄 등록 성공 (순서가 뒤섞여 와도 정상 정렬 및 저장)")
    void createSchedule_Success() throws Exception {
        // Given
        Long userId = 1L;
        User user = User.builder().role(Role.ENGINEER).build();
        ReflectionTestUtils.setField(user, "id", userId);

        ApplianceCategory category = new ApplianceCategory();
        ReflectionTestUtils.setField(category, "categoryId", 10);
        ReflectionTestUtils.setField(category, "depth", 2);

        EngineerProfile completedProfile = EngineerProfile.builder()
                .user(user).category(category).careerStartedYear(2020).skillLevel(SkillLevel.BEGINNER).build();

        // 1. ScheduleRequest 생성 우회
        Constructor<ScheduleRequest> reqConstructor = ScheduleRequest.class.getDeclaredConstructor();
        reqConstructor.setAccessible(true);
        ScheduleRequest request = reqConstructor.newInstance();
        ReflectionTestUtils.setField(request, "workDate", LocalDate.now().plusDays(1));

        // 2. TimeSlotDto 생성 우회
        Constructor<ScheduleRequest.TimeSlotDto> slotConstructor = ScheduleRequest.TimeSlotDto.class.getDeclaredConstructor();
        slotConstructor.setAccessible(true);

        ScheduleRequest.TimeSlotDto slot1 = slotConstructor.newInstance();
        ReflectionTestUtils.setField(slot1, "start", "13:00");
        ReflectionTestUtils.setField(slot1, "end", "15:00");

        ScheduleRequest.TimeSlotDto slot2 = slotConstructor.newInstance();
        ReflectionTestUtils.setField(slot2, "start", "09:00");
        ReflectionTestUtils.setField(slot2, "end", "12:00");

        ReflectionTestUtils.setField(request, "timeSlots", List.of(slot1, slot2));

        // 팀원이 수정한 메서드명 적용
        given(userRepository.findById(1L)).willReturn(Optional.of(user));
        given(engineerProfileRepository.findByUser_Id(1L)).willReturn(Optional.of(completedProfile));
        given(engineerScheduleRepository.existsByUser_IdAndWorkDate(1L, request.getWorkDate())).willReturn(false);

        given(engineerScheduleRepository.save(any(EngineerSchedule.class)))
                .willAnswer(invocation -> invocation.getArgument(0));

        // When
        ScheduleResponse response = engineerScheduleService.createSchedule(1L, request);

        // Then
        assertThat(response.getTimeSlots()).hasSize(2);
        // 정렬이 잘 되었다면 첫 번째 슬롯은 09:00 이어야 함
        assertThat(response.getTimeSlots().get(0).getStart()).isEqualTo("09:00");
    }

    @Test
    @DisplayName("실패: 시간대가 겹치는 경우 예외 발생")
    void createSchedule_Fail_TimeOverlap() throws Exception {
        // Given
        Long userId = 1L;
        User user = User.builder().role(Role.ENGINEER).build();
        ReflectionTestUtils.setField(user, "id", userId);

        ApplianceCategory category = new ApplianceCategory();
        ReflectionTestUtils.setField(category, "categoryId", 10);
        ReflectionTestUtils.setField(category, "depth", 2);

        EngineerProfile completedProfile = EngineerProfile.builder()
                .user(user).category(category).careerStartedYear(2020).skillLevel(SkillLevel.BEGINNER).build();

        Constructor<ScheduleRequest> reqConstructor = ScheduleRequest.class.getDeclaredConstructor();
        reqConstructor.setAccessible(true);
        ScheduleRequest request = reqConstructor.newInstance();
        ReflectionTestUtils.setField(request, "workDate", LocalDate.now().plusDays(1));

        Constructor<ScheduleRequest.TimeSlotDto> slotConstructor = ScheduleRequest.TimeSlotDto.class.getDeclaredConstructor();
        slotConstructor.setAccessible(true);

        ScheduleRequest.TimeSlotDto slot1 = slotConstructor.newInstance();
        ReflectionTestUtils.setField(slot1, "start", "09:00");
        ReflectionTestUtils.setField(slot1, "end", "12:00");

        ScheduleRequest.TimeSlotDto slot2 = slotConstructor.newInstance();
        ReflectionTestUtils.setField(slot2, "start", "11:00");
        ReflectionTestUtils.setField(slot2, "end", "14:00");

        ReflectionTestUtils.setField(request, "timeSlots", List.of(slot1, slot2));

        given(userRepository.findById(1L)).willReturn(Optional.of(user));
        given(engineerProfileRepository.findByUser_Id(1L)).willReturn(Optional.of(completedProfile));

        // When & Then
        assertThatThrownBy(() -> engineerScheduleService.createSchedule(1L, request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("근무 가능 시간이 서로 겹칠 수 없습니다.");
    }
}