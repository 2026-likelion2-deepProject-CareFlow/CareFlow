package com.careflow.notification.dto;

// 고객 알림센터 카테고리별 요약(전체/AS_STATUS/CONSUMABLE/WARRANTY) 한 칸에 대응하는 통계
public record NotificationCategoryStat(long count, long unreadCount) {
}
