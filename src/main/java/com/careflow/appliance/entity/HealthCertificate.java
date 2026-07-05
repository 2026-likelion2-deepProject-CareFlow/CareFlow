package com.careflow.appliance.entity;

import com.careflow.common.enums.PartImportance; // 🎯 도메인 의존성 해결(Nit)은 추후 common 이동 시 일괄 처리
import com.careflow.report.domain.policy.HealthScoreCalculator;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(
        name = "health_certificates",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_cert_appliance", columnNames = "appliance_id")
        },
        indexes = {
                @Index(name = "idx_cert_grade", columnList = "grade, score"),
                @Index(name = "idx_cert_certified", columnList = "is_certified")
        }
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EntityListeners(AuditingEntityListener.class)
public class HealthCertificate {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "cert_id")
    private Long certId;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "appliance_id", nullable = false)
    private Appliance appliance;

    @Column(name = "grade", nullable = false, length = 1, columnDefinition = "CHAR(1) DEFAULT 'E'")
    private String grade;

    @Column(name = "score", nullable = false, columnDefinition = "TINYINT UNSIGNED DEFAULT 0")
    private Integer score;

    @Column(name = "repair_count", nullable = false, columnDefinition = "TINYINT UNSIGNED DEFAULT 0")
    private Integer repairCount;

    @Column(name = "critical_parts_replaced", nullable = false, columnDefinition = "TINYINT UNSIGNED DEFAULT 0")
    private Integer criticalPartsReplaced;

    @Column(name = "last_repaired_at")
    private LocalDateTime lastRepairedAt;

    @Column(name = "is_certified", nullable = false, columnDefinition = "TINYINT(1) DEFAULT 0")
    private boolean isCertified;

    @CreatedDate
    @Column(name = "issued_at", updatable = false)
    private LocalDateTime issuedAt;

    @LastModifiedDate
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Builder
    public HealthCertificate(Appliance appliance) {
        this.appliance = appliance;
        this.grade = "E";
        this.score = 0;
        this.repairCount = 0;
        this.criticalPartsReplaced = 0;
        this.isCertified = false;
    }

    // 🌟 수정: += 누적 방식을 버리고 외부에서 주입받은 절대값으로 재계산
    public void recalculate(int totalRepairCount, int totalCriticalParts,
                            PartImportance worstImportance, LocalDateTime lastRepaired,
                            LocalDate purchaseDate, String minGrade, int minScore) {
        this.repairCount = totalRepairCount;
        this.criticalPartsReplaced = totalCriticalParts;
        this.lastRepairedAt = lastRepaired;

        LocalDateTime now = LocalDateTime.now();

        int axis1 = HealthScoreCalculator.calculateRepairCountScore(this.repairCount);
        int axis2 = HealthScoreCalculator.calculateUsagePeriodScore(purchaseDate, now);
        int axis3 = HealthScoreCalculator.calculatePartImportanceScore(worstImportance);
        int axis4 = HealthScoreCalculator.calculateLastRepairedScore(this.lastRepairedAt, now);

        this.score = axis1 + axis2 + axis3 + axis4;

        if (this.score >= 90) this.grade = "A";
        else if (this.score >= 75) this.grade = "B";
        else if (this.score >= 60) this.grade = "C";
        else if (this.score >= 40) this.grade = "D";
        else this.grade = "E";

        this.isCertified = (this.score >= minScore && this.grade.compareTo(minGrade) <= 0);
        this.updatedAt = LocalDateTime.now();
    }
}