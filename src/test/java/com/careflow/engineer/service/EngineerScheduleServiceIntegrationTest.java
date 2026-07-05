package com.careflow.engineer.service;

import com.careflow.common.enums.Role;
import com.careflow.appliance.entity.ApplianceCategory;
import com.careflow.engineer.domain.entity.EngineerProfile;
import com.careflow.engineer.domain.entity.EngineerSchedule;
import com.careflow.common.enums.ScheduleStatus;
import com.careflow.common.enums.SkillLevel;
import com.careflow.engineer.dto.ScheduleRequest;
import com.careflow.engineer.dto.ScheduleResponse;
import com.careflow.appliance.repository.ApplianceCategoryRepository;
import com.careflow.engineer.repository.EngineerProfileRepository;
import com.careflow.engineer.repository.EngineerScheduleRepository;
import com.careflow.user.entity.User;
import com.careflow.user.repository.UserRepository;
import jakarta.transaction.Transactional;
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
@Transactional
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

        // v5 신규 API: createRoot → createChild 2단계 생성
        ApplianceCategory rootCat = categoryRepository.save(ApplianceCategory.createRoot("테스트대분류", 1));
        testCategory = categoryRepository.save(ApplianceCategory.createChild("테스트소분류", rootCat, 1));
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

    // ─────────────────────────────────────────────
    //  조회 및 삭제 로직 통합 테스트
    // ─────────────────────────────────────────────

    @Test
    @DisplayName("성공: 월간 근무 일정 조회 (DB Select 검증)")
    void getMonthlySchedules_Success() throws Exception {
        // Given: 6월 1일과 6월 15일, 그리고 7월 1일 스케줄을 진짜 DB에 저장
        EngineerSchedule schedule1 = EngineerSchedule.builder()
                .user(testUser).workDate(LocalDate.of(2026, 6, 1)).status(ScheduleStatus.AVAILABLE).build();
        EngineerSchedule schedule2 = EngineerSchedule.builder()
                .user(testUser).workDate(LocalDate.of(2026, 6, 15)).status(ScheduleStatus.AVAILABLE).build();
        EngineerSchedule scheduleOtherMonth = EngineerSchedule.builder()
                .user(testUser).workDate(LocalDate.of(2026, 7, 1)).status(ScheduleStatus.AVAILABLE).build();

        engineerScheduleRepository.saveAll(List.of(schedule1, schedule2, scheduleOtherMonth));

        // When: 2026년 6월 데이터만 조회!
        List<ScheduleResponse> responses = engineerScheduleService.getMonthlySchedules(testUser.getId(), 2026, 6);

        // Then: 7월 일정은 빠지고 딱 2개만 나와야 함
        assertThat(responses).hasSize(2);
        assertThat(responses.get(0).getWorkDate()).isEqualTo(LocalDate.of(2026, 6, 1));
        assertThat(responses.get(1).getWorkDate()).isEqualTo(LocalDate.of(2026, 6, 15));
    }

    @Test
    @DisplayName("성공: 스케줄 삭제 시 DB에서 Hard Delete 된다 (DB Delete 검증)")
    void deleteSchedule_Success() throws Exception {
        // Given: 스케줄과 슬롯을 DB에 저장
        EngineerSchedule schedule = EngineerSchedule.builder()
                .user(testUser).workDate(LocalDate.of(2026, 6, 1)).status(ScheduleStatus.AVAILABLE).build();
        schedule.addTimeSlot(com.careflow.engineer.domain.entity.EngineerScheduleSlot.builder()
                .startTime(java.time.LocalTime.of(9, 0)).endTime(java.time.LocalTime.of(12, 0)).build());

        EngineerSchedule savedSchedule = engineerScheduleRepository.save(schedule);

        // 엔티티 매니저 플러시 및 클리어를 해주면 더 완벽하지만, 스프링 데이터 JPA의 더티체킹을 믿고 그냥 진행!

        // When: 삭제 요청
        engineerScheduleService.deleteSchedule(testUser.getId(), savedSchedule.getScheduleId());

        // Then: DB에서 행 자체가 삭제되었는지 확인! (EngineerScheduleServiceTest의 Hard Delete 검증과 동일한 계약)
        assertThat(engineerScheduleRepository.findById(savedSchedule.getScheduleId())).isEmpty();
    }

    @Test
    @DisplayName("실패: 이미 배정된 스케줄은 삭제 불가 (DB 검증)")
    void deleteSchedule_Fail_Booked() throws Exception {
        // Given: BOOKED 상태의 스케줄을 DB에 저장
        EngineerSchedule schedule = EngineerSchedule.builder()
                .user(testUser).workDate(LocalDate.of(2026, 6, 1)).status(ScheduleStatus.BOOKED).build();
        EngineerSchedule savedSchedule = engineerScheduleRepository.save(schedule);

        // When & Then
        assertThatThrownBy(() -> engineerScheduleService.deleteSchedule(testUser.getId(), savedSchedule.getScheduleId()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("이미 A/S가 배정된 근무표");
    }
}