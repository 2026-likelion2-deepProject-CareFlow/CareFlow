package com.careflow.assignment.entity;

import com.careflow.agency.entity.Agencies;
import com.careflow.as_request.entity.AsRequest;
import com.careflow.common.enums.AssignType;
import com.careflow.user.entity.User;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(
        name = "as_assignments",
        indexes = {
                @Index(name = "idx_assignment_request",  columnList = "request_id, status"),
                @Index(name = "idx_assignment_engineer", columnList = "engineer_id, status"),
                @Index(name = "idx_assignment_agency",   columnList = "agency_id, status")
        }
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AsAssignment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "assignment_id")
    private Long id;

    // 대상 A/S 신청 (as_requests.request_id FK)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "request_id", nullable = false)
    private AsRequest asRequest;

    // 배정된 수리 기사 (users.user_id FK)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "engineer_id", nullable = false)
    private User engineer;

    // 배차 대행사 (agencies.agency_id FK)
    // v5 스키마: assigned_by 컬럼 삭제됨
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "agency_id", nullable = false)
    private Agencies agency;

    @Enumerated(EnumType.STRING)
    @Column(name = "assign_method", nullable = false, length = 10,
            columnDefinition = "ENUM('AUTO','MANUAL') DEFAULT 'MANUAL'")
    private AssignType assignMethod;

    // columnDefinition 삭제하지 말아주세요(H2 DB 테스트에 필요)
    @Column(name = "status", nullable = false, length = 15,
            columnDefinition = "ENUM('WAITING','ACCEPTED','REJECTED','COMPLETED') DEFAULT 'WAITING'")
    private String status;

    @Column(name = "accepted_at")
    private LocalDateTime acceptedAt;

    @Column(name = "rejected_at")
    private LocalDateTime rejectedAt;

    @Column(name = "reject_reason", length = 255)
    private String rejectReason;

    // columnDefinition 삭제하지 말아주세요(H2 DB 테스트에 필요)
    @Column(name = "assigned_at", nullable = false, updatable = false,
            columnDefinition = "DATETIME DEFAULT CURRENT_TIMESTAMP")
    private LocalDateTime assignedAt = LocalDateTime.now();

    @Builder
    public AsAssignment(AsRequest asRequest, User engineer, Agencies agency, AssignType assignMethod) {
        this.asRequest = asRequest;
        this.engineer = engineer;
        this.agency = agency;
        this.assignMethod = assignMethod;
        this.status = "WAITING";
    }

    public static AsAssignment create(AsRequest asRequest, User engineer,
                                      Agencies agency, AssignType assignMethod) {
        return AsAssignment.builder()
                .asRequest(asRequest)
                .engineer(engineer)
                .agency(agency)
                .assignMethod(assignMethod)
                .build();
    }
}
