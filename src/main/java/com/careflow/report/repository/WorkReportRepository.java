package com.careflow.report.repository;
import com.careflow.report.domain.entity.WorkReport;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WorkReportRepository extends JpaRepository<WorkReport, Long> {}