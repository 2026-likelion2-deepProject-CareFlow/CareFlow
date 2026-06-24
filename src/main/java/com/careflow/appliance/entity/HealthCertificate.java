package com.careflow.appliance.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

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

    public void updateHealthGrade(String newGrade, int newScore, boolean isCriticalReplaced) {
        this.grade = newGrade;
        this.score = newScore;
        this.repairCount += 1;
        if (isCriticalReplaced) {
            this.criticalPartsReplaced += 1;
        }
        this.lastRepairedAt = LocalDateTime.now();
        this.isCertified = (this.score >= 75 && (this.grade.equals("A") || this.grade.equals("B")));
    }
}