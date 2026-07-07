package com.careflow.engineer.repository;

import com.careflow.engineer.domain.entity.EngineerProfile;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
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

    // 배정 목록 조회 N+1 방지 — 기사 user_id 복수 배치 조회
    List<EngineerProfile> findByUser_IdIn(List<Long> userIds);

    // 슬롯 단위 중복 배정 방지 공통 서브쿼리 — 해당 기사가 같은 날짜·같은 예약 시각에
    // 이미 활성 상태(WAITING/ACCEPTED/COMPLETED) 배정을 갖고 있으면 후보에서 제외한다.
    // (근무표 status 컬럼은 하루 단위라 슬롯 4개 중 하나만 찼는지 구분 못 하므로 게이트로 쓰지 않는다 — 대신 실제 배정 내역을 직접 확인)
    String NO_CONFLICTING_ASSIGNMENT =
            "AND NOT EXISTS (" +
            "    SELECT 1 FROM AsAssignment aa JOIN aa.asRequest ar " +
            "    WHERE aa.engineer = u " +
            "    AND aa.status <> 'REJECTED' " +
            "    AND ar.scheduledDate = :workDate " +
            "    AND ar.scheduledTime = :workTimeStr" +
            ") ";

    /**
     * AUTO 배정 - Fallback 0: 스케줄 + 브랜드 + 카테고리 + 서비스 지역 (4가지 조건 모두)
     * LMS 교육 이수 완료 기사만 대상 (isLmsCompleted = true)
     * 슬롯(09~11/11~13/14~16/16~18) 중 하나라도 이미 배정이 있다고 해서 그 날 전체를 막지 않도록,
     * 근무표 status 대신 같은 시각에 활성 배정이 있는지를 직접 확인한다.
     */
    @Query("SELECT DISTINCT ep FROM EngineerProfile ep " +
           "JOIN ep.user u " +
           "JOIN EngineerSchedule es ON es.user = u " +
           "JOIN es.timeSlots slot " +
           "JOIN EngineerExpertBrand eb ON eb.engineer = u " +
           "JOIN EngineerServiceRegion esr ON esr.engineer = u " +
           "WHERE es.workDate = :workDate " +
           "AND slot.startTime <= :workTime AND slot.endTime > :workTime " +
           "AND ep.isLmsCompleted = true " +
           "AND eb.brandName = :brand " +
           "AND ep.category.categoryId = :categoryId " +
           "AND esr.region.id = :regionId " +
           NO_CONFLICTING_ASSIGNMENT +
           "ORDER BY ep.profileId ASC")
    List<EngineerProfile> findByAllConditions(
            @Param("workDate") LocalDate workDate,
            @Param("workTime") LocalTime workTime,
            @Param("workTimeStr") String workTimeStr,
            @Param("brand") String brand,
            @Param("categoryId") Integer categoryId,
            @Param("regionId") Integer regionId
    );

    /**
     * AUTO 배정 - Fallback 1: 스케줄 + 카테고리 + 서비스 지역 (브랜드 조건 완화)
     * LMS 교육 이수 완료 기사만 대상 (isLmsCompleted = true)
     */
    @Query("SELECT DISTINCT ep FROM EngineerProfile ep " +
           "JOIN ep.user u " +
           "JOIN EngineerSchedule es ON es.user = u " +
           "JOIN es.timeSlots slot " +
           "JOIN EngineerServiceRegion esr ON esr.engineer = u " +
           "WHERE es.workDate = :workDate " +
           "AND slot.startTime <= :workTime AND slot.endTime > :workTime " +
           "AND ep.isLmsCompleted = true " +
           "AND ep.category.categoryId = :categoryId " +
           "AND esr.region.id = :regionId " +
           NO_CONFLICTING_ASSIGNMENT +
           "ORDER BY ep.profileId ASC")
    List<EngineerProfile> findWithoutBrand(
            @Param("workDate") LocalDate workDate,
            @Param("workTime") LocalTime workTime,
            @Param("workTimeStr") String workTimeStr,
            @Param("categoryId") Integer categoryId,
            @Param("regionId") Integer regionId
    );

    /**
     * AUTO 배정 - Fallback 2: 스케줄 + 카테고리 (브랜드 + 서비스 지역 조건 완화)
     * LMS 교육 이수 완료 기사만 대상 (isLmsCompleted = true)
     */
    @Query("SELECT DISTINCT ep FROM EngineerProfile ep " +
           "JOIN ep.user u " +
           "JOIN EngineerSchedule es ON es.user = u " +
           "JOIN es.timeSlots slot " +
           "WHERE es.workDate = :workDate " +
           "AND slot.startTime <= :workTime AND slot.endTime > :workTime " +
           "AND ep.isLmsCompleted = true " +
           "AND ep.category.categoryId = :categoryId " +
           NO_CONFLICTING_ASSIGNMENT +
           "ORDER BY ep.profileId ASC")
    List<EngineerProfile> findWithoutBrandAndRegion(
            @Param("workDate") LocalDate workDate,
            @Param("workTime") LocalTime workTime,
            @Param("workTimeStr") String workTimeStr,
            @Param("categoryId") Integer categoryId
    );

    /**
     * AUTO 배정 - Fallback 3: 스케줄 조건만 (브랜드 + 지역 + 카테고리 조건 완화)
     * LMS 교육 이수 완료 기사만 대상 (isLmsCompleted = true)
     */
    @Query("SELECT DISTINCT ep FROM EngineerProfile ep " +
           "JOIN ep.user u " +
           "JOIN EngineerSchedule es ON es.user = u " +
           "JOIN es.timeSlots slot " +
           "WHERE es.workDate = :workDate " +
           "AND slot.startTime <= :workTime AND slot.endTime > :workTime " +
           "AND ep.isLmsCompleted = true " +
           NO_CONFLICTING_ASSIGNMENT +
           "ORDER BY ep.profileId ASC")
    List<EngineerProfile> findByScheduleOnly(
            @Param("workDate") LocalDate workDate,
            @Param("workTime") LocalTime workTime,
            @Param("workTimeStr") String workTimeStr
    );

    // ─── 재배차 전용: 이미 배차된 기사 제외 버전 ───────────────────────────────
    // 기존 Fallback 메서드와 동일한 조건에 AND u.id NOT IN :excludeIds 추가
    // 기존 메서드는 일체 변경하지 않음 (하위 호환 유지)

    /**
     * 재배차 Fallback 0: 스케줄 + 브랜드 + 카테고리 + 서비스 지역 (이전 배차 기사 제외)
     * LMS 교육 이수 완료 기사만 대상 (isLmsCompleted = true)
     */
    @Query("SELECT DISTINCT ep FROM EngineerProfile ep " +
           "JOIN ep.user u " +
           "JOIN EngineerSchedule es ON es.user = u " +
           "JOIN es.timeSlots slot " +
           "JOIN EngineerExpertBrand eb ON eb.engineer = u " +
           "JOIN EngineerServiceRegion esr ON esr.engineer = u " +
           "WHERE es.workDate = :workDate " +
           "AND slot.startTime <= :workTime AND slot.endTime > :workTime " +
           "AND ep.isLmsCompleted = true " +
           "AND eb.brandName = :brand " +
           "AND ep.category.categoryId = :categoryId " +
           "AND esr.region.id = :regionId " +
           "AND u.id NOT IN :excludeIds " +
           NO_CONFLICTING_ASSIGNMENT +
           "ORDER BY ep.profileId ASC")
    List<EngineerProfile> findByAllConditionsExcluding(
            @Param("workDate") LocalDate workDate,
            @Param("workTime") LocalTime workTime,
            @Param("workTimeStr") String workTimeStr,
            @Param("brand") String brand,
            @Param("categoryId") Integer categoryId,
            @Param("regionId") Integer regionId,
            @Param("excludeIds") Set<Long> excludeIds
    );

    /**
     * 재배차 Fallback 1: 스케줄 + 카테고리 + 서비스 지역 (브랜드 조건 완화, 이전 배차 기사 제외)
     * LMS 교육 이수 완료 기사만 대상 (isLmsCompleted = true)
     */
    @Query("SELECT DISTINCT ep FROM EngineerProfile ep " +
           "JOIN ep.user u " +
           "JOIN EngineerSchedule es ON es.user = u " +
           "JOIN es.timeSlots slot " +
           "JOIN EngineerServiceRegion esr ON esr.engineer = u " +
           "WHERE es.workDate = :workDate " +
           "AND slot.startTime <= :workTime AND slot.endTime > :workTime " +
           "AND ep.isLmsCompleted = true " +
           "AND ep.category.categoryId = :categoryId " +
           "AND esr.region.id = :regionId " +
           "AND u.id NOT IN :excludeIds " +
           NO_CONFLICTING_ASSIGNMENT +
           "ORDER BY ep.profileId ASC")
    List<EngineerProfile> findWithoutBrandExcluding(
            @Param("workDate") LocalDate workDate,
            @Param("workTime") LocalTime workTime,
            @Param("workTimeStr") String workTimeStr,
            @Param("categoryId") Integer categoryId,
            @Param("regionId") Integer regionId,
            @Param("excludeIds") Set<Long> excludeIds
    );

    /**
     * 재배차 Fallback 2: 스케줄 + 카테고리 (브랜드 + 서비스 지역 조건 완화, 이전 배차 기사 제외)
     * LMS 교육 이수 완료 기사만 대상 (isLmsCompleted = true)
     */
    @Query("SELECT DISTINCT ep FROM EngineerProfile ep " +
           "JOIN ep.user u " +
           "JOIN EngineerSchedule es ON es.user = u " +
           "JOIN es.timeSlots slot " +
           "WHERE es.workDate = :workDate " +
           "AND slot.startTime <= :workTime AND slot.endTime > :workTime " +
           "AND ep.isLmsCompleted = true " +
           "AND ep.category.categoryId = :categoryId " +
           "AND u.id NOT IN :excludeIds " +
           NO_CONFLICTING_ASSIGNMENT +
           "ORDER BY ep.profileId ASC")
    List<EngineerProfile> findWithoutBrandAndRegionExcluding(
            @Param("workDate") LocalDate workDate,
            @Param("workTime") LocalTime workTime,
            @Param("workTimeStr") String workTimeStr,
            @Param("categoryId") Integer categoryId,
            @Param("excludeIds") Set<Long> excludeIds
    );

    /**
     * 재배차 Fallback 3: 스케줄 조건만 (브랜드 + 지역 + 카테고리 조건 완화, 이전 배차 기사 제외)
     * LMS 교육 이수 완료 기사만 대상 (isLmsCompleted = true)
     */
    @Query("SELECT DISTINCT ep FROM EngineerProfile ep " +
           "JOIN ep.user u " +
           "JOIN EngineerSchedule es ON es.user = u " +
           "JOIN es.timeSlots slot " +
           "WHERE es.workDate = :workDate " +
           "AND slot.startTime <= :workTime AND slot.endTime > :workTime " +
           "AND ep.isLmsCompleted = true " +
           "AND u.id NOT IN :excludeIds " +
           NO_CONFLICTING_ASSIGNMENT +
           "ORDER BY ep.profileId ASC")
    List<EngineerProfile> findByScheduleOnlyExcluding(
            @Param("workDate") LocalDate workDate,
            @Param("workTime") LocalTime workTime,
            @Param("workTimeStr") String workTimeStr,
            @Param("excludeIds") Set<Long> excludeIds
    );


    // 매년 LMS교육 이수 전부 리셋
    @Modifying
    @Query("UPDATE EngineerProfile ep SET ep.isLmsCompleted = false")
    int resetAllLmsCompleted();


    // 대행사에 소속된 엔지니어 전체 조회
    @Query("""
    SELECT ep FROM EngineerProfile ep
    JOIN FETCH ep.user
    WHERE ep.user.agency.id = :agencyId
    """)
    List<EngineerProfile> findByAgencyId(@Param("agencyId") Long agencyId);

    /**
     * 고객용 수동 배정 - 후보 기사 조회 (대행사 제한 없음, 전체 소속 기사 대상)
     * 서비스 지역 + LMS 이수 완료 + 소속 대행사 보유를 기본 조건으로 하고,
     * brand/skill 은 선택 필터(null 이면 미적용)
     */
    @Query("SELECT DISTINCT ep FROM EngineerProfile ep " +
           "JOIN ep.user u " +
           "JOIN EngineerServiceRegion esr ON esr.engineer = u " +
           "WHERE esr.region.id = :regionId " +
           "AND ep.isLmsCompleted = true " +
           "AND u.agency IS NOT NULL " +
           "AND (:brand IS NULL OR EXISTS (SELECT 1 FROM EngineerExpertBrand eb WHERE eb.engineer = u AND eb.brandName = :brand)) " +
           "AND (:skill IS NULL OR ep.category.name = :skill) " +
           "ORDER BY ep.avgRating DESC")
    List<EngineerProfile> findAvailableForCustomer(
            @Param("regionId") Integer regionId,
            @Param("brand") String brand,
            @Param("skill") String skill
    );
}
