package com.careflow.report.repository;

import com.careflow.report.domain.entity.WorkReport;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface WorkReportRepository extends JpaRepository<WorkReport, Long> {
    // 동일 가전제품(appliance_id)에 대해 작성된 모든 수리 보고서 조회 — 이전 수리 이력 표시용
    List<WorkReport> findByAsRequest_Appliance_Id(Long applianceId);
}
