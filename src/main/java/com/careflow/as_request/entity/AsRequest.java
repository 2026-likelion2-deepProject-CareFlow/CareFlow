package com.careflow.as_request.entity;

import com.careflow.common.enums.AsStatus;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

@Entity
@Table(name = "as_requests")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AsRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(nullable = false, name = "as_request_id")
    private Long id;

    @Column(nullable = false, name = "customer_user_id")
    private Long customerId; // 신청한 고객 ID (윤혜민 담당 User 엔티티의 id)

    @Column(nullable = false, name = "appliance_id")
    private Long applianceId; // 고장 난 가전제품 ID

    @Column(nullable = false, name = "title", length = 100)
    private String title; // 요청 제목

    @Column(nullable = false, name = "description", columnDefinition = "TEXT")
    private String description; // 고장 증상 상세 설명

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, name = "as_status")
    private AsStatus asStatus; // 상태값 (PENDING, ASSIGNED 등)

    @Column(name = "assigned_engineer_id")
    private Long engineerId; // 배정된 수리기사 ID (서호준 작업할당 시 채워짐)

    @Column(nullable = false, name = "created_at", updatable = false, columnDefinition = "DATETIME DEFAULT CURRENT_TIMESTAMP")
    private LocalDateTime createdAt;

    @Column(nullable = false, name = "updated_at", columnDefinition = "DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP")
    private LocalDateTime updatedAt;

    @Builder
    public AsRequest(Long customerId, Long applianceId, String title, String description) {
        this.customerId = customerId;
        this.applianceId = applianceId;
        this.title = title;
        this.description = description;
        this.asStatus = AsStatus.PENDING; // 최초 생성 시에는 무조건 접수 대기(PENDING)
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    // [고객 기능] 대행사가 접수하기 전(PENDING) 상태일 때만 고객이 직접 취소 가능
    public void cancel() {
        if (this.asStatus != AsStatus.PENDING) {
            throw new IllegalStateException("대행사 접수가 진행되었거나 완료된 요청은 취소할 수 없습니다.");
        }
        this.asStatus = AsStatus.CANCELLED;
        this.updatedAt = LocalDateTime.now();
    }
}