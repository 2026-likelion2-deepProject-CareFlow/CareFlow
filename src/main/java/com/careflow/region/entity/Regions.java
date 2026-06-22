package com.careflow.region.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "regions")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Regions {

    @Id
    @Column(name = "region_id", nullable = false, columnDefinition = "INT UNSIGNED")
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_id", nullable = true)
    private Regions parentId; // 자기 참조

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "depth", nullable = false, columnDefinition = "TINYINT DEFAULT 1")
    private int depth;

    @Column(name = "sort_order", nullable = false, columnDefinition = "INT DEFAULT 0")
    private int sortOrder;

    @Builder
    public Regions(String name, Regions parentId, int depth, int sortOrder) {
        this.name = name;
        this.parentId = parentId;
        this.depth = depth;
        this.sortOrder = sortOrder;
    }

    public static Regions create(String name, Regions parentId, int depth, int sortOrder) {
        return Regions.builder()
                .name(name)
                .parentId(parentId)
                .depth(depth)
                .sortOrder(sortOrder)
                .build();
    }
}
