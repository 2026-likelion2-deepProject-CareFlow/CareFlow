package com.careflow.appliance.entity;

import com.careflow.common.enums.ApplianceStatus;
import com.careflow.common.enums.RegisterMethod;
import com.careflow.engineer.domain.entity.ApplianceCategory;
import com.careflow.user.entity.User;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "appliances")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Appliance {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "appliance_id")
    private Long id;

    // 소유 고객 (users.user_id FK)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User owner;

    // 가전 카테고리 소분류 (appliance_categories.category_id FK)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "category_id", nullable = false)
    private ApplianceCategory category;

    @Column(name = "brand", nullable = false, length = 50)
    private String brand;

    @Column(name = "model_name", nullable = false, length = 100)
    private String modelName;

    @Column(name = "serial_number", length = 100)
    private String serialNumber;

    @Column(name = "purchase_date")
    private LocalDate purchaseDate;

    @Column(name = "warranty_end_date")
    private LocalDate warrantyEndDate;

    // columnDefinition 삭제하지 말아주세요(H2 DB 테스트에 필요)
    @Enumerated(EnumType.STRING)
    @Column(name = "register_method", nullable = false, length = 10,
            columnDefinition = "ENUM('MANUAL','OCR') DEFAULT 'MANUAL'")
    private RegisterMethod registerMethod;

    @Column(name = "image_url", length = 500)
    private String imageUrl;

    // columnDefinition 삭제하지 말아주세요(H2 DB 테스트에 필요)
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 15,
            columnDefinition = "ENUM('NORMAL','NEED_REPAIR','SOLD') DEFAULT 'NORMAL'")
    private ApplianceStatus status;

    // columnDefinition 삭제하지 말아주세요(H2 DB 테스트에 필요)
    @Column(name = "created_at", nullable = false, updatable = false,
            columnDefinition = "DATETIME DEFAULT CURRENT_TIMESTAMP")
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "updated_at", nullable = false,
            columnDefinition = "DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP")
    private LocalDateTime updatedAt = LocalDateTime.now();

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    @Builder
    public Appliance(User owner, ApplianceCategory category, String brand, String modelName,
                     String serialNumber, LocalDate purchaseDate, LocalDate warrantyEndDate,
                     RegisterMethod registerMethod, String imageUrl) {
        this.owner = owner;
        this.category = category;
        this.brand = brand;
        this.modelName = modelName;
        this.serialNumber = serialNumber;
        this.purchaseDate = purchaseDate;
        this.warrantyEndDate = warrantyEndDate;
        this.registerMethod = registerMethod != null ? registerMethod : RegisterMethod.MANUAL;
        this.imageUrl = imageUrl;
        this.status = ApplianceStatus.NORMAL;
    }

    public static Appliance create(User owner, ApplianceCategory category, String brand,
                                   String modelName, RegisterMethod registerMethod) {
        return Appliance.builder()
                .owner(owner)
                .category(category)
                .brand(brand)
                .modelName(modelName)
                .registerMethod(registerMethod)
                .build();
    }

    // 가전 상태 변경 (수리 필요, 판매 등)
    public void changeStatus(ApplianceStatus status) {
        this.status = status;
        this.updatedAt = LocalDateTime.now();
    }
}
