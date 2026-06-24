package com.careflow.engineer.service;

import com.careflow.common.enums.Role;
import com.careflow.engineer.domain.entity.ApplianceCategory;
import com.careflow.engineer.domain.entity.EngineerProfile;
import com.careflow.engineer.domain.enums.SkillLevel;
import com.careflow.engineer.dto.ScheduleRequest;
import com.careflow.engineer.dto.ScheduleResponse;
import com.careflow.engineer.repository.ApplianceCategoryRepository;
import com.careflow.engineer.repository.EngineerProfileRepository;
import com.careflow.engineer.repository.EngineerScheduleRepository;
import com.careflow.user.entity.User;
import com.careflow.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.util.ReflectionTestUtils;

import java.lang.reflect.Constructor;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("local")
@Sql(scripts = "/cleanup.sql", executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
@DisplayName("EngineerScheduleService 통합 테스트 (진짜 DB 연동)")
class EngineerScheduleServiceIntegrationTest {

    @Autowired private EngineerScheduleService engineerScheduleService;
    @Autowired private EngineerScheduleRepository engineerScheduleRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private EngineerProfileRepository engineerProfileRepository;
    @Autowired private ApplianceCategoryRepository categoryRepository;

    private User testUser;
    private ApplianceCategory testCategory;

    @BeforeEach
    void setUp() {
        // 기초 DB 세팅
        testUser = userRepository.save(User.builder()
                .email("schedule@test.com").passwordHash("hashed")
                .name("스케줄기사").phone("010-9999-8888").role(Role.ENGINEER).build());

        ApplianceCategory cat = new ApplianceCategory();
        ReflectionTestUtils.setField(cat, "depth", 2);
        testCategory = categoryRepository.save(cat);
    }

    @Test
    @DisplayName("성공: 스케줄 등록 (DB Insert 검증)")
    void createSchedule_Success() throws Exception {
        // Given (프로필 완성 상태 세팅 후 DB 저장)
        EngineerProfile profile = EngineerProfile.createInitial(testUser);
        profile.completeProfile(testCategory, 2020, SkillLevel.BEGINNER, "안녕");
        engineerProfileRepository.save(profile);

        ScheduleRequest request = newScheduleRequest(LocalDate.now().plusDays(1),
                List.of(slot("13:00", "15:00"), slot("09:00", "12:00")));

        // When
        ScheduleResponse response = engineerScheduleService.createSchedule(testUser.getId(), request);

        // Then
        assertThat(response.getTimeSlots()).hasSize(2);
        assertThat(response.getTimeSlots().get(0).getStart()).isEqualTo("09:00");

        // 실제 DB에 스케줄이 제대로 들어갔는지 검증!
        boolean exists = engineerScheduleRepository.existsByUser_IdAndWorkDate(testUser.getId(), request.getWorkDate());
        assertThat(exists).isTrue();
    }

    @Test
    @DisplayName("실패: 프로필 미완성 기사가 스케줄 등록 시도")
    void createSchedule_Fail_ProfileIncomplete() throws Exception {
        // Given (초기화만 되고 완성 안 된 프로필 DB 저장)
        engineerProfileRepository.save(EngineerProfile.createInitial(testUser));

        ScheduleRequest request = newScheduleRequest(LocalDate.now().plusDays(1),
                List.of(slot("09:00", "12:00")));

        // When & Then
        assertThatThrownBy(() -> engineerScheduleService.createSchedule(testUser.getId(), request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("프로필 필수 정보를 먼저 완성");
    }

    // --- 헬퍼 메서드 ---
    private ScheduleRequest.TimeSlotDto slot(String start, String end) throws Exception {
        Constructor<ScheduleRequest.TimeSlotDto> constructor = ScheduleRequest.TimeSlotDto.class.getDeclaredConstructor();
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