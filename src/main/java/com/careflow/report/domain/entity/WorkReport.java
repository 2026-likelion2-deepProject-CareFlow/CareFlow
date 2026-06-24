package com.careflow.report.domain.entity;

import com.careflow.as_request.entity.AsRequest;
import com.careflow.report.domain.enums.DiagnosisResult;
import com.careflow.user.entity.User;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(
        name = "work_reports",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_work_report_request", columnNames = "request_id")
        },
        indexes = {
                @Index(name = "idx_work_report_engineer", columnList = "engineer_id, submitted_at")
        }
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EntityListeners(AuditingEntityListener.class)
public class WorkReport {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "report_id")
    private Long reportId;

    // A/S 요청과 1:1 매핑
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "request_id", nullable = false)
    private AsRequest asRequest;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "engineer_id", nullable = false)
    private User engineer;

    @Enumerated(EnumType.STRING)
    @Column(name = "diagnosis_result", nullable = false, length = 20)
    private DiagnosisResult diagnosisResult;

    @Column(name = "work_duration_min", nullable = false)
    private Integer workDurationMin;

    @Column(name = "final_amount", nullable = false)
    private Integer finalAmount;

    @Column(name = "memo", columnDefinition = "TEXT")
    private String memo;

    @Column(name = "customer_approved", nullable = false, columnDefinition = "TINYINT(1) DEFAULT 0")
    private boolean customerApproved;

    @Column(name = "approved_at")
    private LocalDateTime approvedAt;

    @CreatedDate
    @Column(name = "submitted_at", updatable = false)
    private LocalDateTime submittedAt;

    @LastModifiedDate
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @OneToMany(mappedBy = "report", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<WorkReportPart> parts = new ArrayList<>();

    @Builder
    public WorkReport(AsRequest asRequest, User engineer, DiagnosisResult diagnosisResult, Integer workDurationMin, Integer finalAmount, String memo) {
        this.asRequest = asRequest;
        this.engineer = engineer;
        this.diagnosisResult = diagnosisResult;
        this.workDurationMin = workDurationMin;
        this.finalAmount = finalAmount;
        this.memo = memo;
        this.customerApproved = false; // 기본값
    }

    public void addPart(WorkReportPart part) {
        this.parts.add(part);
        part.assignReport(this);
    }
}