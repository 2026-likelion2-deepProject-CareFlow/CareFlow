package com.careflow.appliance.entity;

import com.careflow.report.domain.enums.PartImportance; // 🎯 도메인 의존성 해결(Nit)은 추후 common 이동 시 일괄 처리
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
import java.time.temporal.ChronoUnit;

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

    public void calculateAndUpdateHealth(PartImportance maxImportance, LocalDate purchaseDate) {
        this.repairCount += 1;
        if (maxImportance == PartImportance.CRITICAL) {
            this.criticalPartsReplaced += 1;
        }

        int axis1 = calculateRepairCountScore(this.repairCount);
        int axis2 = calculateUsagePeriodScore(purchaseDate);
        int axis3 = calculatePartImportanceScore(maxImportance);
        int axis4 = calculateLastRepairedScore(this.lastRepairedAt);

        this.score = axis1 + axis2 + axis3 + axis4;

        if (this.score >= 90) this.grade = "A";
        else if (this.score >= 75) this.grade = "B";
        else if (this.score >= 60) this.grade = "C";
        else if (this.score >= 40) this.grade = "D";
        else this.grade = "E";

        this.isCertified = (this.score >= 75 && (this.grade.equals("A") || this.grade.equals("B")));
        this.lastRepairedAt = LocalDateTime.now();
    }

    private int calculateRepairCountScore(int count) {
        if (count <= 1) return 25;
        if (count == 2) return 20;
        if (count == 3) return 15;
        if (count == 4) return 8;
        return 0;
    }

    private int calculateUsagePeriodScore(LocalDate purchaseDate) {
        if (purchaseDate == null) return 25;
        long years = ChronoUnit.YEARS.between(purchaseDate, LocalDate.now());
        if (years < 1) return 25;
        if (years < 3) return 20;
        if (years < 5) return 15;
        if (years < 8) return 8;
        return 0;
    }

    private int calculatePartImportanceScore(PartImportance importance) {
        if (importance == null) return 25;
        return switch (importance) {
            case MINOR -> 20;
            case NORMAL -> 15;
            case MAJOR -> 8;
            case CRITICAL -> 0;
        };
    }

    private int calculateLastRepairedScore(LocalDateTime lastRepaired) {
        if (lastRepaired == null) return 25;
        long months = ChronoUnit.MONTHS.between(lastRepaired, LocalDateTime.now());
        if (months >= 24) return 20;
        if (months >= 12) return 15;
        if (months >= 6) return 8;
        return 0;
    }
}