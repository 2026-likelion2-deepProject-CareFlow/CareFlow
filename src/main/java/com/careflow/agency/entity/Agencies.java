package com.careflow.agency.entity;

import com.careflow.common.enums.AgencyStatus;
import com.careflow.user.entity.User;
import jakarta.persistence.*;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotBlank;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.ColumnDefault;

import java.time.LocalDateTime;

@Entity
@Table(name = "agencies")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED) // JPA 를 위한 기본 생성자
public class Agencies {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(nullable = false, name = "agency_id")
    private Long id; // 대행사 id

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(nullable = false, name = "representative_user_id", unique = true)
    private User representativeId; // 대표 담당자 id
    /*
    대행사 회원가입 시 사용자(대표 담당자 회원가입) 회원가입 + 대행사 회원가입 동시에 진행됨
    1. 우선 대표 담당자 회원가입 부터 진행
    2. 문제없이 완료되면 회원가입된 대표 담당자 id 값을 그대로 가져와서 representativeId 필드에 적재후 대행사 회원가입 진행
     */
    @Column(nullable = false, name = "name", length = 100)
    private String agencyName; // 대행사 상호명

    @Column(nullable = false, name = "business_number", length = 20, unique = true)
    private String businessNumber; // 사업자 등록번호

    @Column(nullable = true, name = "address", length = 255)
    private String agencyAddress; // 소재지 주소

    @Column(nullable = false, name = "agency_fee_rate",
            columnDefinition = "DECIMAL(5,2) DEFAULT 0.00")
    private Double agencyFeeRate; // 대행사 기사 수수료율

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, name = "approval_status")
    @ColumnDefault(value = "PENDING")
    private AgencyStatus approvalStatus; // 관리자 승인 상태

    @Column(nullable = true, name = "approved_at")
    private LocalDateTime approvedAt; // 승인 날짜

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(nullable = true, name = "approved_by")
    private User approvedBy; // 승인한 관리자 id

    @Column(nullable = false, name = "created_at", updatable = false, columnDefinition = "DATETIME DEFAULT CURRENT_TIMESTAMP")
    private LocalDateTime createdAt; // 등록일, 최초 등록이후 데이터 수정 불가

    @Column(nullable = false, name = "updated_at",
            columnDefinition = "DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP")
    private LocalDateTime updatedAt; // 수정일, 데이터 수정 시 현재 시각으로 수정

    // 빌더
    /*
    관리자 승인 상태, 관리자 승인 날짜, 관리자 승인 id : 관리자 승인 시 update 로직으로 갱신
    등록일 : default value
    수정일 : 데이터 수정 시 default value 적용
     */
    @Builder
    public Agencies(User representativeId, String agencyName, String businessNumber, String agencyAddress, Double agencyFeeRate, AgencyStatus approvalStatus, LocalDateTime approvedAt, User approvedBy,LocalDateTime updatedAt) {
        this.representativeId = representativeId;
        this.agencyName = agencyName;
        this.businessNumber = businessNumber;
        this.agencyAddress = agencyAddress;
        this.agencyFeeRate = agencyFeeRate;
        this.approvalStatus = approvalStatus;
        this.approvedAt = approvedAt;
        this.approvedBy = approvedBy;
        this.updatedAt = updatedAt;
    }

    // 생성 메서드
    public static Agencies create(String agencyName, String businessNumber, String agencyAddress, Double agencyFeeRate) {
        return Agencies.builder()
                .agencyName(agencyName)
                .businessNumber(businessNumber)
                .agencyAddress(agencyAddress)
                .agencyFeeRate(agencyFeeRate)
                .build();
    }
}
