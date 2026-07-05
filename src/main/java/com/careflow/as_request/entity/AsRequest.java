package com.careflow.as_request.entity;

import com.careflow.agency.entity.Agencies;
import com.careflow.appliance.entity.Appliance;
import com.careflow.common.enums.AssignType;
import com.careflow.common.enums.AsStatus;
import com.careflow.region.entity.Regions;
import com.careflow.report.domain.entity.WorkReport;
import com.careflow.review.entity.Review;
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

    @OneToOne(mappedBy ="asRequest", fetch=FetchType.LAZY)
    private WorkReport workReport;

    @OneToOne(mappedBy = "asRequest", fetch = FetchType.LAZY)
    private Review review;

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
     * 고객용: 예약 확정(ACCEPTED) 이전(PENDING, AGENCY_RECEIVED, ASSIGNED)까지만 취소 가능
     * ASSIGNED는 기사가 배정되었지만 아직 수락(ACCEPTED)하기 전 단계이므로 취소 허용 대상에 포함한다.
     */
    public void cancel(String cancelReason) {
        if (this.status != AsStatus.PENDING
                && this.status != AsStatus.AGENCY_RECEIVED
                && this.status != AsStatus.ASSIGNED) {
            throw new IllegalStateException("예약이 확정된 요청은 취소할 수 없습니다.");
        }
        this.status = AsStatus.CANCELLED;
        this.cancelReason = cancelReason;
        this.updatedAt = LocalDateTime.now();
    }

    public void acceptAssignment() {    // 기사용: 배정 수락 시 ACCEPTED 로 전환
        if (this.status != AsStatus.ASSIGNED) {
            throw new IllegalStateException("기사가 배정된(ASSIGNED) 상태에서만 수락할 수 있습니다.");
        }
        this.status = AsStatus.ACCEPTED;
        this.updatedAt = LocalDateTime.now();
    }

    /**
     * 기사용: 현장으로 출발 (세부 진행상태)
     * ⚠ as_requests.status ENUM 은 PENDING/AGENCY_RECEIVED/ASSIGNED/ACCEPTED/IN_PROGRESS/COMPLETED/PAID/CANCELLED
     *    만 허용한다. ENGINEER_DEPARTED/ENGINEER_ARRIVED 같은 세부 진행상태를 이 컬럼에 저장하면
     *    "Data truncated for column 'status'" 오류가 발생하므로, as_status_logs.to_status 로만 기록한다.
     *    (coarse status 는 ACCEPTED 로 유지)
     */
    public void depart() {
        if (this.status != AsStatus.ACCEPTED) {
            throw new IllegalStateException("배정을 수락한(ACCEPTED) 상태에서만 출발 처리할 수 있습니다. (현재 상태: " + this.status + ")");
        }
        this.updatedAt = LocalDateTime.now();
    }

    /**
     * 기사용: 현장 도착 (세부 진행상태)
     * 출발과 동일하게 as_requests.status 는 ACCEPTED 로 유지하고 세부 상태는 로그로만 관리한다.
     * '출발 -> 도착' 순서 검증은 서비스 계층에서 as_status_logs 최신 상태를 기준으로 수행한다.
     */
    public void arrive() {
        if (this.status != AsStatus.ACCEPTED) {
            throw new IllegalStateException("수락(ACCEPTED) 상태에서만 도착 처리할 수 있습니다. (현재 상태: " + this.status + ")");
        }
        this.updatedAt = LocalDateTime.now();
    }

    /**
     * 기사용: 수리 시작 — 이 시점에 비로소 coarse status 를 IN_PROGRESS 로 전환한다.
     * (출발/도착 동안 status 는 ACCEPTED 로 유지되었으므로 ACCEPTED -> IN_PROGRESS)
     */
    public void startWork() {
        if (this.status != AsStatus.ACCEPTED) {
            throw new IllegalStateException("수락(ACCEPTED) 상태에서만 작업을 시작할 수 있습니다. (현재 상태: " + this.status + ")");
        }
        this.status = AsStatus.IN_PROGRESS;
        this.updatedAt = LocalDateTime.now();
    }


    public void completeWork() {   // 기사용: 작업 완료 보고서 제출 시 상태를 COMPLETED로 변경
        if (this.status != AsStatus.IN_PROGRESS) {
            throw new IllegalStateException("수리 진행 중(IN_PROGRESS)인 상태에서만 작업 완료 처리가 가능합니다. (현재 상태: " + this.status + ")");
        }
        this.status = AsStatus.COMPLETED;
        this.updatedAt = LocalDateTime.now();
    }

    public void markPaid() {   // 고객용: 결제 완료 시 COMPLETED → PAID 전환
        if (this.status != AsStatus.COMPLETED) {
            throw new IllegalStateException("작업이 완료된(COMPLETED) 상태에서만 결제 처리가 가능합니다. (현재 상태: " + this.status + ")");
        }
        this.status = AsStatus.PAID;
        this.updatedAt = LocalDateTime.now();
    }

    // 대행사 관리자가 배정을 취소할 때 — ASSIGNED → AGENCY_RECEIVED(재배정 대기)로 되돌림
    public void revertToAgencyReceived() {
        this.status = AsStatus.AGENCY_RECEIVED;
        this.updatedAt = LocalDateTime.now();
    }

    // 대행사 관리자가 방문 일정을 변경할 때
    public void updateSchedule(LocalDate scheduledDate, String scheduledTime) {
        this.scheduledDate = scheduledDate;
        this.scheduledTime = scheduledTime;
        this.updatedAt = LocalDateTime.now();
    }

    // [기사용] 작업 완료 보고서 승인 요청 취소 시 상태 되돌리기
    public void revertToInProgress() {
        if (this.status != AsStatus.COMPLETED) {
            throw new IllegalStateException("완료(COMPLETED) 상태에서만 진행 중으로 되돌릴 수 있습니다.");
        }
        this.status = AsStatus.IN_PROGRESS;
        this.updatedAt = LocalDateTime.now();
    }
}
