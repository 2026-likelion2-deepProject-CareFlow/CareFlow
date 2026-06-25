package com.careflow.engineer.repository;

import com.careflow.engineer.domain.entity.EngineerProfile;
import com.careflow.engineer.domain.enums.ScheduleStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;

@Repository
public interface EngineerProfileRepository extends JpaRepository<EngineerProfile, Long> {

    boolean existsByUser_Id(Long userId);
    Optional<EngineerProfile> findByUser_Id(Long userId);

    /**
     * AUTO 배정 - Fallback 0: 스케줄 + 브랜드 + 카테고리 + 서비스 지역 (4가지 조건 모두)
     * avg_rating 내림차순으로 정렬
     */
    @Query("SELECT DISTINCT ep FROM EngineerProfile ep " +
           "JOIN ep.user u " +
           "JOIN EngineerSchedule es ON es.user = u " +
           "JOIN es.timeSlots slot " +
           "JOIN EngineerExpertBrand eb ON eb.engineer = u " +
           "JOIN EngineerServiceRegion esr ON esr.engineer = u " +
           "WHERE es.workDate = :workDate " +
           "AND slot.startTime <= :workTime AND slot.endTime > :workTime " +
           "AND es.status = :availableStatus " +
           "AND eb.brandName = :brand " +
           "AND ep.category.categoryId = :categoryId " +
           "AND esr.region.id = :regionId " +
           "ORDER BY ep.profileId ASC")
    List<EngineerProfile> findByAllConditions(
            @Param("workDate") LocalDate workDate,
            @Param("workTime") LocalTime workTime,
            @Param("availableStatus") ScheduleStatus availableStatus,
            @Param("brand") String brand,
            @Param("categoryId") Integer categoryId,
            @Param("regionId") Integer regionId
    );

    /**
     * AUTO 배정 - Fallback 1: 스케줄 + 카테고리 + 서비스 지역 (브랜드 조건 완화)
     */
    @Query("SELECT DISTINCT ep FROM EngineerProfile ep " +
           "JOIN ep.user u " +
           "JOIN EngineerSchedule es ON es.user = u " +
           "JOIN es.timeSlots slot " +
           "JOIN EngineerServiceRegion esr ON esr.engineer = u " +
           "WHERE es.workDate = :workDate " +
           "AND slot.startTime <= :workTime AND slot.endTime > :workTime " +
           "AND es.status = :availableStatus " +
           "AND ep.category.categoryId = :categoryId " +
           "AND esr.region.id = :regionId " +
           "ORDER BY ep.profileId ASC")
    List<EngineerProfile> findWithoutBrand(
            @Param("workDate") LocalDate workDate,
            @Param("workTime") LocalTime workTime,
            @Param("availableStatus") ScheduleStatus availableStatus,
            @Param("categoryId") Integer categoryId,
            @Param("regionId") Integer regionId
    );

    /**
     * AUTO 배정 - Fallback 2: 스케줄 + 카테고리 (브랜드 + 서비스 지역 조건 완화)
     */
    @Query("SELECT DISTINCT ep FROM EngineerProfile ep " +
           "JOIN ep.user u " +
           "JOIN EngineerSchedule es ON es.user = u " +
           "JOIN es.timeSlots slot " +
           "WHERE es.workDate = :workDate " +
           "AND slot.startTime <= :workTime AND slot.endTime > :workTime " +
           "AND es.status = :availableStatus " +
           "AND ep.category.categoryId = :categoryId " +
           "ORDER BY ep.profileId ASC")
    List<EngineerProfile> findWithoutBrandAndRegion(
            @Param("workDate") LocalDate workDate,
            @Param("workTime") LocalTime workTime,
            @Param("availableStatus") ScheduleStatus availableStatus,
            @Param("categoryId") Integer categoryId
    );

    /**
     * AUTO 배정 - Fallback 3: 스케줄 조건만 (브랜드 + 지역 + 카테고리 조건 완화)
     */
    @Query("SELECT DISTINCT ep FROM EngineerProfile ep " +
           "JOIN ep.user u " +
           "JOIN EngineerSchedule es ON es.user = u " +
           "JOIN es.timeSlots slot " +
           "WHERE es.workDate = :workDate " +
           "AND slot.startTime <= :workTime AND slot.endTime > :workTime " +
           "AND es.status = :availableStatus " +
           "ORDER BY ep.profileId ASC")
    List<EngineerProfile> findByScheduleOnly(
            @Param("workDate") LocalDate workDate,
            @Param("workTime") LocalTime workTime,
            @Param("availableStatus") ScheduleStatus availableStatus
    );

    // ─── 재배차 전용: 이미 배차된 기사 제외 버전 ───────────────────────────────
    // 기존 Fallback 메서드와 동일한 조건에 AND u.id NOT IN :excludeIds 추가
    // 기존 메서드는 일체 변경하지 않음 (하위 호환 유지)

