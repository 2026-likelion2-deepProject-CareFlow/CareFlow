package com.careflow.engineer.repository;

import com.careflow.engineer.domain.entity.EngineerSchedule;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface EngineerScheduleRepository extends JpaRepository<EngineerSchedule, Long> {
    boolean existsByUser_IdAndWorkDate(Long userId, LocalDate workdate);  // 중복 등록 방지 (특정기사가 해당날짜에 이미 등록한 근무표 있는지의 여부)

    List<EngineerSchedule> findByUser_IdAndWorkDateBetweenOrderByWorkDateAsc(Long userId, LocalDate startDate, LocalDate endDate);

    // 근무표 수정(PUT upsert)용 — 특정 기사의 특정 날짜 근무표 단건 조회
    Optional<EngineerSchedule> findByUser_IdAndWorkDate(Long userId, LocalDate workDate);
}
