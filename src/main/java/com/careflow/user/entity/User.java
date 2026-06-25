package com.careflow.user.entity;

import com.careflow.agency.entity.Agencies;
import com.careflow.common.enums.Role;
import com.careflow.region.entity.Regions;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;

import java.time.LocalDateTime;

@Entity
@Table(name = "users", uniqueConstraints = @UniqueConstraint(name = "uk_users_email", columnNames = "email"))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@SQLDelete(sql = "UPDATE users SET deleted_at = NOW(), email = CONCAT(email, '_deleted_', user_id) WHERE user_id = ?")
@SQLRestriction("deleted_at IS NULL")
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "user_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "agency_id", nullable = true)
    private Agencies agency;

    @Column(nullable = false, length = 100)
    private String email;

    @Column(name = "password_hash", length = 255)
    private String passwordHash;

    @Column(nullable = false, length = 50)
    private String name;

    @Column(length = 20)
    private String phone;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Role role;

    @Column(length = 20, nullable = false)
    private String status; // ACTIVE / INACTIVE(휴면) / SUSPENDED

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "region_id", nullable = true)
    private Regions regionId;

    @Column(name = "address_detail", length = 100)
    private String addressDetail;

    @Column(name = "last_login_at")
    private LocalDateTime lastLoginAt;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    @Builder
    public User(String email, String passwordHash, String name, String phone,
                Role role, Regions regionId, String addressDetail, Agencies agency) {
        this.email = email;
        this.passwordHash = passwordHash;
        this.name = name;
        this.phone = phone;
        this.role = role;
        this.regionId = regionId;
        this.addressDetail = addressDetail;
        this.status = "ACTIVE";
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
        this.agency = agency;
    }

    public void updateLastLogin() {
        this.lastLoginAt = LocalDateTime.now();
    }
}