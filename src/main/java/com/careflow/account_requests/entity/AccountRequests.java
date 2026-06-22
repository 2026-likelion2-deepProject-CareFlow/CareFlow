package com.careflow.account_requests.entity;

import com.careflow.agency.entity.Agencies;
import com.careflow.common.enums.AccountRequestsRole;
import com.careflow.common.enums.AccountRequestsStatus;
import com.careflow.region.entity.Regions;
import com.careflow.user.entity.User;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.ColumnDefault;

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
    @JoinColumn(nullable = true, name = "agency_id")
    private Agencies agencyId;

    @Column(name = "email", nullable = false, unique = true)
    private String email;

    @Column(name = "password", nullable = false)
    private String password;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "phone", nullable = true)
    private String phone;

    @Column(name = "requests_role", nullable = false)
    private AccountRequestsRole requestsRole;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    @ColumnDefault(value = "PENDING")
    private AccountRequestsStatus status;

    @Column(name = "reviewed_at", nullable = true)
    private LocalDateTime reviewedAt;

    @Column(name = "created_at", nullable = false, columnDefinition = "DATETIME DEFAULT CURRENT_TIMESTAMP")
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false, columnDefinition = "DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP")
    private LocalDateTime updatedAt;

    @Column(name = "reject_reason", nullable = true)
    private  String rejectReason;

    @Column(name = "address_detail", nullable = true)
    private String addressDetail;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "region_id", nullable = true, unique = true)
    private Regions regionId;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reviewed_by", nullable = true, unique = true)
    private User reviewedBy; // 승인한 관리자 id

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_user_id", nullable = true, unique = true)
    private User createdUserId; // 해당 요청을 통해 생성된 회원 id

    @Builder
    public AccountRequests(Agencies agencies, String email, String password, String name, String phone, AccountRequestsRole requestsRole, String addressDetail, Regions regionId, AccountRequestsStatus status, LocalDateTime updatedAt, LocalDateTime reviewedAt, String rejectReason, User reviewedBy, User createdUserId){
        this.agencyId = agencies;
        this.email = email;
        this.password = password;
        this.name = name;
        this.phone = phone;
        this.requestsRole = requestsRole;
        this.addressDetail = addressDetail;
        this.regionId = regionId;

        this.status = status;
        this.updatedAt = updatedAt;
        this.reviewedAt = reviewedAt;
        this.rejectReason = rejectReason;
        this.reviewedBy = reviewedBy;
        this.createdUserId = createdUserId;
    }

    public static AccountRequests create(Agencies agencies, String email, String password, String name, String phone, AccountRequestsRole requestsRole, String addressDetail, Regions regionId){
        return AccountRequests.builder()
                .agencies(agencies)
                .email(email)
                .password(password)
                .name(name)
                .phone(phone)
                .requestsRole(requestsRole)
                .addressDetail(addressDetail)
                .regionId(regionId)
                .build();
    }
}
