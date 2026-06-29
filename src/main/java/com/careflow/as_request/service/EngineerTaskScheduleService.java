package com.careflow.as_request.service;

import com.careflow.as_request.dto.EngineerTaskScheduleResponse;
import com.careflow.assignment.repository.AsAssignmentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

/**
 * 수리 기사 A/S 작업 일정 조회 서비스
 * - 기사 본인이 특정 날짜의 배정된 작업을 확인할 때 사용
 * - REJECTED 건은 제외하고 반환
 */
@Service
@RequiredArgsConstructor
public class EngineerTaskScheduleService {

    private final AsAssignmentRepository asAssignmentRepository;

    @Transactional(readOnly = true)
    public List<EngineerTaskScheduleResponse> getTaskSchedule(Long engineerUserId, LocalDate date) {
        return asAssignmentRepository.findTaskSchedule(engineerUserId, date)
                .stream()
                .map(EngineerTaskScheduleResponse::from)
                .toList();
    }
}
