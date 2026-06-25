package com.careflow.appliance.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(
        name = "appliance_categories",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_category_name", columnNames = "name")
        },
        indexes = {
                @Index(name = "idx_category_parent", columnList = "parent_id")
        }
)
public class ApplianceCategory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "category_id")
    private Integer categoryId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_id")
    private ApplianceCategory parent;

    @OneToMany(mappedBy = "parent", cascade = CascadeType.ALL, orphanRemoval = false)
    private List<ApplianceCategory> children = new ArrayList<>();

    @Column(name = "name", nullable = false, length = 50)
    private String name;

    @Column(name = "depth", nullable = false)
    private int depth;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    private ApplianceCategory(String name, ApplianceCategory parent, int sortOrder) {
        this.name = name;
        this.parent = parent;
        this.sortOrder = sortOrder;
        this.depth = (parent == null) ? 1 : 2;
    }

    public static ApplianceCategory createRoot(String name, int sortOrder) {
        return new ApplianceCategory(name, null, sortOrder);
    }

    public static ApplianceCategory createChild(String name, ApplianceCategory parent, int sortOrder) {
        if (parent == null) {
            throw new IllegalArgumentException("하위 카테고리는 부모 카테고리가 필요합니다.");
        }
        if (parent.getDepth() != 1) {
            throw new IllegalArgumentException("소분류의 부모는 depth=1 카테고리만 가능합니다.");
        }
        return new ApplianceCategory(name, parent, sortOrder);
    }

    public void update(String name, Integer sortOrder) {
        if (name != null && !name.isBlank()) {
            this.name = name;
        }
        if (sortOrder != null) {
            this.sortOrder = sortOrder;
        }
    }

    public void changeParent(ApplianceCategory newParent) {
        if (newParent != null && newParent.getDepth() != 1) {
            throw new IllegalArgumentException("부모 카테고리는 depth=1만 가능합니다.");
        }

        this.parent = newParent;
        this.depth = (newParent == null) ? 1 : 2;
    }

    public boolean isRoot() {
        return this.parent == null;
    }

    public boolean isChild() {
        return this.parent != null;
    }
}