    /**
     * 재배차 Fallback 0: 스케줄 + 브랜드 + 카테고리 + 서비스 지역 (이전 배차 기사 제외)
     */
    @Query("SELECT DISTINCT ep FROM EngineerProfile ep " +
           "JOIN ep.user u " +
           "JOIN EngineerSchedule es ON es.user = u " +
           "JOIN es.timeSlots slot " +
           "JOIN EngineerExpertBrand eb ON eb.engineer = u " +
           "JOIN EngineerServiceRegion esr ON esr.engineer = u " +
           "WHERE es.workDate = :workDate " +
           "AND slot.startTime <= :workTime AND slot.endTime > :workTime " +
           "AND es.status = :availableStatus " +
           "AND eb.brandName = :brand " +
           "AND ep.category.categoryId = :categoryId " +
           "AND esr.region.id = :regionId " +
           "AND u.id NOT IN :excludeIds " +
           "ORDER BY ep.profileId ASC")
    List<EngineerProfile> findByAllConditionsExcluding(
            @Param("workDate") LocalDate workDate,
            @Param("workTime") LocalTime workTime,
            @Param("availableStatus") ScheduleStatus availableStatus,
            @Param("brand") String brand,
            @Param("categoryId") Integer categoryId,
            @Param("regionId") Integer regionId,
            @Param("excludeIds") Set<Long> excludeIds
    );

    /**
     * 재배차 Fallback 1: 스케줄 + 카테고리 + 서비스 지역 (브랜드 조건 완화, 이전 배차 기사 제외)
     */
    @Query("SELECT DISTINCT ep FROM EngineerProfile ep " +
           "JOIN ep.user u " +
           "JOIN EngineerSchedule es ON es.user = u " +
           "JOIN es.timeSlots slot " +
           "JOIN EngineerServiceRegion esr ON esr.engineer = u " +
           "WHERE es.workDate = :workDate " +
           "AND slot.startTime <= :workTime AND slot.endTime > :workTime " +
           "AND es.status = :availableStatus " +
           "AND ep.category.categoryId = :categoryId " +
           "AND esr.region.id = :regionId " +
           "AND u.id NOT IN :excludeIds " +
           "ORDER BY ep.profileId ASC")
    List<EngineerProfile> findWithoutBrandExcluding(
            @Param("workDate") LocalDate workDate,
            @Param("workTime") LocalTime workTime,
            @Param("availableStatus") ScheduleStatus availableStatus,
            @Param("categoryId") Integer categoryId,
            @Param("regionId") Integer regionId,
            @Param("excludeIds") Set<Long> excludeIds
    );

    /**
     * 재배차 Fallback 2: 스케줄 + 카테고리 (브랜드 + 서비스 지역 조건 완화, 이전 배차 기사 제외)
     */
    @Query("SELECT DISTINCT ep FROM EngineerProfile ep " +
           "JOIN ep.user u " +
           "JOIN EngineerSchedule es ON es.user = u " +
           "JOIN es.timeSlots slot " +
           "WHERE es.workDate = :workDate " +
           "AND slot.startTime <= :workTime AND slot.endTime > :workTime " +
           "AND es.status = :availableStatus " +
           "AND ep.category.categoryId = :categoryId " +
           "AND u.id NOT IN :excludeIds " +
           "ORDER BY ep.profileId ASC")
    List<EngineerProfile> findWithoutBrandAndRegionExcluding(
            @Param("workDate") LocalDate workDate,
            @Param("workTime") LocalTime workTime,
            @Param("availableStatus") ScheduleStatus availableStatus,
            @Param("categoryId") Integer categoryId,
            @Param("excludeIds") Set<Long> excludeIds
    );

    /**
     * 재배차 Fallback 3: 스케줄 조건만 (브랜드 + 지역 + 카테고리 조건 완화, 이전 배차 기사 제외)
     */
    @Query("SELECT DISTINCT ep FROM EngineerProfile ep " +
           "JOIN ep.user u " +
           "JOIN EngineerSchedule es ON es.user = u " +
           "JOIN es.timeSlots slot " +
           "WHERE es.workDate = :workDate " +
           "AND slot.startTime <= :workTime AND slot.endTime > :workTime " +
           "AND es.status = :availableStatus " +
           "AND u.id NOT IN :excludeIds " +
           "ORDER BY ep.profileId ASC")
    List<EngineerProfile> findByScheduleOnlyExcluding(
            @Param("workDate") LocalDate workDate,
            @Param("workTime") LocalTime workTime,
            @Param("availableStatus") ScheduleStatus availableStatus,
            @Param("excludeIds") Set<Long> excludeIds
    );
}
