package com.careflow.engineer.domain.entity;

import com.careflow.user.entity.User;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(
        name = "engineer_expert_brands",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_eng_brand", columnNames = {"engineer_id", "brand_name"})
        },
        indexes = {
                @Index(name = "idx_eng_brand_name", columnList = "brand_name")
        }
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class EngineerExpertBrand {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "engineer_id", nullable = false)
    private User engineer;

    @Column(name = "brand_name", nullable = false, length = 50)
    private String brandName;

    @Builder
    public EngineerExpertBrand(User engineer, String brandName) {
        this.engineer = engineer;
        this.brandName = brandName;
    }
}