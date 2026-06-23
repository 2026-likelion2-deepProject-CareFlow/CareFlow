package com.careflow.engineer.repository;

import com.careflow.engineer.domain.entity.EngineerSchedule;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;

public interface EngineerScheduleRepository extends JpaRepository<EngineerSchedule, Long> {
    boolean existsByUser_IdAndWorkDate(Long userId, LocalDate workdate);  // 중복 등록 방지 (특정기사가 해당날짜에 이미 등록한 근무표 있는지의 여부)
}
