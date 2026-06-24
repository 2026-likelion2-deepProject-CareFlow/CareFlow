package com.careflow.as_request.entity;

import com.careflow.agency.entity.Agencies;
import com.careflow.appliance.entity.Appliance;
import com.careflow.common.enums.AssignType;
import com.careflow.common.enums.AsStatus;
import com.careflow.region.entity.Regions;
import com.careflow.symptom.entity.Symptom;
import com.careflow.user.entity.User;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "as_requests")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AsRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "request_id")
    private Long id;

    // 신청 고객 (users.user_id FK)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "customer_id", nullable = false)
    private User customer;

    // 대상 가전 (appliances.appliance_id FK)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "appliance_id", nullable = false)
    private Appliance appliance;

    // 접수 대행사 (agencies.agency_id FK) — 배정 전 null
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "agency_id", nullable = true)
    private Agencies agency;

    // 고객 선호 기사 (users.user_id FK) — 수동 배정 시에만 설정
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "preferred_engineer_id", nullable = true)
    private User preferredEngineer;

    // 증상 마스터 (symptoms.symptom_id FK) — v5 스키마: symptom_code VARCHAR 에서 FK 로 변경
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "symptom_id", nullable = false)
    private Symptom symptom;

    @Column(name = "symptom_desc", columnDefinition = "TEXT")
    private String symptomDesc;

    @Column(name = "image_urls", columnDefinition = "JSON")
    private String imageUrls;

    @Enumerated(EnumType.STRING)
    @Column(name = "assign_type", nullable = false, length = 20)
    private AssignType assignType;

    // 방문 지역 (regions.region_id FK, depth=2 구 단위)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "visit_region_id", nullable = false)
    private Regions visitRegion;

    @Column(name = "visit_address_detail", nullable = false, length = 100)
    private String visitAddressDetail;

    @Column(name = "scheduled_date", nullable = false)
    private LocalDate scheduledDate;

    @Column(name = "scheduled_time", nullable = false, length = 10)
    private String scheduledTime;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private AsStatus status;

    @Column(name = "cancel_reason", length = 255)
    private String cancelReason;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Builder
    public AsRequest(User customer, Appliance appliance, Symptom symptom, String symptomDesc,
                     String imageUrls, Regions visitRegion, String visitAddressDetail,
                     LocalDate scheduledDate, String scheduledTime, User preferredEngineer) {
        this.customer = customer;
        this.appliance = appliance;
        this.symptom = symptom;
        this.symptomDesc = symptomDesc;
        this.imageUrls = imageUrls;
        this.assignType = AssignType.MANUAL; // MVP: 수동 배정 고정
        this.visitRegion = visitRegion;
        this.visitAddressDetail = visitAddressDetail;
        this.scheduledDate = scheduledDate;
        this.scheduledTime = scheduledTime;
        this.preferredEngineer = preferredEngineer;
        this.status = AsStatus.PENDING;
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    /**
     * 대행사 배정 시 agency 설정 — PENDING → AGENCY_RECEIVED 전환 시 호출
     */
    public void assignAgency(Agencies agency) {
        this.agency = agency;
        this.status = AsStatus.AGENCY_RECEIVED;
        this.updatedAt = LocalDateTime.now();
    }

    /**
     * 수리 기사 배정 완료 — agency 설정 및 ASSIGNED 상태로 전환
     * AUTO/MANUAL 배정 모두 이 메서드를 통해 상태 변경
     */
    public void processAssignment(Agencies agency) {
        this.agency = agency;
        this.status = AsStatus.ASSIGNED;
        this.updatedAt = LocalDateTime.now();
    }

    /**
     * 고객용: ASSIGNED 이전(PENDING, AGENCY_RECEIVED)까지만 취소 가능
     */
    public void cancel(String cancelReason) {
        if (this.status != AsStatus.PENDING && this.status != AsStatus.AGENCY_RECEIVED) {
            throw new IllegalStateException("기사 배정이 진행된 요청은 취소할 수 없습니다.");
        }
        this.status = AsStatus.CANCELLED;
        this.cancelReason = cancelReason;
        this.updatedAt = LocalDateTime.now();
    }

    public void completeWork() {   // 기사용: 작업 완료 보고서 제출 시 상태를 COMPLETED로 변경
        if (this.status == AsStatus.CANCELLED) {
            throw new IllegalStateException("이미 취소된 A/S 건은 완료 처리할 수 없습니다.");
        }
        this.status = AsStatus.COMPLETED;
        this.updatedAt = LocalDateTime.now();
    }
}
