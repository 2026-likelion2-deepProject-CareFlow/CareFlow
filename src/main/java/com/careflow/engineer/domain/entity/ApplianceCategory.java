package com.careflow.engineer.domain.entity;

import jakarta.persistence.*;
import lombok.Getter;

@Entity
@Getter
@Table(name = "appliance_categories")
public class ApplianceCategory {    // 임시 ApplianceCategory 클래스 (삭제 예정)
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "category_id")
    private Long categoryId;

    private int depth;
}
