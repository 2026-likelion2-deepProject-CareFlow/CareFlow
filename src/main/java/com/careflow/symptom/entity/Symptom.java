package com.careflow.symptom.entity;

import com.careflow.appliance.entity.ApplianceCategory;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(
        name = "symptoms",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_symptom_cat_code", columnNames = {"category_id", "symptom_code"})
        },
        indexes = {
                @Index(name = "idx_symptom_category", columnList = "category_id, is_active"),
                @Index(name = "idx_symptom_active",   columnList = "is_active")
        }
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Symptom {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "symptom_id")
    private Long id;

    // 카테고리별 증상 목록 분리 (appliance_categories depth=2 소분류만)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "category_id", nullable = false)
    private ApplianceCategory category;

    // 증상 코드 (GROUP BY 집계 기준, 예: COOLING_FAIL)
    @Column(name = "symptom_code", nullable = false, length = 50)
    private String symptomCode;

    // 화면 표시용 한글명 (예: 냉방 불량)
    @Column(name = "symptom_name", nullable = false, length = 100)
    private String symptomName;

    // columnDefinition 삭제하지 말아주세요(H2 DB 테스트에 필요)
    @Column(name = "is_active", nullable = false,
            columnDefinition = "TINYINT(1) DEFAULT 1")
    private boolean isActive;

    // columnDefinition 삭제하지 말아주세요(H2 DB 테스트에 필요)
    @Column(name = "created_at", nullable = false, updatable = false,
            columnDefinition = "DATETIME DEFAULT CURRENT_TIMESTAMP")
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "updated_at", nullable = false,
            columnDefinition = "DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP")
    private LocalDateTime updatedAt = LocalDateTime.now();

    @Builder
    public Symptom(ApplianceCategory category, String symptomCode, String symptomName) {
        this.category = category;
        this.symptomCode = symptomCode;
        this.symptomName = symptomName;
        this.isActive = true;
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }
}
