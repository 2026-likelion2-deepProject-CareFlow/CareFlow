package com.careflow.report.domain.entity;

import com.careflow.part.domain.entity.RepairPart;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

@Entity
@Table(
        name = "work_report_parts",
        indexes = {
                @Index(name = "idx_parts_report", columnList = "report_id"),
                @Index(name = "idx_parts_repair", columnList = "repair_part_id")
        }
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EntityListeners(AuditingEntityListener.class)
public class WorkReportPart {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "part_id")
    private Long partId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "report_id", nullable = false)
    private WorkReport report;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "repair_part_id", nullable = false)
    private RepairPart repairPart;

    @Column(name = "quantity", nullable = false, columnDefinition = "TINYINT UNSIGNED DEFAULT 1")
    private Integer quantity;

    @Column(name = "applied_unit_price")
    private Integer appliedUnitPrice;

    @CreatedDate
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @Builder
    public WorkReportPart(RepairPart repairPart, Integer quantity, Integer appliedUnitPrice) {
        this.repairPart = repairPart;
        this.quantity = quantity;
        this.appliedUnitPrice = appliedUnitPrice;
    }

    public void assignReport(WorkReport report) {
        this.report = report;
    }
}