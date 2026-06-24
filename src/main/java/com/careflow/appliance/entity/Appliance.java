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

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "category_id", nullable = false)
    private ApplianceCategory category;

    @Column(name = "brand", nullable = false, length = 50)
    private String brand;

    @Column(name = "model_name", length = 100)
    private String modelName;

    @Column(name = "serial_number", length = 100)
    private String serialNumber;

    @Column(name = "purchase_date")
    private LocalDate purchaseDate;

    @Column(name = "warranty_end_date")
    private LocalDate warrantyEndDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "register_method", nullable = false, length = 10,
            columnDefinition = "ENUM('MANUAL','OCR') DEFAULT 'MANUAL'")
    private RegisterMethod registerMethod;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 15,
            columnDefinition = "ENUM('NORMAL','NEED_REPAIR','SOLD') DEFAULT 'NORMAL'")
    private ApplianceStatus status;

    @Column(name = "created_at", nullable = false, updatable = false,
            columnDefinition = "DATETIME DEFAULT CURRENT_TIMESTAMP")
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false,
            columnDefinition = "DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP")
    private LocalDateTime updatedAt;

    // 논리 삭제용 — null이면 활성, 값이 있으면 삭제된 가전
    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    @Builder
    public Appliance(User user, ApplianceCategory category, String brand, String modelName,
                     String serialNumber, LocalDate purchaseDate, LocalDate warrantyEndDate,
                     RegisterMethod registerMethod) {
        this.user = user;
        this.category = category;
        this.brand = brand;
        this.modelName = modelName;
        this.serialNumber = serialNumber;
        this.purchaseDate = purchaseDate;
        this.warrantyEndDate = warrantyEndDate;
        this.registerMethod = registerMethod != null ? registerMethod : RegisterMethod.MANUAL;
        this.status = ApplianceStatus.NORMAL;
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    public static Appliance create(User user, ApplianceCategory category, String brand,
                                   String modelName, String serialNumber,
                                   LocalDate purchaseDate, LocalDate warrantyEndDate,
                                   RegisterMethod registerMethod) {
        return Appliance.builder()
                .user(user)
                .category(category)
                .brand(brand)
                .modelName(modelName)
                .serialNumber(serialNumber)
                .purchaseDate(purchaseDate)
                .warrantyEndDate(warrantyEndDate)
                .registerMethod(registerMethod)
                .build();
    }

    // 논리 삭제 처리
    public void delete() {
        this.deletedAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    // 상태 변경 (수리 필요, 판매 완료 등)
    public void changeStatus(ApplianceStatus newStatus) {
        this.status = newStatus;
        this.updatedAt = LocalDateTime.now();
    }
}
