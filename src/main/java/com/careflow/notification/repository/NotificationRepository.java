package com.careflow.notification.repository;

import com.careflow.notification.entity.Notification;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface NotificationRepository extends JpaRepository<Notification, Long> {
    List<Notification> findByUser_IdOrderByCreatedAtDesc(Long userId);

    // user_id 목록 범위 내 알림을 최신순으로 페이징 조회 (대행사 알림센터 — 소속 기사/고객 알림 집계)
    // type이 null이면 전체 타입 조회, 값이 있으면 해당 타입만 필터링
    @Query("SELECT n FROM Notification n " +
           "WHERE n.user.id IN :userIds " +
           "AND (:type IS NULL OR n.type = :type) " +
           "ORDER BY n.createdAt DESC")
    Page<Notification> findByUser_IdInAndTypeOrderByCreatedAtDesc(
            @Param("userIds") List<Long> userIds,
            @Param("type") String type,
            Pageable pageable);

    // user_id 목록 범위 내 전체 건수 집계 (type 필터와 무관한 stats.totalCount 산정용)
    long countByUser_IdIn(List<Long> userIds);

    // user_id 목록 범위 내 미열람(is_read = false) 건수 집계
    long countByUser_IdInAndIsReadFalse(List<Long> userIds);

    // user_id 목록 범위 내 특정 기간(created_at) 건수 집계 — 오늘 발생 건수 산정용
    @Query("SELECT COUNT(n) FROM Notification n " +
           "WHERE n.user.id IN :userIds " +
           "AND n.createdAt >= :startOfDay AND n.createdAt < :endOfDay")
    long countByUser_IdInAndCreatedAtBetween(
            @Param("userIds") List<Long> userIds,
            @Param("startOfDay") LocalDateTime startOfDay,
            @Param("endOfDay") LocalDateTime endOfDay);
}
