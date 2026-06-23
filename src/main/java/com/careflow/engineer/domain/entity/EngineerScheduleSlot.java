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
    @JoinColumn(name = "schedule_id", nullable = false)
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
