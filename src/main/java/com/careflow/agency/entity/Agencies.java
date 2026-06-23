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
    @JoinColumn(nullable = true, name = "representative_user_id", unique = true)
    private User representativeId; // 대표 담당자 id
    /*
    슈퍼 계정 생성 요청 승인 전까지는 null — 승인 시점에 approve() 메서드로 설정됨
    (최초 대행사 등록 시 슈퍼 계정이 아직 존재하지 않으므로 nullable = true)
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

    // columnDefinition 삭제하지 말아주세요(H2 DB 테스트에 필요)
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, name = "approval_status",
            columnDefinition = "ENUM('PENDING', 'APPROVED', 'REJECTED') DEFAULT 'PENDING'")
    private AgencyStatus approvalStatus = AgencyStatus.PENDING; // JPA INSERT 시 null 방지용 기본값

    @Column(nullable = true, name = "approved_at")
    private LocalDateTime approvedAt; // 승인 날짜

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(nullable = true, name = "approved_by")
    private User approvedBy; // 승인한 관리자 id

    // columnDefinition 삭제하지 말아주세요(H2 DB 테스트에 필요)
    // = LocalDateTime.now() : H2 에서 DB DEFAULT 가 적용되지 않으므로 Java 레벨에서 기본값 보장
    @Column(nullable = false, name = "created_at", updatable = false, columnDefinition = "DATETIME DEFAULT CURRENT_TIMESTAMP")
    private LocalDateTime createdAt = LocalDateTime.now(); // 등록일, 최초 등록이후 데이터 수정 불가

    @Column(nullable = false, name = "updated_at",
            columnDefinition = "DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP")
    private LocalDateTime updatedAt = LocalDateTime.now(); // 수정일, 데이터 수정 시 현재 시각으로 수정

    // 빌더
    /*
    관리자 승인 상태, 관리자 승인 날짜, 관리자 승인 id : 관리자 승인 시 update 로직으로 갱신
    등록일 : default value
    수정일 : 데이터 수정 시 default value 적용
     */
    @Builder
    public Agencies(User representativeId, String agencyName, String businessNumber, String agencyAddress, Double agencyFeeRate, AgencyStatus approvalStatus, LocalDateTime approvedAt, User approvedBy, LocalDateTime updatedAt) {
        this.representativeId = representativeId;
        this.agencyName = agencyName;
        this.businessNumber = businessNumber;
        this.agencyAddress = agencyAddress;
        this.agencyFeeRate = agencyFeeRate;
        // null 방지: builder 에서 미지정 시 기본값 적용 (H2 NOT NULL 대응 — DB DEFAULT 는 명시적 NULL 전달 시 적용 안 됨)
        this.approvalStatus = approvalStatus != null ? approvalStatus : AgencyStatus.PENDING;
        this.approvedAt = approvedAt;
        this.approvedBy = approvedBy;
        this.updatedAt = updatedAt != null ? updatedAt : LocalDateTime.now();
    }

    // 슈퍼 계정 승인 시 대행사 상태 갱신 — 더티 체킹으로 UPDATE 처리
    public void approve(User approvedBy, User representative) {
        this.approvalStatus = AgencyStatus.APPROVED;
        this.approvedBy = approvedBy;
        this.approvedAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
        this.representativeId = representative;
    }

    // 슈퍼 계정 요청 거부 시 대행사 상태 REJECTED 로 전환
    public void reject() {
        this.approvalStatus = AgencyStatus.REJECTED;
        this.updatedAt = LocalDateTime.now();
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
