package com.careflow.engineer.domain.entity;

import com.careflow.engineer.domain.enums.ScheduleStatus;
import com.careflow.user.entity.User;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(
        name = "engineer_schedules",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_eng_schedule", columnNames = {"user_id", "work_date"})
        },
        indexes = {
                @Index(name = "idx_schedule_date_status", columnList = "work_date, status"),
                @Index(name = "idx_schedule_user_date", columnList = "user_id, work_date, status")
        }
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EntityListeners(AuditingEntityListener.class)
public class EngineerSchedule { // 기사 근무표
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    // DDL(CareFlow_v4.sql) 기준 BIGINT UNSIGNED — H2 MySQL mode 및 MySQL 8 모두 호환
    @Column(name = "schedule_id", columnDefinition = "BIGINT UNSIGNED NOT NULL AUTO_INCREMENT")
    private Long scheduleId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "work_date", nullable = false)
    private LocalDate workDate;

    @OneToMany(mappedBy = "schedule", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("startTime ASC")
    private List<EngineerScheduleSlot> timeSlots = new ArrayList<>();

    @Column(name = "status", nullable = false, length = 20)
    @Enumerated(EnumType.STRING)
    private ScheduleStatus status;

    @CreatedDate
    @Column(name = "submitted_at", updatable = false)
    private LocalDateTime submittedAt;

    @LastModifiedDate
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Builder
    public EngineerSchedule(User user, LocalDate workDate, ScheduleStatus status) {
        this.user = user;
        this.workDate = workDate;
        this.status = status != null ? status : ScheduleStatus.AVAILABLE;
    }

    public void changeScheduleStatus (ScheduleStatus newStatus){  // 스케쥴 상태 변경
        if(newStatus == null){
            throw new IllegalArgumentException("새로운 스케쥴 상태 정보가 존재하지 않습니다");
        }

        this.status = newStatus;
    }

    public void addTimeSlot(EngineerScheduleSlot slot){
        this.timeSlots.add(slot);
        slot.assignSchedule(this);
    }
}
