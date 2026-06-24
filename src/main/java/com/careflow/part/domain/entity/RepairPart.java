package com.careflow.part.domain.entity;

import com.careflow.report.domain.enums.PartImportance;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(
        name = "repair_parts",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_repair_parts_code", columnNames = "part_code")
        },
        indexes = {
                @Index(name = "idx_repair_parts_importance", columnList = "importance")
        }
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RepairPart {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "repair_part_id")
    private Long repairPartId;

    @Column(name = "part_code", nullable = false, length = 50)
    private String partCode;

    @Column(name = "part_name", nullable = false, length = 100)
    private String partName;

    @Column(name = "spec", length = 200)
    private String spec;

    @Enumerated(EnumType.STRING)
    @Column(name = "importance", nullable = false, length = 20)
    private PartImportance importance;

    @Column(name = "base_unit_price", nullable = false)
    private Integer baseUnitPrice;
}