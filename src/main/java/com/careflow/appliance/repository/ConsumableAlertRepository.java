package com.careflow.appliance.repository;

import com.careflow.appliance.entity.ConsumableAlert;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

@Repository
public interface ConsumableAlertRepository extends JpaRepository<ConsumableAlert, Long> {

    /**
     * [Quartz 배치용] 특정 날짜(보통 오늘) 이하의 알림 발송 대상 조회
     * 알림을 보낼 때 user(수신자)와 appliance(제품명) 정보가 필수적이므로,
     * N+1 문제를 방지하기 위해 단일 쿼리에서 JOIN FETCH 로 한 번에 가져옵니다.
     * (혹시 배치가 하루 누락되었을 경우를 대비해 <= 연산자 사용)
     */
    @Query("SELECT c FROM ConsumableAlert c " +
            "JOIN FETCH c.user " +
            "JOIN FETCH c.appliance " +
            "WHERE c.nextAlertDate <= :targetDate AND c.isActive = true")
    List<ConsumableAlert> findAlertsToNotify(@Param("targetDate") LocalDate targetDate);

    // 추후 고객용(CUSTOMER) 마이페이지 소모품 관리 기능에서 사용할 조회 쿼리
    List<ConsumableAlert> findByUser_IdOrderByNextAlertDateAsc(Long userId);
}