package com.careflow.notification.repository;

import com.careflow.notification.entity.Notification;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface NotificationRepository extends JpaRepository<Notification, Long> {
    List<Notification> findByUser_IdOrderByCreatedAtDesc(Long userId);

    Page<Notification> findByUser_IdOrderByCreatedAtDesc(Long userId, Pageable pageable);   // 프론트엔드 페이징 처리를 위한 Pageable 지원 메서드

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

    // user_id 목록 범위 내 미열람 알림 전체를 벌크 읽음 처리 (대행사 "모두 읽음 처리" 버튼)
    @Modifying
    @Transactional
    @Query("UPDATE Notification n SET n.isRead = true WHERE n.user.id IN :userIds AND n.isRead = false")
    int markAllAsReadByUserIds(@Param("userIds") List<Long> userIds);

    // user_id 목록 범위 내 특정 기간(created_at) 건수 집계 — 오늘 발생 건수 산정용
    @Query("SELECT COUNT(n) FROM Notification n " +
           "WHERE n.user.id IN :userIds " +
           "AND n.createdAt >= :startOfDay AND n.createdAt < :endOfDay")
    long countByUser_IdInAndCreatedAtBetween(
            @Param("userIds") List<Long> userIds,
            @Param("startOfDay") LocalDateTime startOfDay,
            @Param("endOfDay") LocalDateTime endOfDay);

    // [기사용] 알림 목록 페이징 + type 동적 필터링
    @Query("SELECT n FROM Notification n " +
            "WHERE n.user.id = :userId " +
            "AND (:type IS NULL OR n.type = :type) " +
            "ORDER BY n.createdAt DESC")
    org.springframework.data.domain.Page<Notification> findByUserIdAndTypeWithPaging(
            @org.springframework.data.repository.query.Param("userId") Long userId,
            @org.springframework.data.repository.query.Param("type") String type,
            org.springframework.data.domain.Pageable pageable);
}
