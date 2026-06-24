package com.careflow.appliance.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@NoArgsConstructor  // 기존 테스트(ReflectionTestUtils)가 public 기본 생성자를 사용하므로 유지
@Table(name = "appliance_categories")
public class ApplianceCategory {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "category_id")
    private Integer categoryId;

    private int depth;

    // 테스트 및 초기 데이터 생성용 팩토리
    public static ApplianceCategory create(int depth) {
        ApplianceCategory c = new ApplianceCategory();
        c.depth = depth;
        return c;
    }
}
