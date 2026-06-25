package com.careflow.report.repository;
import com.careflow.report.domain.entity.WorkReport;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WorkReportRepository extends JpaRepository<WorkReport, Long> {
    boolean existsByAsRequest_Id(Long requestId);   // 해당 A/S 건에 대한 보고서가 이미 존재하는지 선검사 (중복 제출 방어용)
}