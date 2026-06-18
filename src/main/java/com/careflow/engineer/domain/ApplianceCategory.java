package com.careflow.engineer.domain;

import jakarta.persistence.*;
import lombok.Getter;

@Entity
@Getter
@Table(name = "appliancecategories")
public class ApplianceCategory {    // 임시 ApplianceCategory 클래스 (삭제 예정)
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "categoryid")
    private Long categoryId;

    private int depth;
}
