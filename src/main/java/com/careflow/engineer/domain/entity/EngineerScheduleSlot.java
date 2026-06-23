package com.careflow.engineer.domain.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalTime;

@Entity
@Table(
        name = "engineer_schedule_slots",
        indexes = {
                @Index(name = "idx_slot_time", columnList = "schedule_id, start_time, end_time"),
        }
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class EngineerScheduleSlot {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "slot_id")
    private Long slotId;

    @ManyToOne(fetch = FetchType.LAZY)
    // H2 2.x 에서 IDENTITY PK 컬럼으로의 FK 타입 호환 문제(NOT NULL 컬럼 한정) 우회
    // DB 레벨 FK 선언을 생략하되, NOT NULL + JPA 관계는 그대로 유지됨
    @JoinColumn(name = "schedule_id", nullable = false,
            foreignKey = @ForeignKey(ConstraintMode.NO_CONSTRAINT))
    private EngineerSchedule schedule;

    @Column(name = "start_time", nullable = false)
    private LocalTime startTime;

    @Column(name = "end_time", nullable = false)
    private LocalTime endTime;

    @Builder
    public EngineerScheduleSlot(LocalTime startTime, LocalTime endTime) {
        this.startTime = startTime;
        this.endTime = endTime;
    }

    public void assignSchedule(EngineerSchedule schedule) {
        this.schedule = schedule;
    }
}
