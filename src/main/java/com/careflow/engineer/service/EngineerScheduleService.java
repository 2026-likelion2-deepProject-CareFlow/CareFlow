package com.careflow.engineer.service;

import com.careflow.common.enums.Role;
import com.careflow.engineer.domain.entity.EngineerProfile;
import com.careflow.engineer.domain.entity.EngineerSchedule;
import com.careflow.engineer.domain.entity.EngineerScheduleSlot;
import com.careflow.engineer.domain.enums.ScheduleStatus;
import com.careflow.engineer.dto.ScheduleRequest;
import com.careflow.engineer.dto.ScheduleResponse;
import com.careflow.engineer.repository.EngineerProfileRepository;
import com.careflow.engineer.repository.EngineerScheduleRepository;
import com.careflow.user.entity.User;
import com.careflow.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class EngineerScheduleService {

    private final EngineerScheduleRepository engineerScheduleRepository;
    private final UserRepository userRepository;
    private final EngineerProfileRepository engineerProfileRepository;

    private record ParsedSlot(LocalTime start, LocalTime end) {}

    @Transactional
    public ScheduleResponse createSchedule(Long userId, ScheduleRequest request) {

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("유저 정보가 존재하지 않습니다."));

        if (user.getRole() != Role.ENGINEER) {
            throw new IllegalArgumentException("기사 권한만 등록 가능합니다.");
        }

        EngineerProfile profile = engineerProfileRepository.findByUser_Id(userId)
                .orElseThrow(() -> new IllegalArgumentException("프로필 정보가 존재하지 않습니다."));

        if (!profile.isCompleted()) {
            throw new IllegalArgumentException("전문 분야 카테고리 등 프로필 필수 정보를 먼저 완성해주세요.");
        }

        if (engineerScheduleRepository.existsByUser_IdAndWorkDate(userId, request.getWorkDate())) {
            throw new IllegalArgumentException("해당 날짜에 이미 근무표가 존재합니다.");
        }

        List<ParsedSlot> parsedSlots = validAndParseTimeSlots(request.getTimeSlots());

        EngineerSchedule newSchedule = EngineerSchedule.builder()
                .user(user)
                .workDate(request.getWorkDate())
                .status(ScheduleStatus.AVAILABLE)
                .build();

        for (ParsedSlot slot : parsedSlots) {
            newSchedule.addTimeSlot(EngineerScheduleSlot.builder()
                    .startTime(slot.start())
                    .endTime(slot.end())
                    .build());
        }

        EngineerSchedule savedSchedule = engineerScheduleRepository.save(newSchedule);

        return ScheduleResponse.from(savedSchedule);
    }

    private List<ParsedSlot> validAndParseTimeSlots(List<ScheduleRequest.TimeSlotDto> timeSlots) {
        if (timeSlots == null || timeSlots.isEmpty()) {
            throw new IllegalArgumentException("최소 1개 이상의 근무 가능 시간을 입력해주세요.");
        }

        List<ParsedSlot> parsedSlots = timeSlots.stream()
                .map(dto -> new ParsedSlot(LocalTime.parse(dto.getStart()), LocalTime.parse(dto.getEnd())))
                .sorted(Comparator.comparing(ParsedSlot::start))
                .collect(Collectors.toList());

        for (int i = 0; i < parsedSlots.size(); i++) {
            ParsedSlot current = parsedSlots.get(i);

            if (!current.start().isBefore(current.end())) {
                throw new IllegalArgumentException("시작 시간은 종료 시간보다 빨라야 합니다. (" + current.start() + " ~ " + current.end() + ")");
            }

            if (i > 0) {
                ParsedSlot prev = parsedSlots.get(i - 1);
                if (prev.end().isAfter(current.start())) {
                    throw new IllegalArgumentException("근무 가능 시간이 서로 겹칠 수 없습니다. (겹치는 시간: " + current.start() + ")");
                }
            }
        }

        return parsedSlots;
    }

    @Transactional(readOnly = true)
    public List<ScheduleResponse> getMonthlySchedules(Long userId, int year, int month) {   // 월간 내 근무 일정 조회
        LocalDate startDate = LocalDate.of(year, month, 1);
        LocalDate endDate = startDate.withDayOfMonth(startDate.lengthOfMonth());

        List<EngineerSchedule> schedules = engineerScheduleRepository
                .findByUser_IdAndWorkDateBetweenOrderByWorkDateAsc(userId, startDate, endDate);

        return schedules.stream()
                .map(ScheduleResponse::from)
                .collect(Collectors.toList());
    }

    @Transactional
    public void deleteSchedule(Long userId, Long scheduleId) {  // 스케줄 삭제 및 OFF 상태 처리
        EngineerSchedule schedule = engineerScheduleRepository.findById(scheduleId)
                .orElseThrow(() -> new IllegalArgumentException("해당 근무표를 찾을 수 없습니다."));

        if (!schedule.getUser().getId().equals(userId)) {
            throw new IllegalArgumentException("본인의 근무표만 삭제할 수 있습니다.");
        }

        if (schedule.getStatus() == ScheduleStatus.BOOKED) {
            throw new IllegalStateException("이미 A/S가 배정된 근무표는 삭제할 수 없습니다. 대행사에 문의해주세요.");
        }

        schedule.changeScheduleStatus(ScheduleStatus.OFF);
        schedule.getTimeSlots().clear();
    }

    @Transactional(readOnly = true)
    public ScheduleResponse getDailySchedule(Long userId, LocalDate date) {
        // 해당 날짜의 스케줄이 없으면 빈 응답(OFF 상태 등)으로 반환하거나 null 처리
        Optional<EngineerSchedule> scheduleOpt = engineerScheduleRepository
                .findByUser_IdAndWorkDateBetweenOrderByWorkDateAsc(userId, date, date)
                .stream().findFirst();

        if (scheduleOpt.isEmpty()) {
            // 근무표가 없으면 프론트엔드가 에러 대신 '일정 없음'으로 처리할 수 있도록 204 No Content 대신 빈 객체를 주거나 예외 처리
            throw new IllegalArgumentException("해당 날짜에 등록된 근무표가 없습니다.");
        }

        return ScheduleResponse.from(scheduleOpt.get());
    }
}