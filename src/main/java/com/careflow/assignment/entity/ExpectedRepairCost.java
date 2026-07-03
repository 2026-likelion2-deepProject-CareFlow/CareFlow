package com.careflow.assignment.entity;

import com.careflow.appliance.entity.ApplianceCategory;
import com.careflow.symptom.entity.Symptom;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(
        name = "expected_repair_costs",
        indexes = {
                @Index(name = "idx_repair_cost_lookup", columnList = "symptom_id")
        }
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ExpectedRepairCost {

    /** 테스트 픽스처 생성용 정적 팩토리 (운영 코드에서는 Quartz 배치가 직접 INSERT) */
    public static ExpectedRepairCost createForTest(ApplianceCategory category, Symptom symptom,
                                                   Integer avgCost, Integer sampleCount) {
        ExpectedRepairCost cost = new ExpectedRepairCost();
        cost.category = category;
        cost.symptom = symptom;
        cost.avgCost = avgCost;
        cost.sampleCount = sampleCount;
        return cost;
    }


    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "repair_cost_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "category_id", nullable = false)
    private ApplianceCategory category;

    // Quartz 배치로 집계되는 증상별 예상 비용 (1:1 unique)
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "symptom_id", nullable = false,
            foreignKey = @ForeignKey(name = "FK_symptoms_TO_expected_repair_costs"))
    private Symptom symptom;

    // Quartz 집계 전까지 null — 프론트에서 "데이터 수집 중" 표시
    @Column(name = "avg_cost")
    private Integer avgCost;

    @Column(name = "sample_count", columnDefinition = "INT DEFAULT 0")
    private Integer sampleCount;

    @Column(name = "last_calculated_at")
    private LocalDateTime lastCalculatedAt;

    // columnDefinition 삭제하지 말아주세요(H2 DB 테스트에 필요)
    @Column(name = "created_at", nullable = false, updatable = false,
            columnDefinition = "DATETIME DEFAULT CURRENT_TIMESTAMP")
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "updated_at", nullable = false,
            columnDefinition = "DATETIME DEFAULT CURRENT_TIMESTAMP")
    private LocalDateTime updatedAt = LocalDateTime.now();

    // 관리자 수동 보정을 위한 메서드 추가 (더티 체킹용)
    public void updateAvgCost(Integer newAvgCost) {
        this.avgCost = newAvgCost;
        this.updatedAt = java.time.LocalDateTime.now();
    }
}
