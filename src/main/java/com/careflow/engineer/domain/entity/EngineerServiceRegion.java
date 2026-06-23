package com.careflow.engineer.domain.entity;

import com.careflow.region.entity.Regions;
import com.careflow.user.entity.User;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(
        name = "engineer_service_regions",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_eng_region", columnNames = {"engineer_id", "region_id"})
        },
        indexes = {
                @Index(name = "idx_eng_region_reg", columnList = "region_id")
        }
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class EngineerServiceRegion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "engineer_id", nullable = false)
    private User engineer;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "region_id", nullable = false)
    private Regions region;

    @Builder
    public EngineerServiceRegion(User engineer, Regions region) {
        this.engineer = engineer;
        this.region = region;
    }
}