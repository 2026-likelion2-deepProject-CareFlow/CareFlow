package com.careflow.account_requests.entity;

import com.careflow.agency.entity.Agencies;
import com.careflow.common.enums.AccountRequestsRole;
import com.careflow.common.enums.AccountRequestsStatus;
import com.careflow.region.entity.Regions; // 호준이의 수정 사항: Regions 임포트
import com.careflow.user.entity.User;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "account_requests")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AccountRequests {

    @Id
    @Column(name = "account_request_id", nullable = false)
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "agency_id", nullable = true)
    private Agencies agency;

    @Column(name = "email", nullable = false, unique = true)
    private String email;

    @Column(name = "password", nullable = false)
    private String password;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "phone", nullable = true)
    private String phone;

    @Enumerated(EnumType.STRING)
    @Column(name = "requests_role", nullable = false)
    private AccountRequestsRole requestsRole;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private AccountRequestsStatus status = AccountRequestsStatus.PENDING;

    @Column(name = "reviewed_at", nullable = true)
    private LocalDateTime reviewedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt = LocalDateTime.now();

    @Column(name = "reject_reason", nullable = true)
    private String rejectReason;

    @Column(name = "address_detail", nullable = true)
    private String addressDetail;

    // 호준이의 수정 사항: Long 대신 Regions 객체로 매핑
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "region_id", nullable = true, unique = true)
    private Regions region;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reviewed_by", nullable = true, unique = true)
    private User reviewedBy;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_user_id", nullable = true, unique = true)
    private User createdUserId;

    @Builder
    public AccountRequests(Agencies agency, String email, String password, String name, String phone, AccountRequestsRole requestsRole, String addressDetail, Regions region, AccountRequestsStatus status, LocalDateTime updatedAt, LocalDateTime reviewedAt, String rejectReason, User reviewedBy, User createdUserId){
        this.agency = agency;
        this.email = email;
        this.password = password;
        this.name = name;
        this.phone = phone;
        this.requestsRole = requestsRole;
        this.addressDetail = addressDetail;
        this.region = region; // 여기도 객체 이름 통일
        if (status != null) this.status = status;
        if (updatedAt != null) this.updatedAt = updatedAt;
        this.reviewedAt = reviewedAt;
        this.rejectReason = rejectReason;
        this.reviewedBy = reviewedBy;
        this.createdUserId = createdUserId;
    }

    public static AccountRequests create(Agencies agency, String email, String password, String name, String phone, AccountRequestsRole requestsRole, String addressDetail, Regions region){
        return AccountRequests.builder()
                .agency(agency)
                .email(email)
                .password(password)
                .name(name)
                .phone(phone)
                .requestsRole(requestsRole)
                .addressDetail(addressDetail)
                .region(region) // 객체 형태로 전달
                .build();
    }
}