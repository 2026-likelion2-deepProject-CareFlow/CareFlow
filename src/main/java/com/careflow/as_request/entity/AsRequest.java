package com.careflow.as_request.entity;

import com.careflow.common.enums.AssignType;
import com.careflow.common.enums.AsStatus;
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

    @Column(name = "customer_id", nullable = false)
    private Long customerId;

    @Column(name = "appliance_id", nullable = false)
    private Long applianceId;

    @Column(name = "agency_id")
    private Long agencyId;

    @Column(name = "preferred_engineer_id")
    private Long preferredEngineerId;

    @Column(name = "symptom_code", nullable = false, length = 50)
    private String symptomCode;

    @Column(name = "symptom_desc", columnDefinition = "TEXT")
    private String symptomDesc;

    @Column(name = "image_urls", columnDefinition = "JSON")
    private String imageUrls;

    @Enumerated(EnumType.STRING)
    @Column(name = "assign_type", nullable = false, length = 20)
    private AssignType assignType;

    @Column(name = "visit_region_id", nullable = false)
    private Integer visitRegionId;

    @Column(name = "visit_address_detail", nullable = false, length = 100)
    private String visitAddressDetail;

    @Column(name = "scheduled_date", nullable = false)
    private LocalDate scheduledDate;

    @Column(name = "scheduled_time", nullable = false, length = 10)
    private String scheduledTime;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AsStatus status;

    @Column(name = "cancel_reason", length = 255)
    private String cancelReason;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Builder
    public AsRequest(Long customerId, Long applianceId, String symptomCode, String symptomDesc,
                     String imageUrls, Integer visitRegionId, String visitAddressDetail,
                     LocalDate scheduledDate, String scheduledTime, Long preferredEngineerId) {
        this.customerId = customerId;
        this.applianceId = applianceId;
        this.symptomCode = symptomCode;
        this.symptomDesc = symptomDesc;
        this.imageUrls = imageUrls;
        this.assignType = AssignType.MANUAL;
        this.visitRegionId = visitRegionId;
        this.visitAddressDetail = visitAddressDetail;
        this.scheduledDate = scheduledDate;
        this.scheduledTime = scheduledTime;
        this.preferredEngineerId = preferredEngineerId;
        this.status = AsStatus.PENDING;
        this.createdAt = LocalDateTime.now();
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
}