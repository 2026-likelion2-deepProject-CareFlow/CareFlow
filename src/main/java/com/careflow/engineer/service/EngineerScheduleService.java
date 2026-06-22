package com.careflow.engineer.service;

import com.careflow.common.enums.Role;
import com.careflow.engineer.domain.entity.EngineerProfile;
import com.careflow.engineer.domain.entity.EngineerSchedule;
import com.careflow.engineer.domain.enums.ScheduleStatus;
import com.careflow.engineer.dto.ScheduleRequest;
import com.careflow.engineer.dto.ScheduleResponse;
import com.careflow.engineer.repository.EngineerProfileRepository;
import com.careflow.engineer.repository.EngineerScheduleRepository;
import com.careflow.user.entity.User;
import com.careflow.user.repository.UserRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalTime;
import java.util.Comparator;
import java.util.List;

@Service
@RequiredArgsConstructor
public class EngineerScheduleService {
    private final EngineerScheduleRepository engineerScheduleRepository;
    private final UserRepository userRepository;
    private final EngineerProfileRepository engineerProfileRepository;
    private final ObjectMapper objectMapper;

    @Transactional
    public ScheduleResponse createSchedule(Long userId, ScheduleRequest request) {  // 근무표 등록
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("유저 정보가 존재하지 않습니다."));

        if (user.getRole() != Role.ENGINEER) {
            throw new IllegalArgumentException("기사 권한만 등록 가능합니다.");
        }

        EngineerProfile profile = engineerProfileRepository.findByUser_UserId(userId)
                .orElseThrow(() -> new IllegalArgumentException("프로필 정보가 존재하지 않습니다."));

        if (profile.getCategory() == null) {
            throw new IllegalArgumentException("전문 분야 카테고리 등 프로필 필수 정보를 먼저 완성해주세요.");
        }

        if (engineerScheduleRepository.existsByUser_UserIdAndWorkDate(userId, request.getWorkDate())) {
            throw new IllegalArgumentException("해당 날짜에 이미 근무표가 존재합니다.");
        }

        validTimeSlots(request.getTimeSlots());

        String timeSlotsString;
        try {
            timeSlotsString = objectMapper.writeValueAsString(request.getTimeSlots());
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("시간표 데이터 변환 중 오류가 발생했습니다.");
        }

        EngineerSchedule newSchedule = EngineerSchedule.builder()
                .user(user)
                .workDate(request.getWorkDate())
                .timeSlots(timeSlotsString)
                .status(ScheduleStatus.AVAILABLE)
                .build();

        EngineerSchedule savedSchedule = engineerScheduleRepository.save(newSchedule);

        return ScheduleResponse.from(savedSchedule);
    }


    private void validTimeSlots(List<ScheduleRequest.TimeSlotDto> timeSlots){    // 시간표 논리 검증
        if(timeSlots == null || timeSlots.isEmpty()){
            throw new IllegalArgumentException("최소 1개 이상의 근무 가능 시간을 입력해주세요.");
        }

        timeSlots.sort(Comparator.comparing(slot -> LocalTime.parse(slot.getStart())));

        for(int i = 0; i < timeSlots.size(); i++){
            LocalTime start = LocalTime.parse(timeSlots.get(i).getStart());
            LocalTime end = LocalTime.parse(timeSlots.get(i).getEnd());

            if (!start.isBefore(end)) {
                throw new IllegalArgumentException("시작 시간은 종료 시간보다 빨라야 합니다. (" + start + " ~ " + end + ")");
            }

            // 구간 겹치는지 검사
            if (i > 0) {
                LocalTime prevEnd = LocalTime.parse(timeSlots.get(i - 1).getEnd());

                if (prevEnd.isAfter(start)) {
                    throw new IllegalArgumentException("근무 가능 시간이 서로 겹칠 수 없습니다. (겹치는 시간: " + start + ")");
                }
            }
        }
    }
}
